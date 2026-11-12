package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure

/** Result of admission for one actual constituent call, independent of the client transport. */
sealed interface ConstituentCallResult<out T> {
    data class Admitted<T>(val value: T) : ConstituentCallResult<T>
    data class Rejected(val failure: ExecutionFailure) : ConstituentCallResult<Nothing>
}

/**
 * Authorize and account for one actual attempt. The callback must represent exactly one call;
 * callers must use this function again for every retry or additional network invocation.
 * Exceptions and cancellation from the callback propagate to the caller.
 *
 * This helper does not account for retained bytes or prevent an opaque client from making
 * additional calls internally. Such clients require an appropriate lower-level integration.
 */
suspend fun <T> InvocationScope.callConstituent(
    identity: ConstituentIdentity,
    retry: Boolean = false,
    call: suspend () -> T,
): ConstituentCallResult<T> {
    authorize(identity)?.let { return ConstituentCallResult.Rejected(it) }
    acquireCall(identity, retry)?.let { return ConstituentCallResult.Rejected(it) }
    return try {
        ConstituentCallResult.Admitted(call())
    } finally {
        // InvocationScope implementations must make release cancellation-safe. Keeping observer
        // callbacks outside NonCancellable ensures a suspending event sink cannot pin cancellation.
        releaseCall(identity)
    }
}
