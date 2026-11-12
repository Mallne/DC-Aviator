package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoRunExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoStepExecution
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class ArazzoRunExecutorTest {
    private val workflowIdentity = WorkflowIdentity("arazzo", "1.1.0", "workflow:test", "revision-1")
    private val workflow = ArazzoWorkflow("weather", listOf(ArazzoStep("first", operationId = "getWeather"), ArazzoStep("second", operationId = "getForecast")))
    private val constituent = ConstituentIdentity(ServiceLocator("weather>get"), "provider", "source", "rev")

    @Test
    fun sharedRunBudgetStopsSchedulingAndEmitsOrderedTerminalEvents() = runTest {
        val calls = mutableListOf<String>()
        val sink = RecordingSink()
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { step, _, scope ->
            scope.acquireCall(constituent)?.let { return@ArazzoStepExecutor failed(it) }
            try {
                calls += step.stepId
                succeeded()
            } finally {
                scope.releaseCall(constituent)
            }
        })

        val result = executor.execute(
            workflow, workflowIdentity, RunId("run-budget"),
            metadata = mapOf("Authorization" to "Bearer secret", "trace.id" to "trace-1"),
            budget = ExecutionBudget(maxCalls = 1), events = sink,
        )

        assertEquals(listOf("first"), calls)
        val failure = assertIs<ExecutionOutcome.Failed>(result.outcome)
        assertEquals("calls", failure.failure.code)
        assertEquals(listOf("first"), failure.completedSteps)
        assertEquals("[REDACTED]", sink.events.first().metadata.values["Authorization"])
        assertEquals("trace-1", sink.events.first().metadata.values["trace.id"])
        assertEquals(
            listOf(
                ExecutionEventKind.RUN_STARTED,
                ExecutionEventKind.STEP_STARTED,
                ExecutionEventKind.CONSTITUENT_STARTED,
                ExecutionEventKind.CONSTITUENT_FINISHED,
                ExecutionEventKind.STEP_FINISHED,
                ExecutionEventKind.STEP_STARTED,
                ExecutionEventKind.STEP_FINISHED,
                ExecutionEventKind.RUN_FINISHED,
            ),
            sink.events.map { it.kind },
        )
        assertEquals(ExecutionEventOutcome.FAILED, sink.events.last().terminal)
    }

    @Test
    fun cancellationStopsLaterStepsAndEmitsCancelledTerminalEvent() = runTest {
        val calls = mutableListOf<String>()
        val sink = RecordingSink(blockOnCancelledTerminal = true)
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { step, _, scope ->
            scope.acquireCall(constituent)?.let { return@ArazzoStepExecutor failed(it) }
            try {
                calls += step.stepId
                awaitCancellation()
            } finally {
                scope.releaseCall(constituent)
            }
        })

        val run = launch(start = CoroutineStart.UNDISPATCHED) { executor.execute(workflow, workflowIdentity, RunId("run-cancel"), events = sink) }
        run.cancelAndJoin()

        assertEquals(listOf("first"), calls)
        assertEquals(ExecutionEventOutcome.CANCELLED, sink.events.last().terminal)
        assertEquals(ExecutionEventKind.RUN_FINISHED, sink.events.last().kind)
        assertEquals(1, sink.events.count { it.kind == ExecutionEventKind.STEP_STARTED })
    }

    @Test
    fun cancelledConstituentFinishedObserverCannotPinCallCleanup() = runTest {
        val neverReleased = CompletableDeferred<Unit>()
        val sink = RecordingSink(blockOn = ExecutionEventKind.CONSTITUENT_FINISHED, gate = neverReleased)
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { _, _, scope ->
            scope.acquireCall(constituent)?.let { return@ArazzoStepExecutor failed(it) }
            try {
                awaitCancellation()
            } finally {
                scope.releaseCall(constituent)
            }
        })

        val run = launch(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("run-observer-cancel"), events = sink)
        }
        run.cancelAndJoin()

        assertEquals(ExecutionEventOutcome.CANCELLED, sink.events.last().terminal)
        assertEquals(ExecutionEventKind.RUN_FINISHED, sink.events.last().kind)
    }

    @Test
    fun concurrentRunsHaveIndependentBudgetAndEventState() = runTest {
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { _, _, scope ->
            scope.acquireCall(constituent)?.let { return@ArazzoStepExecutor failed(it) }
            try {
                kotlinx.coroutines.yield()
                succeeded()
            } finally {
                scope.releaseCall(constituent)
            }
        })
        val firstEvents = RecordingSink()
        val secondEvents = RecordingSink()
        val first = async { executor.execute(workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("run-a"), budget = ExecutionBudget(maxCalls = 1), events = firstEvents) }
        val second = async { executor.execute(workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("run-b"), budget = ExecutionBudget(maxCalls = 1), events = secondEvents) }

        assertIs<ExecutionOutcome.Succeeded>(first.await().outcome)
        assertIs<ExecutionOutcome.Succeeded>(second.await().outcome)
        assertEquals(RunId("run-a"), firstEvents.events.first().metadata.runId)
        assertEquals(RunId("run-b"), secondEvents.events.first().metadata.runId)
    }

    @Test
    fun unsupportedExecutableFeaturesAreRejectedBeforeAnyInvocation() = runTest {
        var calls = 0
        val sink = RecordingSink()
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { _, _, _ ->
            calls++
            succeeded()
        })

        val result = executor.execute(
            workflow.copy(steps = listOf(workflow.steps.first().copy(successCriteria = listOf(
                cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoCriterion("\$response.statusCode == 200"),
            )))),
            workflowIdentity, RunId("run-unsupported"), events = sink,
        )

        assertEquals(0, calls)
        assertEquals("unsupported_step", assertIs<ExecutionOutcome.Failed>(result.outcome).failure.code)
        assertEquals(ExecutionEventOutcome.FAILED, sink.events.last().terminal)
    }

    @Test
    fun expiredRunDeadlineStopsBeforeFirstStepUsingInjectedClock() = runTest {
        var calls = 0
        val sink = RecordingSink()
        val executor = ArazzoRunExecutor(
            ArazzoStepExecutor { _, _, _ ->
                calls++
                succeeded()
            },
            now = { Instant.fromEpochMilliseconds(42L) },
        )

        val result = executor.execute(
            workflow, workflowIdentity, RunId("run-deadline"),
            budget = ExecutionBudget(deadline = Instant.fromEpochMilliseconds(42L)), events = sink,
        )

        assertEquals(0, calls)
        assertEquals("deadline", assertIs<ExecutionOutcome.Failed>(result.outcome).failure.code)
        assertEquals(listOf(ExecutionEventKind.RUN_STARTED, ExecutionEventKind.RUN_FINISHED), sink.events.map { it.kind })
    }

    @Test
    fun deadlineCancelsAnActiveStepAndReturnsStructuredFailure() = runTest {
        var calls = 0
        var released = false
        val sink = RecordingSink()
        val executor = ArazzoRunExecutor(
            ArazzoStepExecutor { _, _, scope ->
                scope.acquireCall(constituent)?.let { return@ArazzoStepExecutor failed(it) }
                calls++
                try {
                    awaitCancellation()
                } finally {
                    scope.releaseCall(constituent)
                    released = true
                }
            },
            now = { Instant.fromEpochMilliseconds(42L) },
        )

        val result = executor.execute(
            workflow, workflowIdentity, RunId("run-active-deadline"),
            budget = ExecutionBudget(deadline = Instant.fromEpochMilliseconds(52L)), events = sink,
        )

        assertEquals(1, calls)
        assertEquals(true, released)
        assertEquals("deadline", assertIs<ExecutionOutcome.Failed>(result.outcome).failure.code)
        assertEquals(ExecutionEventOutcome.FAILED, sink.events.last().terminal)
        assertEquals(ExecutionEventKind.RUN_FINISHED, sink.events.last().kind)
    }

    @Test
    fun thrownProviderFailureProducesSanitizedTerminalOutcome() = runTest {
        val sink = RecordingSink()
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { _, _, _ ->
            error("Authorization: Bearer top-secret")
        })

        val result = executor.execute(
            workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("run-provider-error"), events = sink,
        )

        val failure = assertIs<ExecutionOutcome.Failed>(result.outcome).failure
        assertEquals("operation_failed", failure.code)
        assertEquals("Arazzo step execution failed", failure.message)
        assertEquals(ExecutionEventOutcome.FAILED, sink.events.last().terminal)
        assertEquals(ExecutionEventKind.RUN_FINISHED, sink.events.last().kind)
        assertEquals(false, failure.message.contains("top-secret"))
    }

    @Test
    fun suspendingObserverBackpressuresSchedulingWithoutDetachedWork() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sink = RecordingSink(blockOn = ExecutionEventKind.STEP_STARTED, gate = gate)
        var calls = 0
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { _, _, _ ->
            calls++
            succeeded()
        })

        val run = launch(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("run-backpressure"), events = sink)
        }
        assertEquals(0, calls)
        assertEquals(listOf(ExecutionEventKind.RUN_STARTED, ExecutionEventKind.STEP_STARTED), sink.events.map { it.kind })
        gate.complete(Unit)
        run.join()
        assertEquals(1, calls)
        assertEquals(ExecutionEventOutcome.SUCCEEDED, sink.events.last().terminal)
    }

    private fun succeeded() = ArazzoStepExecution.Completed(
        CapabilityResult(constituent.locator, CapabilityOutcome.Succeeded(BodyValue.Empty), emptyList()),
    )
    private fun failed(failure: ExecutionFailure) = ArazzoStepExecution.Completed(
        CapabilityResult(constituent.locator, CapabilityOutcome.Failed(failure), emptyList()),
    )

    private class RecordingSink(
        private val blockOnCancelledTerminal: Boolean = false,
        private val blockOn: ExecutionEventKind? = null,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : ExecutionEventSink {
        val events = mutableListOf<ExecutionEvent>()
        override suspend fun emit(event: ExecutionEvent) {
            events += event
            if (event.kind == blockOn) checkNotNull(gate).await()
            if (blockOnCancelledTerminal && event.terminal == ExecutionEventOutcome.CANCELLED) awaitCancellation()
        }
    }
}
