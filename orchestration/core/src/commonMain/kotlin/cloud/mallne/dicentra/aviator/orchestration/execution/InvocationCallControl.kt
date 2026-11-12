package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.core.execution.AviatorCallControl
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationAttempt
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginState
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStageContext
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** Admission failure at a transport boundary; contains no provider exception or request data. */
class InvocationRejectedException(val failure: ExecutionFailure) : Exception(failure.message)

/** Bind an isolated constituent identity and run scope to calls made by a client pipeline. */
class InvocationCallControl(
    private val scope: InvocationScope,
    private val identity: ConstituentIdentity,
    private val invocation: CapabilityInvocation? = null,
    private val plugins: OrchestrationPluginRegistry = OrchestrationPluginRegistry.Empty,
    private val pluginState: OrchestrationPluginState? = null,
) : AviatorCallControl() {
    override suspend fun <T> execute(retry: Boolean, call: suspend () -> T): T {
        scope.authorize(identity)?.let { throw InvocationRejectedException(it) }
        val state = pluginState
        var primaryFailure: Throwable? = null
        var admitted = false
        try {
            if (invocation != null && state != null) {
                val before = OrchestrationStageContext(OrchestrationStage.BEFORE_ATTEMPT,
                    invocation = invocation, scope = scope, state = state, constituent = identity,
                    payload = OrchestrationAttempt(retry), lifecycleScope = OrchestrationLifecycleScope.ATTEMPT)
                plugins.execute(before)
                before.rejection?.let { throw InvocationRejectedException(it) }
                if (before.shortCircuit != null) {
                    throw InvocationRejectedException(ExecutionFailure(
                        "invalid_attempt_short_circuit", "Transport attempt hooks cannot supply a capability result",
                    ))
                }
            }

            scope.acquireCall(identity, retry)?.let { throw InvocationRejectedException(it) }
            admitted = true
            val result = call()
            if (invocation != null && state != null) {
                val after = OrchestrationStageContext(OrchestrationStage.AFTER_ATTEMPT,
                    invocation = invocation, scope = scope, state = state, constituent = identity,
                    payload = OrchestrationAttempt(retry, result = result),
                    lifecycleScope = OrchestrationLifecycleScope.ATTEMPT)
                plugins.execute(after)
                after.rejection?.let { throw InvocationRejectedException(it) }
            }
            return result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            primaryFailure = cancelled
            if (invocation != null && state != null) {
                try {
                    withContext(NonCancellable) {
                        withTimeoutOrNull(ATTEMPT_CLEANUP_TIMEOUT) {
                            plugins.execute(OrchestrationStageContext(OrchestrationStage.FAILURE,
                                invocation = invocation, scope = scope, state = state, constituent = identity,
                                payload = OrchestrationAttempt(retry, failure = ExecutionFailure("cancelled", "Attempt was cancelled")),
                                lifecycleScope = OrchestrationLifecycleScope.ATTEMPT))
                        }
                    }
                } catch (pluginFailure: Throwable) {
                    cancelled.addSuppressed(pluginFailure)
                }
            }
            throw cancelled
        } catch (cause: Throwable) {
            primaryFailure = cause
            if (invocation != null && state != null) {
                try {
                    plugins.execute(OrchestrationStageContext(OrchestrationStage.FAILURE,
                        invocation = invocation, scope = scope, state = state, constituent = identity,
                        payload = OrchestrationAttempt(retry, failure = ExecutionFailure("attempt_failed", "Transport attempt failed")),
                        lifecycleScope = OrchestrationLifecycleScope.ATTEMPT))
                } catch (pluginFailure: Throwable) {
                    cause.addSuppressed(pluginFailure)
                }
            }
            throw cause
        } finally {
            try {
                if (admitted) scope.releaseCall(identity)
            } finally {
                if (invocation != null && state != null) {
                    try {
                        withContext(NonCancellable) {
                            withTimeoutOrNull(ATTEMPT_CLEANUP_TIMEOUT) {
                                plugins.execute(OrchestrationStageContext(OrchestrationStage.CLEANUP,
                                    invocation = invocation, scope = scope, state = state, constituent = identity,
                                    lifecycleScope = OrchestrationLifecycleScope.ATTEMPT))
                            }
                        }
                    } catch (cleanupFailure: Throwable) {
                        val primary = primaryFailure
                        if (primary == null) throw cleanupFailure else primary.addSuppressed(cleanupFailure)
                    }
                }
            }
        }
    }

    private companion object { val ATTEMPT_CLEANUP_TIMEOUT = 250.milliseconds }
}
