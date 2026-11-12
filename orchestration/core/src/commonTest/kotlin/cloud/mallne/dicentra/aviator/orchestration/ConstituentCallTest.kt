package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.ConstituentCallResult
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.callConstituent
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.core.execution.controlledAviatorCall
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class ConstituentCallTest {
    private val identity = ConstituentIdentity(ServiceLocator("weather>gather"), "provider", "source", "1")
    private val metadata = ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "doc", "1"))

    @Test
    fun coreTransportControlUsesSharedRunBudget() = runTest {
        val scope = EnforcedInvocationScope(metadata, ExecutionBudget(maxCalls = 1))
        var calls = 0
        withContext(InvocationCallControl(scope, identity)) {
            controlledAviatorCall(call = { calls++ })
            val failure = assertFailsWith<InvocationRejectedException> { controlledAviatorCall(call = { calls++ }) }
            assertEquals("calls", failure.failure.code)
        }
        assertEquals(1, calls)
    }

    @Test
    fun rejectsBeforeCallingAndCountsEachAttempt() = runTest {
        val denied = EnforcedInvocationScope(metadata, ExecutionBudget(), authorizeCall = { ExecutionFailure("denied", "Denied") })
        var calls = 0
        val rejected = denied.callConstituent(identity, call = { calls++ })
        assertEquals("denied", assertIs<ConstituentCallResult.Rejected>(rejected).failure.code)
        val scope = EnforcedInvocationScope(metadata, ExecutionBudget(maxCalls = 1))
        assertIs<ConstituentCallResult.Admitted<Int>>(scope.callConstituent(identity, call = { ++calls }))
        val exhausted = scope.callConstituent(identity, retry = true, call = { calls++ })
        assertEquals("calls", assertIs<ConstituentCallResult.Rejected>(exhausted).failure.code)
        assertEquals(1, calls)
    }

    @Test
    fun retriesAreChargedAtEachTransportAttempt() = runTest {
        val scope = EnforcedInvocationScope(metadata, ExecutionBudget(maxRetries = 1))
        assertIs<ConstituentCallResult.Admitted<String>>(
            scope.callConstituent(identity, call = { "abc" }),
        )
        assertIs<ConstituentCallResult.Admitted<String>>(
            scope.callConstituent(identity, retry = true, call = { "d" }),
        )
        val retryExhausted = scope.callConstituent(identity, retry = true, call = { "x" })
        assertEquals("retries", assertIs<ConstituentCallResult.Rejected>(retryExhausted).failure.code)
    }

    @Test
    fun cancellationReleasesConcurrencySlot() = runTest {
        val scope = EnforcedInvocationScope(metadata, ExecutionBudget(maxConcurrency = 1))
        val started = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.callConstituent(identity, call = {
                started.complete(Unit)
                awaitCancellation()
            })
        }
        started.await()
        job.cancelAndJoin()
        assertIs<ConstituentCallResult.Admitted<String>>(scope.callConstituent(identity, call = { "ok" }))
    }
}
