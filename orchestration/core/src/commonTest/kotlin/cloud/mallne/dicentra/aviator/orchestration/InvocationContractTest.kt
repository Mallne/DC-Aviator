package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.OperationBinding
import cloud.mallne.dicentra.aviator.orchestration.execution.ResolvedOperation
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentResult
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEvent
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEventKind
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEventSink
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.redacted
import cloud.mallne.dicentra.aviator.orchestration.model.redacting
import cloud.mallne.dicentra.aviator.orchestration.policy.ConstituentOrdering
import cloud.mallne.dicentra.aviator.orchestration.policy.EmptySelectionPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionStrategy
import cloud.mallne.dicentra.aviator.orchestration.policy.PartialFailurePolicy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Tests the common contracts with a deterministic fake, not Aviator's production transport. */
class InvocationContractTest {
    private val locator = ServiceLocator("weather>gather")
    private val a = identity("a", "firstOperation")
    private val b = identity("b", "differentOperation")

    @Test
    fun multipleOperationsAggregateInPolicyOrderAndReceiveIsolatedMetadata() = runImmediate {
        val binding = FakeBinding(listOf(b, a, identity("c", "update", ServiceLocator("weather>update"))))
        val invocation = CapabilityInvocation(locator, contractPolicy())
        val firstScope = scope("run-1")
        val result = binding.invoke(binding.resolve(invocation), JsonPrimitive("input"), firstScope)

        assertEquals(listOf(a, b), result.constituents.map { it.identity })
        assertEquals(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))), aggregate(result))
        assertEquals(listOf(a, b), firstScope.authorized)
        assertEquals(listOf(a, b), firstScope.admitted)
        assertNotSame(binding.contexts[0], binding.contexts[1])

        val secondScope = scope("run-2")
        binding.invoke(binding.resolve(invocation), JsonPrimitive("input"), secondScope)
        assertEquals(listOf("run-1", "run-1", "run-2", "run-2"), binding.contexts.map { it.runId.value })
        assertEquals(2, firstScope.admitted.size)
        assertEquals(2, secondScope.admitted.size)
    }

    @Test
    fun restrictionsAreNotWidenedAndAuthorizationIsCheckedAtInvocation() = runImmediate {
        val binding = FakeBinding(listOf(a, b))
        val prepared = binding.resolve(CapabilityInvocation(locator, contractPolicy(), setOf(b)))
        val deniedScope = scope(denied = setOf(b))
        val denied = binding.invoke(prepared, JsonPrimitive("input"), deniedScope)

        assertEquals(listOf(b), deniedScope.authorized)
        assertTrue(binding.contexts.isEmpty())
        assertTrue(deniedScope.admitted.isEmpty())
        assertIs<CapabilityOutcome.Failed>(denied.outcome)
        val accepted = binding.invoke(prepared, JsonPrimitive("input"), scope())
        assertEquals(listOf(b), accepted.constituents.map { it.identity })
    }

    @Test
    fun partialFailuresEmptySelectionAndFallbackFollowExplicitPolicy() = runImmediate {
        val binding = FakeBinding(listOf(a, b), failures = setOf(a))
        val invocation = CapabilityInvocation(locator, contractPolicy())
        val partial = binding.invoke(binding.resolve(invocation), JsonPrimitive("input"), scope())
        assertIs<CapabilityOutcome.Succeeded>(partial.outcome)
        assertIs<ConstituentOutcome.Failed>(partial.constituents.first().outcome)
        val strict = invocation.copy(policy = invocation.policy.copy(partialFailure = PartialFailurePolicy.REQUIRE_ALL))
        assertIs<CapabilityOutcome.Failed>(binding.invoke(binding.resolve(strict), JsonPrimitive("input"), scope()).outcome)

        val fallback = invocation.copy(policy = invocation.policy.copy(strategy = ExecutionStrategy.FIRST_USABLE))
        assertEquals(JsonArray(listOf(JsonPrimitive("b"))), aggregate(binding.invoke(binding.resolve(fallback), JsonPrimitive("input"), scope())))
        val noTargets = invocation.copy(constituents = emptySet())
        assertIs<CapabilityOutcome.Failed>(binding.invoke(binding.resolve(noTargets), JsonPrimitive("input"), scope()).outcome)
        val allowEmpty = noTargets.copy(policy = noTargets.policy.copy(emptySelection = EmptySelectionPolicy.SUCCEED))
        assertEquals(JsonArray(emptyList()), aggregate(binding.invoke(binding.resolve(allowEmpty), JsonPrimitive("input"), scope())))
    }

    @Test
    fun budgetsCountConstituentsRetriesAndConcurrency() = runImmediate {
        val binding = FakeBinding(listOf(a, b))
        val limited = scope(budget = ExecutionBudget(maxCalls = 1))
        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, contractPolicy())), JsonPrimitive("input"), limited)
        assertEquals("calls", assertIs<CapabilityOutcome.Failed>(result.outcome).failure.code)
        assertEquals(1, binding.contexts.size)
        assertEquals(listOf(a), limited.admitted)

        val shared = scope(budget = ExecutionBudget(maxCalls = 3, maxRetries = 1, maxConcurrency = 1, maxTransitions = 1, maxNestingDepth = 0))
        assertNull(shared.acquireCall(a))
        assertEquals("concurrency", shared.acquireCall(b)?.code)
        shared.releaseCall(a)
        assertNull(shared.acquireCall(a, retry = true))
        shared.releaseCall(a)
        assertEquals("retries", shared.acquireCall(a, retry = true)?.code)
        assertEquals(2, shared.admitted.size)
        assertNull(shared.checkpoint(transitions = 1))
        assertEquals("transitions", shared.checkpoint(transitions = 1)?.code)
        assertEquals("nesting", shared.checkpoint(nestingDepth = 1)?.code)
    }

    @Test
    fun callbackCancellationPropagatesAndDoesNotScheduleAnotherConstituent() {
        val binding = FakeBinding(listOf(a, b), cancelled = a)
        val runScope = scope(budget = ExecutionBudget(maxConcurrency = 1))
        val cancellation = assertFailsWith<CancellationException> {
            runImmediate {
                binding.invoke(binding.resolve(CapabilityInvocation(locator, contractPolicy())), JsonPrimitive("input"), runScope)
            }
        }
        assertEquals("cancel", cancellation.message)
        assertEquals(1, binding.contexts.size)
        runImmediate {
            assertNull(runScope.acquireCall(b)) // finally released the cancelled call's slot
            runScope.releaseCall(b)
        }
    }

    @Test
    fun applicationLocalAndRegistryStyleBindingsPreserveTheSameLocatorSemantics() = runImmediate {
        // Registry visibility is supplied before binding; neither environment changes core identity.
        val local = FakeBinding(listOf(a, b))
        val registryVisible = FakeBinding(listOf(a, b, identity("private", "hidden")).filter { it.providerId != "private" })
        val invocation = CapabilityInvocation(locator, contractPolicy())
        val localResult = local.invoke(local.resolve(invocation), JsonPrimitive("input"), scope("local"))
        val hostedResult = registryVisible.invoke(registryVisible.resolve(invocation), JsonPrimitive("input"), scope("hosted"))
        assertEquals(localResult, hostedResult)
    }

    @Test
    fun suspendingEventDeliveryProvidesBackpressureAndPropagatesSinkFailure() {
        var pending: Continuation<Unit>? = null
        var completion: Result<Unit>? = null
        val delivered = mutableListOf<ExecutionEventKind>()
        val sink = ExecutionEventSink { event ->
            delivered += event.kind
            if (event.kind == ExecutionEventKind.RUN_STARTED) suspendCoroutine { pending = it }
        }
        val emit: suspend () -> Unit = {
            sink.emit(ExecutionEvent(scope().metadata, ExecutionEventKind.RUN_STARTED))
            sink.emit(ExecutionEvent(scope().metadata, ExecutionEventKind.RUN_FINISHED))
        }
        emit.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        assertEquals(listOf(ExecutionEventKind.RUN_STARTED), delivered)
        assertNull(completion)
        val failure = CancellationException("sink cancelled")
        checkNotNull(pending).resumeWith(Result.failure(failure))
        assertEquals(failure, checkNotNull(completion).exceptionOrNull())
        assertEquals(listOf(ExecutionEventKind.RUN_STARTED), delivered)
    }

    @Test
    fun negativeBudgetLimitsAreRejectedButZeroLimitsAreValid() {
        assertFailsWith<IllegalArgumentException> { ExecutionBudget(maxCalls = -1) }
        assertFailsWith<IllegalArgumentException> { ExecutionBudget(maxRetries = -1) }
        assertFailsWith<IllegalArgumentException> { ExecutionBudget(maxConcurrency = -1) }
        assertFailsWith<IllegalArgumentException> { ExecutionBudget(maxTransitions = -1) }
        assertFailsWith<IllegalArgumentException> { ExecutionBudget(maxNestingDepth = -1) }
        assertFailsWith<IllegalArgumentException> { ExecutionBudget(maxCallsPerStep = -1) }
        assertFailsWith<IllegalArgumentException> { ExecutionBudget(maxRetriesPerStep = -1) }
        assertEquals(0, ExecutionBudget(maxCalls = 0).maxCalls)
    }

    @Test
    fun executionBudgetSerializesTypedAbsoluteDeadline() {
        val budget = ExecutionBudget(deadline = Instant.fromEpochMilliseconds(1_000L))
        assertEquals(budget, kotlinx.serialization.json.Json.decodeFromString<ExecutionBudget>(
            kotlinx.serialization.json.Json.encodeToString(budget),
        ))
    }

    @Test
    fun sharedDeadlineRejectsCallsBeforeSideEffects() = runImmediate {
        val runScope = ContractScope(scope().metadata, ExecutionBudget(deadline = Instant.fromEpochMilliseconds(10)), now = { Instant.fromEpochMilliseconds(10) })
        assertEquals("deadline", runScope.checkpoint()?.code)
        assertEquals("deadline", runScope.acquireCall(a)?.code)
        assertTrue(runScope.admitted.isEmpty())
    }

    @Test
    fun productionScopeAtomicallyEnforcesSharedBudgetsAndReleasesReservations() = runImmediate {
        val runScope = EnforcedInvocationScope(
            scope().metadata,
            ExecutionBudget(maxCalls = 3, maxConcurrency = 1, maxTransitions = 1, maxRetries = 0),
            now = { Instant.fromEpochMilliseconds(1L) },
        )
        assertNull(runScope.acquireCall(a))
        assertEquals("concurrency", runScope.acquireCall(b)?.code)
        runScope.releaseCall(a)
        assertNull(runScope.acquireCall(b))
        runScope.releaseCall(b)
        assertEquals("retries", runScope.acquireCall(a, retry = true)?.code)
        assertNull(runScope.acquireCall(a))
        runScope.releaseCall(a)
        assertEquals("calls", runScope.acquireCall(a)?.code)
        assertNull(runScope.checkpoint(transitions = 1))
        assertEquals("transitions", runScope.checkpoint(transitions = 1)?.code)
    }

    @Test
    fun productionScopeHonorsDeadlineAndMetadataRedaction() = runImmediate {
        val metadata = scope().metadata.copy(values = mapOf(
            "trace.id" to "trace-1", "Authorization" to "Bearer secret", "db.password" to "pw", "custom" to "ok",
        ))
        assertEquals(mapOf("trace.id" to "trace-1", "Authorization" to "[REDACTED]", "db.password" to "[REDACTED]", "custom" to "ok"), metadata.redacted().values)
        val expired = EnforcedInvocationScope(metadata, ExecutionBudget(deadline = Instant.fromEpochMilliseconds(10)), now = { Instant.fromEpochMilliseconds(10L) })
        assertEquals("deadline", expired.checkpoint()?.code)
        assertEquals("deadline", expired.acquireCall(a)?.code)

        var observed: ExecutionEvent? = null
        val safeSink = ExecutionEventSink { observed = it }.redacting()
        safeSink.emit(ExecutionEvent(metadata, ExecutionEventKind.RUN_STARTED))
        assertEquals("[REDACTED]", checkNotNull(observed).metadata.values.getValue("Authorization"))
        assertEquals("trace-1", checkNotNull(observed).metadata.values.getValue("trace.id"))
    }

    @Test
    fun productionScopeReleasesConcurrencyReservationAfterCallerCancellation() {
        val runScope = EnforcedInvocationScope(scope().metadata, ExecutionBudget(maxConcurrency = 1))
        val job = Job()
        var completion: Result<Unit>? = null
        val block: suspend () -> Unit = {
            assertNull(runScope.acquireCall(a))
            job.cancel()
            runScope.releaseCall(a)
        }
        block.startCoroutine(object : Continuation<Unit> {
            override val context = job
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        assertNull(checkNotNull(completion).exceptionOrNull())
        runImmediate {
            assertNull(runScope.acquireCall(b))
            runScope.releaseCall(b)
        }
    }

    @Test
    fun stepScopedViewsEnforceLocalAndRunWideLimitsTogether() = runImmediate {
        val run = EnforcedInvocationScope(
            scope().metadata,
            ExecutionBudget(maxCalls = 3, maxCallsPerStep = 1),
        )
        val first = run.forStep("step-1")
        assertNull(first.acquireCall(a))
        first.releaseCall(a)
        assertEquals("step_calls", first.acquireCall(a)?.code)
        val second = run.forStep("step-2")
        assertNull(second.acquireCall(b))
        second.releaseCall(b)
        val third = run.forStep("step-3")
        assertNull(third.acquireCall(a))
        third.releaseCall(a)
        assertEquals("calls", run.forStep("step-4").acquireCall(b)?.code)

        val retryScope = EnforcedInvocationScope(scope().metadata, ExecutionBudget(maxCallsPerStep = 2, maxRetriesPerStep = 0)).forStep("retry-step")
        assertEquals("step_retries", retryScope.acquireCall(a, retry = true)?.code)
    }

    @Test
    fun concurrentInvocationsShareOneAtomicConcurrencyReservation() = runTest {
        val runScope = EnforcedInvocationScope(scope().metadata, ExecutionBudget(maxConcurrency = 1))
        val releaseFirst = CompletableDeferred<Unit>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            assertNull(runScope.acquireCall(a))
            releaseFirst.await()
            runScope.releaseCall(a)
        }

        val rejected = async { runScope.acquireCall(b) }.await()
        assertEquals("concurrency", rejected?.code)
        releaseFirst.complete(Unit)
        first.await()
        assertNull(runScope.acquireCall(b))
        runScope.releaseCall(b)
    }

    private fun identity(provider: String, operation: String, target: ServiceLocator = locator) =
        ConstituentIdentity(target, provider, "https://example.test/$provider", "revision-1", operation)

    private fun scope(run: String = "run", denied: Set<ConstituentIdentity> = emptySet(), budget: ExecutionBudget = ExecutionBudget()) =
        ContractScope(ExecutionMetadata(RunId(run), WorkflowIdentity("test", "1", "test:workflow", "1"), "step"), budget, denied)

    private fun aggregate(result: CapabilityResult): JsonElement =
        assertIs<BodyValue.JsonValue>(assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate).value

    private class Handle(override val invocation: CapabilityInvocation, val owner: Any) : ResolvedOperation

    private class FakeBinding(
        private val available: List<ConstituentIdentity>,
        private val failures: Set<ConstituentIdentity> = emptySet(),
        private val cancelled: ConstituentIdentity? = null,
    ) : OperationBinding<JsonElement> {
        val contexts = mutableListOf<ExecutionMetadata>()

        override suspend fun resolve(invocation: CapabilityInvocation): ResolvedOperation = Handle(invocation, this)

        override suspend fun invoke(operation: ResolvedOperation, input: JsonElement, scope: InvocationScope): CapabilityResult {
            val handle = assertIs<Handle>(operation)
            check(handle.owner === this)
            val invocation = handle.invocation
            val matches = available.filter(invocation::accepts)
            val selected = when (invocation.policy.ordering) {
                ConstituentOrdering.RESOLUTION -> matches
                ConstituentOrdering.IDENTITY -> matches.sortedWith(compareBy(
                    { it.sourceId }, { it.sourceRevision }, { it.providerId }, { it.operationId }, { it.operationPath },
                ))
            }
            val results = mutableListOf<ConstituentResult>()
            val values = mutableListOf<JsonElement>()
            fun failed(failure: ExecutionFailure) = CapabilityResult(invocation.locator, CapabilityOutcome.Failed(failure), results.toList())
            if (selected.isEmpty() && invocation.policy.emptySelection == EmptySelectionPolicy.FAIL) {
                return failed(ExecutionFailure("empty", "No eligible constituents"))
            }
            for (identity in selected) {
                val denied = scope.authorize(identity)
                if (denied != null) {
                    results += ConstituentResult(identity, ConstituentOutcome.Failed(denied))
                    continue
                }
                scope.acquireCall(identity)?.let { return failed(it) }
                try {
                    contexts += scope.metadata.copy(values = scope.metadata.values.toMap())
                    if (identity == cancelled) throw CancellationException("cancel")
                    if (identity in failures) {
                        results += ConstituentResult(identity, ConstituentOutcome.Failed(ExecutionFailure("upstream", "Unavailable")))
                    } else {
                        val value = JsonPrimitive(identity.providerId)
                        values += value
                        results += ConstituentResult(identity, ConstituentOutcome.Succeeded(BodyValue.JsonValue(value)))
                        if (invocation.policy.strategy == ExecutionStrategy.FIRST_USABLE) break
                    }
                } finally {
                    scope.releaseCall(identity)
                }
            }
            val strictFailure = invocation.policy.strategy == ExecutionStrategy.AGGREGATE &&
                invocation.policy.partialFailure == PartialFailurePolicy.REQUIRE_ALL &&
                results.any { it.outcome is ConstituentOutcome.Failed }
            if (strictFailure || selected.isNotEmpty() && values.isEmpty()) return failed(ExecutionFailure("unusable", "No usable aggregate"))
            return CapabilityResult(invocation.locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(JsonArray(values))), results.toList())
        }
    }

    private fun <T> runImmediate(block: suspend () -> T): T {
        var completion: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { completion = result }
        })
        return checkNotNull(completion) { "Fixture unexpectedly suspended" }.getOrThrow()
    }
}
