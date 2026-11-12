package cloud.mallne.dicentra.aviator.plugin.orchestration.budget

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.core.execution.controlledAviatorCall
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ExecutionBudgetPluginTest {
    private val first = ConstituentIdentity(ServiceLocator("weather>get"), "one", "source", "rev")
    private val second = first.copy(providerId = "two")
    private val metadata = ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "doc", "rev"))
    private val registry = OrchestrationPluginRegistry(listOf(ExecutionBudgetPlugin.install()))

    @Test
    fun atomicallyEnforcesCallRetryConcurrencyAndStepLimitsAcrossStepViews() = runTest {
        val shared = EnforcedInvocationScope(metadata,
            ExecutionBudget(maxCalls = 2, maxConcurrency = 1),
            policyPlugins = registry.invocationPolicyPlugins())
        val firstStep = shared.forStep("first")
        assertNull(firstStep.acquireCall(first))
        assertEquals("concurrency", shared.forStep("other").acquireCall(second)?.code)
        firstStep.releaseCall(first)
        assertNull(shared.forStep("other").acquireCall(second))
        shared.forStep("other").releaseCall(second)
        assertEquals("calls", shared.acquireCall(first)?.code)

        val stepLimited = EnforcedInvocationScope(metadata, ExecutionBudget(maxCallsPerStep = 1),
            policyPlugins = registry.invocationPolicyPlugins())
        val limitedStep = stepLimited.forStep("limited")
        assertNull(limitedStep.acquireCall(first))
        limitedStep.releaseCall(first)
        assertEquals("step_calls", limitedStep.acquireCall(first)?.code)
        assertNull(stepLimited.forStep("unlimited-step").acquireCall(second))
        stepLimited.forStep("unlimited-step").releaseCall(second)

        val retryLimited = EnforcedInvocationScope(metadata, ExecutionBudget(maxRetries = 1),
            policyPlugins = registry.invocationPolicyPlugins())
        assertNull(retryLimited.acquireCall(first))
        retryLimited.releaseCall(first)
        assertNull(retryLimited.acquireCall(first, retry = true))
        retryLimited.releaseCall(first)
        assertEquals("retries", retryLimited.acquireCall(first, retry = true)?.code)
    }

    @Test
    fun deadlineTransitionsAndNestingAreEnforced() = runTest {
        val expired = EnforcedInvocationScope(metadata,
            ExecutionBudget(deadline = Instant.fromEpochMilliseconds(10), maxTransitions = 1, maxNestingDepth = 0),
            now = { Instant.fromEpochMilliseconds(10) }, policyPlugins = registry.invocationPolicyPlugins())

        assertEquals(Instant.fromEpochMilliseconds(10), expired.enforcedDeadline)
        assertEquals("deadline", expired.authorize(first)?.code)
        assertEquals("deadline", expired.checkpoint(transitions = 1)?.code)

        val active = EnforcedInvocationScope(metadata,
            ExecutionBudget(maxTransitions = 1, maxNestingDepth = 0),
            policyPlugins = registry.invocationPolicyPlugins())
        assertNull(active.checkpoint(transitions = 1))
        assertEquals("transitions", active.checkpoint(transitions = 1)?.code)
        assertEquals("nesting", active.checkpoint(nestingDepth = 1)?.code)
    }

    @Test
    fun policyStateIsPerRunEvenForConcurrentScopes() = runTest {
        val firstRun = EnforcedInvocationScope(metadata, ExecutionBudget(maxCalls = 1), policyPlugins = registry.invocationPolicyPlugins())
        val secondRun = EnforcedInvocationScope(metadata.copy(runId = RunId("second")), ExecutionBudget(maxCalls = 1), policyPlugins = registry.invocationPolicyPlugins())
        val results = listOf(async { firstRun.acquireCall(first) }, async { secondRun.acquireCall(second) }).map { it.await() }

        assertEquals(listOf(null, null), results)
        firstRun.releaseCall(first)
        secondRun.releaseCall(second)
    }

    @Test
    fun limitsAreOptInAndChargeEveryActualTransportAttempt() = runTest {
        val noPolicy = EnforcedInvocationScope(metadata, ExecutionBudget(maxCalls = 0))
        assertNull(noPolicy.acquireCall(first))
        noPolicy.releaseCall(first)

        val limited = EnforcedInvocationScope(metadata, ExecutionBudget(maxCalls = 1),
            policyPlugins = registry.invocationPolicyPlugins())
        var calls = 0
        withContext(InvocationCallControl(limited, first)) {
            controlledAviatorCall(call = { calls++ })
            assertEquals("calls", assertFailsWith<InvocationRejectedException> {
                controlledAviatorCall(retry = true, call = { calls++ })
            }.failure.code)
        }
        assertEquals(1, calls)
    }

    @Test
    fun concurrentCallsShareAnAtomicSlotAndCancellationSafeRelease() = runTest {
        val scope = EnforcedInvocationScope(metadata, ExecutionBudget(maxConcurrency = 1),
            policyPlugins = registry.invocationPolicyPlugins())
        val firstAcquired = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstCall = async {
            assertNull(scope.acquireCall(first))
            firstAcquired.complete(Unit)
            releaseFirst.await()
            scope.releaseCall(first)
        }
        firstAcquired.await()
        assertEquals("concurrency", scope.acquireCall(second)?.code)
        releaseFirst.complete(Unit)
        firstCall.await()
        assertNull(scope.acquireCall(second))
        scope.releaseCall(second)
    }
}
