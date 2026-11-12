package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoRunExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoStepExecution
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationPolicy
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationPolicyPlugin
import cloud.mallne.dicentra.aviator.orchestration.plugin.*
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
    fun runStartedSinkFailureStillNotifiesFailureAndCleansUpPartialRun() = runTest {
        val lifecycle = mutableListOf<OrchestrationStage>()
        var deliveredEvents = 0
        var dispatched = 0
        val plugins = installOrchestrationPlugins {
            install("partial-run-cleanup") {
                stages += OrchestrationStage.FAILURE
                stages += OrchestrationStage.CLEANUP
                lifecycleScopes += OrchestrationLifecycleScope.RUN
                executor = OrchestrationPluginExecutor { context -> lifecycle += context.stage }
            }
        }
        val executor = ArazzoRunExecutor(
            ArazzoStepExecutor { _, _, _ -> dispatched++; succeeded() }, plugins = plugins,
        )

        val failure = kotlin.test.assertFailsWith<IllegalStateException> {
            executor.execute(
                workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("partial-run"),
                events = ExecutionEventSink {
                    deliveredEvents++
                    error("sink failed before run preparation")
                },
            )
        }

        assertEquals("sink failed before run preparation", failure.message)
        assertEquals(1, deliveredEvents)
        assertEquals(0, dispatched)
        assertEquals(listOf(OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), lifecycle)
    }

    @Test
    fun runCleanupOccursOnceOnSuccessAndPluginRejection() = runTest {
        for (rejectPreparation in listOf(false, true)) {
            val observed = mutableListOf<OrchestrationStage>()
            val plugins = installOrchestrationPlugins {
                install("terminal-hooks") {
                    stages += OrchestrationStage.PREPARE
                    stages += OrchestrationStage.FAILURE
                    stages += OrchestrationStage.CLEANUP
                    lifecycleScopes += OrchestrationLifecycleScope.RUN
                    executor = OrchestrationPluginExecutor { context ->
                        observed += context.stage
                        if (rejectPreparation && context.stage == OrchestrationStage.PREPARE) {
                            context.reject(ExecutionFailure("preparation_rejected", "Rejected"))
                        }
                    }
                }
            }
            val executor = ArazzoRunExecutor(ArazzoStepExecutor { _, _, _ -> succeeded() }, plugins = plugins)

            val result = executor.execute(workflow.copy(steps = emptyList()), workflowIdentity,
                RunId("terminal-$rejectPreparation"))

            if (rejectPreparation) {
                assertIs<ExecutionOutcome.Failed>(result.outcome)
                assertEquals(listOf(OrchestrationStage.PREPARE, OrchestrationStage.FAILURE,
                    OrchestrationStage.CLEANUP), observed)
            } else {
                assertIs<ExecutionOutcome.Succeeded>(result.outcome)
                assertEquals(listOf(OrchestrationStage.PREPARE, OrchestrationStage.CLEANUP), observed)
            }
        }
    }

    @Test
    fun runAdapterDispatchesPairedInputPhasesAroundTransformation() = runTest {
        val observedPhases = mutableListOf<OrchestrationHookPhase>()
        var received: String? = null
        val plugins = installOrchestrationPlugins {
            install("paired-input") {
                stages += OrchestrationStage.MAP_INPUT
                this.phases += OrchestrationHookPhase.BEFORE
                this.phases += OrchestrationHookPhase.AFTER
                executor = OrchestrationPluginExecutor { context ->
                    observedPhases.add(context.phase)
                    if (context.phase == OrchestrationHookPhase.BEFORE) context.input = "before-mapped"
                    else {
                        check(context.input == "before-mapped")
                        context.input = "after-mapped"
                    }
                }
            }
        }
        val executor = ArazzoRunExecutor(
            ArazzoStepExecutor { _, inputs, _ -> received = inputs; succeeded() }, plugins = plugins,
        )

        executor.execute(workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("paired-input"))

        assertEquals(listOf(OrchestrationHookPhase.BEFORE, OrchestrationHookPhase.AFTER), observedPhases)
        assertEquals("after-mapped", received)
    }

    @Test
    fun stagedPluginTransformsInputsAcrossRunScopedStateAndCanRejectScheduling() = runTest {
        val receivedInputs = mutableListOf<String>()
        val plugins = installOrchestrationPlugins {
            install("run-plugin") {
                executor = OrchestrationPluginExecutor { context ->
                    if (context.stage == OrchestrationStage.PREPARE) context.state.put("prepared", true)
                    if (context.stage == OrchestrationStage.MAP_INPUT) {
                        check(context.state.get("prepared") == true)
                        context.input = "{\"mapped\":true}"
                    }
                    if (context.stage == OrchestrationStage.SCHEDULE && (context.payload as ArazzoStep).stepId == "second") {
                        context.reject(ExecutionFailure("plugin_rejected", "Step rejected"))
                    }
                }
            }
        }
        val executor = ArazzoRunExecutor(
            ArazzoStepExecutor { _, inputs, _ -> receivedInputs += inputs; succeeded() },
            plugins = plugins,
        )

        val result = executor.execute(workflow, workflowIdentity, RunId("plugin-run"), inputs = "{}")

        assertEquals(listOf("{\"mapped\":true}"), receivedInputs)
        assertEquals("plugin_rejected", assertIs<ExecutionOutcome.Failed>(result.outcome).failure.code)
        assertEquals(listOf("first"), assertIs<ExecutionOutcome.Failed>(result.outcome).completedSteps)
    }

    @Test
    fun preparationValidationSchedulingAndInputMappingCanRejectBeforeStepDispatch() = runTest {
        for (stage in listOf(OrchestrationStage.PREPARE, OrchestrationStage.VALIDATE,
            OrchestrationStage.SCHEDULE, OrchestrationStage.MAP_INPUT)) {
            var dispatched = 0
            val plugins = installOrchestrationPlugins {
                install("reject-$stage") {
                    stages += stage
                    executor = OrchestrationPluginExecutor { it.reject(ExecutionFailure("rejected_$stage", "$stage rejected")) }
                }
            }
            val executor = ArazzoRunExecutor(
                ArazzoStepExecutor { _, _, _ -> dispatched++; succeeded() }, plugins = plugins,
            )

            val result = executor.execute(workflow.copy(steps = workflow.steps.take(1)), workflowIdentity,
                RunId("reject-$stage"))

            assertEquals(0, dispatched, "$stage rejection must happen before dispatch")
            assertEquals("rejected_$stage", assertIs<ExecutionOutcome.Failed>(result.outcome).failure.code)
        }
    }

    @Test
    fun thrownAdapterStageFailuresRunFailureAndCleanupWithoutDispatch() = runTest {
        for (stage in listOf(OrchestrationStage.PREPARE, OrchestrationStage.VALIDATE,
            OrchestrationStage.SCHEDULE, OrchestrationStage.MAP_INPUT)) {
            var dispatched = 0
            val unwound = mutableListOf<OrchestrationStage>()
            val plugins = installOrchestrationPlugins {
                install("throw-at-$stage") {
                    stages += stage
                    executor = OrchestrationPluginExecutor { error("sensitive detail at $stage") }
                }
                install("unwind-$stage") {
                    stages += OrchestrationStage.FAILURE
                    stages += OrchestrationStage.CLEANUP
                    executor = OrchestrationPluginExecutor { context ->
                        unwound += context.stage
                        if (context.stage == OrchestrationStage.FAILURE) {
                            check((context.payload as ExecutionFailure).message == "Orchestration execution failed")
                        }
                    }
                }
            }
            val executor = ArazzoRunExecutor(
                ArazzoStepExecutor { _, _, _ -> dispatched++; succeeded() }, plugins = plugins,
            )

            val thrown = kotlin.test.assertFailsWith<IllegalStateException> {
                executor.execute(workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("throw-$stage"))
            }

            assertEquals("sensitive detail at $stage", thrown.message)
            assertEquals(0, dispatched, "$stage failure must happen before step dispatch")
            assertEquals(listOf(OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), unwound)
        }
    }

    @Test
    fun pluginMetadataIsNamespacedRedactedAndMergedIntoEvents() = runTest {
        val plugins = installOrchestrationPlugins {
            install("audit") {
                metadataProvider = OrchestrationMetadataProvider {
                    mapOf("trace" to "plugin", "clientSecret" to "do-not-leak")
                }
            }
        }
        val sink = RecordingSink()
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { _, _, _ -> succeeded() }, plugins = plugins)

        executor.execute(workflow.copy(steps = emptyList()), workflowIdentity, RunId("metadata"),
            metadata = mapOf("audit.trace" to "caller"), events = sink)

        assertEquals("plugin", sink.events.first().metadata.values["audit.trace"])
        assertEquals("[REDACTED]", sink.events.first().metadata.values["audit.clientSecret"])
    }

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
        }, plugins = budgetPolicyRegistry())

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
        val pluginStages = mutableListOf<OrchestrationStage>()
        val plugins = installOrchestrationPlugins {
            install("terminal-observer") {
                stages += OrchestrationStage.FAILURE
                stages += OrchestrationStage.CLEANUP
                executor = OrchestrationPluginExecutor { pluginStages += it.stage }
            }
        }
        val sink = RecordingSink(blockOnCancelledTerminal = true)
        val executor = ArazzoRunExecutor(ArazzoStepExecutor { step, _, scope ->
            scope.acquireCall(constituent)?.let { return@ArazzoStepExecutor failed(it) }
            try {
                calls += step.stepId
                awaitCancellation()
            } finally {
                scope.releaseCall(constituent)
            }
        }, plugins = plugins)

        val run = launch(start = CoroutineStart.UNDISPATCHED) { executor.execute(workflow, workflowIdentity, RunId("run-cancel"), events = sink) }
        run.cancelAndJoin()

        assertEquals(listOf("first"), calls)
        assertEquals(ExecutionEventOutcome.CANCELLED, sink.events.last().terminal)
        assertEquals(ExecutionEventKind.RUN_FINISHED, sink.events.last().kind)
        assertEquals(1, sink.events.count { it.kind == ExecutionEventKind.STEP_STARTED })
        assertEquals(listOf(OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), pluginStages)
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
            plugins = budgetPolicyRegistry(),
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
    fun budgetValuesDoNotEnableDeadlinePolicyWithoutInstallingItsPlugin() = runTest {
        var calls = 0
        val executor = ArazzoRunExecutor(
            ArazzoStepExecutor { _, _, _ -> calls++; succeeded() },
            now = { Instant.fromEpochMilliseconds(42L) },
        )

        val result = executor.execute(
            workflow.copy(steps = workflow.steps.take(1)), workflowIdentity, RunId("no-budget-plugin"),
            budget = ExecutionBudget(deadline = Instant.fromEpochMilliseconds(42L)),
        )

        assertEquals(1, calls)
        assertIs<ExecutionOutcome.Succeeded>(result.outcome)
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
            plugins = budgetPolicyRegistry(),
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

    /** Narrow test policy for Arazzo's policy integration; the optional budget artifact is tested separately. */
    private fun budgetPolicyRegistry() = OrchestrationPluginRegistry(listOf(orchestrationPlugin("test-budget-policy") {
        invocationPolicy = InvocationPolicyPlugin { _, budget, now ->
            object : InvocationPolicy {
                override val enforcedDeadline get() = budget.deadline
                private var calls = 0

                private fun deadlineFailure() = if (budget.deadline?.let { now() >= it } == true) {
                    ExecutionFailure("deadline", "Execution budget exhausted: deadline")
                } else null

                override suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure? = deadlineFailure()

                override suspend fun acquireCall(
                    metadata: ExecutionMetadata,
                    identity: ConstituentIdentity,
                    retry: Boolean,
                ): ExecutionFailure? {
                    deadlineFailure()?.let { return it }
                    if (budget.maxCalls?.let { calls >= it } == true) {
                        return ExecutionFailure("calls", "Execution budget exhausted: calls")
                    }
                    calls++
                    return null
                }

                override suspend fun releaseCall(identity: ConstituentIdentity) = Unit
                override suspend fun checkpoint(
                    metadata: ExecutionMetadata,
                    transitions: Int,
                    nestingDepth: Int,
                ): ExecutionFailure? = deadlineFailure()
            }
        }
    }))

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
