package cloud.mallne.dicentra.aviator.core.execution

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Per-invocation control at a client transport boundary. Implementations must invoke [call] at
 * most once, release admission on failure or cancellation, and propagate cancellation. Clients
 * call this for each transport attempt, not once per pipeline or resolved service.
 *
 * A transport with hidden retries or redirects must disable them or account for them separately.
 */
abstract class AviatorCallControl : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AviatorCallControl>

    abstract suspend fun <T> execute(
        retry: Boolean = false,
        call: suspend () -> T,
    ): T
}

/** Execute one client transport attempt under the caller's optional control. */
suspend fun <T> controlledAviatorCall(
    retry: Boolean = false,
    call: suspend () -> T,
): T {
    val control = currentCoroutineContext()[AviatorCallControl]
    return if (control == null) call() else control.execute(retry, call)
}
