package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.core.engine.EngineTemplate
import cloud.mallne.dicentra.aviator.core.plugins.EngineInvocationContext
import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.core.plugins.PluginScopeKey
import cloud.mallne.dicentra.aviator.core.plugins.PluginActivation
import cloud.mallne.dicentra.aviator.core.plugins.TypedPluginScopeProvider
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginInstance
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEventSink
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionRequest
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionResult
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import kotlin.coroutines.cancellation.CancellationException

/** A host-configured orchestrator and the exact workflow it resolves. */
class WorkflowDefinition(
    override val locator: ServiceLocator,
    val workflow: WorkflowIdentity,
    val orchestrator: Orchestrator,
    override val pluginConfiguration: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
) : ExecutableDefinition {
    override val delegation: String get() = OrchestrationEngineTemplate.IDENTITY
}

data class WorkflowInput(
    val inputs: String = "{}",
    val metadata: Map<String, String> = emptyMap(),
    val budget: ExecutionBudget = ExecutionBudget(),
    val events: ExecutionEventSink = ExecutionEventSink.Ignore,
)

/** Retains the workflow's failure and completed-step information for the engine caller. */
class WorkflowInvocationException(val execution: ExecutionResult) :
    Exception("Workflow execution failed")

/**
 * Dispatches through the existing orchestrator so its validation, authorization, budgets,
 * lifecycle hooks and cancellation remain in force.
 */
object OrchestrationEngineTemplate : EngineTemplate<WorkflowInput, ExecutionResult> {
    override val identity: String = IDENTITY

    override suspend fun execute(
        definition: ExecutableDefinition,
        input: WorkflowInput,
        context: EngineInvocationContext,
    ): Result<ExecutionResult> {
        require(definition is WorkflowDefinition) { "Expected a workflow definition" }
        val result = definition.orchestrator.execute(
            ExecutionRequest(definition.workflow, input.inputs, input.metadata),
            input.budget,
            input.events,
            context.scope(OrchestrationPluginScope.Registry),
        )
        return when (result.outcome) {
            is ExecutionOutcome.Succeeded -> Result.success(result)
            is ExecutionOutcome.Failed -> Result.failure(WorkflowInvocationException(result))
            is ExecutionOutcome.Cancelled -> throw CancellationException("Workflow execution was cancelled")
        }
    }

    const val IDENTITY = "orchestrator"
}

object OrchestrationPluginScope {
    val Registry = PluginScopeKey<cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry>(
        "orchestration.registry",
    )

    fun provider(
        installed: List<OrchestrationPluginRegistration>,
        logger: AviatorLogger? = null,
    ) = TypedPluginScopeProvider(Registry) { locator, requested ->
        val active = installed.mapNotNull { registration ->
            val plugin = registration.instance
            if (plugin.serviceFilter.isNotEmpty() && locator !in plugin.serviceFilter) return@mapNotNull null
            when (registration.activation) {
                PluginActivation.PASSIVE -> plugin
                PluginActivation.DEFINITION -> requested[plugin.identity]?.let(registration.reconfigure)
            }
        }
        OrchestrationPluginRegistry(active, logger)
    }
}

/** Definition activation can reconfigure an installed orchestration plugin, never install code. */
data class OrchestrationPluginRegistration(
    val instance: OrchestrationPluginInstance,
    val activation: PluginActivation = PluginActivation.DEFINITION,
    val reconfigure: (kotlinx.serialization.json.JsonElement) -> OrchestrationPluginInstance = { instance },
)
