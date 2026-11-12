package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget

/** Shared run services, not credentials or state stored in a globally reusable prepared handle. */
interface InvocationScope {
    val metadata: ExecutionMetadata
    val budget: ExecutionBudget

    /** Rechecked immediately before each actual call, including retries. Null means authorized. */
    suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure?

    /**
     * Atomically reserve one actual call (and retry when applicable), plus a concurrency slot.
     * Reject exhausted budgets before any side effect; null means admitted. A binding must call
     * [releaseCall] in finally after admission, including cancellation. Release must complete even
     * when the caller's Job is cancelled. Cumulative counts are not refunded. Implementations
     * propagate cancellation and enforce the shared deadline.
     */
    suspend fun acquireCall(identity: ConstituentIdentity, retry: Boolean = false): ExecutionFailure?

    /** Release only the active-call slot, never cumulative call/retry counts. */
    suspend fun releaseCall(identity: ConstituentIdentity)

    /** Check deadline and charge transitions; validate current nesting, without counting a call. */
    suspend fun checkpoint(transitions: Int = 0, nestingDepth: Int = 0): ExecutionFailure?
}
