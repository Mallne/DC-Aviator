package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/** Shared, atomic accounting for one run. Construct one instance per run and pass it through every
 * binding, retry, fan-out and nested invocation. Callers must release admitted calls in `finally`.
 */
class EnforcedInvocationScope private constructor(
    override val metadata: ExecutionMetadata,
    override val budget: ExecutionBudget,
    private val authorizeCall: suspend (ConstituentIdentity) -> ExecutionFailure?,
    private val now: () -> Instant,
    private val state: State,
) : InvocationScope {
    constructor(
        metadata: ExecutionMetadata,
        budget: ExecutionBudget,
        authorizeCall: suspend (ConstituentIdentity) -> ExecutionFailure? = { null },
        now: () -> Instant = { Clock.System.now() },
    ) : this(metadata, budget, authorizeCall, now, State())

    /** Creates a step-scoped view sharing all run-wide reservations and counters. */
    fun forStep(stepId: String): EnforcedInvocationScope {
        require(stepId.isNotBlank()) { "stepId must not be blank" }
        return EnforcedInvocationScope(metadata.copy(stepId = stepId), budget, authorizeCall, now, state)
    }

    override suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure? {
        currentCoroutineContext().ensureActive()
        deadlineFailure()?.let { return it }
        return authorizeCall(identity)
    }

    override suspend fun acquireCall(identity: ConstituentIdentity, retry: Boolean): ExecutionFailure? {
        currentCoroutineContext().ensureActive()
        return state.mutex.withLock {
            deadlineFailure()?.let { return@withLock it }
            if (budget.maxCalls?.let { state.calls >= it } == true) return@withLock failure("calls")
            if (budget.maxCallsPerStep?.let { (state.stepCalls[metadata.stepId] ?: 0) >= it } == true) return@withLock failure("step_calls")
            if (retry && budget.maxRetries?.let { state.retries >= it } == true) return@withLock failure("retries")
            if (retry && budget.maxRetriesPerStep?.let { (state.stepRetries[metadata.stepId] ?: 0) >= it } == true) return@withLock failure("step_retries")
            if (budget.maxConcurrency?.let { state.activeCalls.values.sum() >= it } == true) return@withLock failure("concurrency")
            state.calls++
            state.stepCalls[metadata.stepId] = (state.stepCalls[metadata.stepId] ?: 0) + 1
            if (retry) {
                state.retries++
                state.stepRetries[metadata.stepId] = (state.stepRetries[metadata.stepId] ?: 0) + 1
            }
            state.activeCalls[identity] = (state.activeCalls[identity] ?: 0) + 1
            null
        }
    }

    override suspend fun releaseCall(identity: ConstituentIdentity) {
        withContext(NonCancellable) {
            state.mutex.withLock {
                val count = checkNotNull(state.activeCalls[identity]) { "No admitted call to release" }
                if (count == 1) state.activeCalls.remove(identity) else state.activeCalls[identity] = count - 1
            }
        }
    }

    override suspend fun checkpoint(transitions: Int, nestingDepth: Int): ExecutionFailure? {
        require(transitions >= 0 && nestingDepth >= 0)
        currentCoroutineContext().ensureActive()
        return state.mutex.withLock {
            deadlineFailure()?.let { return@withLock it }
            if (budget.maxTransitions?.let { transitions > it - state.transitions } == true) return@withLock failure("transitions")
            if (budget.maxNestingDepth?.let { nestingDepth > it } == true) return@withLock failure("nesting")
            state.transitions += transitions
            null
        }
    }

    private fun deadlineFailure(): ExecutionFailure? =
        if (budget.deadline?.let { now() >= it } == true) failure("deadline") else null

    private fun failure(code: String) = ExecutionFailure(code, "Execution budget exhausted: $code")

    private class State {
        val mutex = Mutex()
        val activeCalls = mutableMapOf<ConstituentIdentity, Int>()
        val stepCalls = mutableMapOf<String?, Int>()
        val stepRetries = mutableMapOf<String?, Int>()
        var calls = 0
        var retries = 0
        var transitions = 0
    }
}
