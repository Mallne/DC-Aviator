package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.core.engine.AviatorEngine
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/** The delegation owns how a workflow step resolves its locator and client input. */
data class ArazzoEngineAction(
    val locator: ServiceLocator,
    val identity: ConstituentIdentity,
    private val dispatch: suspend (AviatorEngine) -> CapabilityResult?,
) {
    suspend fun execute(engine: AviatorEngine): CapabilityResult? = dispatch(engine)
}

sealed interface ArazzoEngineActionResolution {
    data class Resolved(val action: ArazzoEngineAction) : ArazzoEngineActionResolution
    data class Failed(val code: String, val message: String) : ArazzoEngineActionResolution
}

/**
 * Runs Arazzo actions through the application engine rather than a locator binding.
 * The mapper is delegation/client-specific because the engine deliberately keeps results opaque.
 */
class ArazzoEngineStepExecutor(
    private val engine: AviatorEngine,
    private val policy: CapabilityPolicy,
    private val resolveAction: suspend (ArazzoStep, JsonElement) -> ArazzoEngineActionResolution,
) : ArazzoStepExecutor {
    override suspend fun execute(step: ArazzoStep, inputs: String, scope: InvocationScope): ArazzoStepExecution {
        val parsed = try {
            kotlinx.serialization.json.Json.parseToJsonElement(inputs)
        } catch (_: IllegalArgumentException) {
            return ArazzoStepExecution.Failed(ExecutionFailure("invalid_inputs", "Arazzo step inputs are not valid JSON"))
        }
        val action = when (val resolution = resolveAction(step, parsed)) {
            is ArazzoEngineActionResolution.Resolved -> resolution.action
            is ArazzoEngineActionResolution.Failed ->
                return ArazzoStepExecution.Failed(ExecutionFailure(resolution.code, resolution.message))
        }
        return try {
            val plugins = scope.plugins ?: OrchestrationPluginRegistry.Empty
            val capability = selectArazzoInvocation(
                cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation(
                    action.locator, policy, constituents = setOf(action.identity),
                ),
                scope,
                plugins,
            )
            scope.authorize(action.identity)?.let { return ArazzoStepExecution.Failed(it) }
            val result = withContext(InvocationCallControl(
                scope, action.identity, capability, plugins, scope.pluginState("orchestration"),
            )) {
                action.execute(engine)
            }
            if (result != null) {
                ArazzoStepExecution.Completed(result)
            } else {
                ArazzoStepExecution.Failed(
                    ExecutionFailure("operation_failed", "Arazzo action failed"),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (rejected: InvocationRejectedException) {
            ArazzoStepExecution.Failed(rejected.failure)
        } catch (_: Exception) {
            ArazzoStepExecution.Failed(ExecutionFailure("operation_failed", "Arazzo action failed"))
        }
    }
}
