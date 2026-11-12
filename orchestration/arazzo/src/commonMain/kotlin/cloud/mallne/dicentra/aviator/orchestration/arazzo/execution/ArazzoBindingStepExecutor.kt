package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.OperationBinding
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStageContext
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationHookPhase
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Client-neutral Arazzo adapter for a native Aviator operation binding. A concrete operationId
 * must resolve uniquely; the exact constituent is restricted before invocation. The control is
 * installed around the binding call so any Aviator client transport in that call inherits the
 * same per-run authorization, actual-call, retry, concurrency and response-byte accounting.
 */
class ArazzoBindingStepExecutor(
    private val binding: OperationBinding<JsonElement>,
    private val operationIndex: List<ConstituentIdentity>,
    private val policy: CapabilityPolicy,
    private val sourceIdsByName: Map<String, String> = emptyMap(),
    private val json: Json = Json,
    private val plugins: OrchestrationPluginRegistry = OrchestrationPluginRegistry.Empty,
) : ArazzoStepExecutor, ArazzoWorkflowPreflight {
    override suspend fun preflight(
        workflow: cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow,
        scope: InvocationScope,
    ): ExecutionFailure? {
        for (step in workflow.steps) {
            when (val resolution = resolveArazzoOperation(step, operationIndex, policy, sourceIdsByName)) {
                is ArazzoOperationResolution.Failed -> return ExecutionFailure(
                    resolution.code, resolution.message, workflowId = workflow.workflowId, stepId = step.stepId,
                )
                is ArazzoOperationResolution.Resolved -> {
                    val identity = checkNotNull(resolution.invocation.constituents).single()
                    scope.authorize(identity)?.let { return it.copy(workflowId = workflow.workflowId, stepId = step.stepId) }
                }
            }
        }
        return null
    }

    override suspend fun execute(step: ArazzoStep, inputs: String, scope: InvocationScope): ArazzoStepExecution {
        val resolution = resolveArazzoOperation(step, operationIndex, policy, sourceIdsByName)
        if (resolution is ArazzoOperationResolution.Failed) {
            return ArazzoStepExecution.Failed(ExecutionFailure(resolution.code, resolution.message))
        }
        resolution as ArazzoOperationResolution.Resolved
        var invocation = resolution.invocation
        val pluginState = scope.pluginState("orchestration")
        val resolvedStage = OrchestrationStageContext(OrchestrationStage.RESOLVE, invocation, scope,
            pluginState, payload = invocation, lifecycleScope = OrchestrationLifecycleScope.STEP)
        plugins.execute(resolvedStage)
        if (resolvedStage.shortCircuit != null) {
            return ArazzoStepExecution.Failed(ExecutionFailure("invalid_plugin_short_circuit", "Resolution cannot short-circuit with a capability result"))
        }
        resolvedStage.rejection?.let { return ArazzoStepExecution.Failed(it) }
        plugins.execute(resolvedStage, OrchestrationHookPhase.AFTER)
        if (resolvedStage.shortCircuit != null) {
            return ArazzoStepExecution.Failed(ExecutionFailure("invalid_plugin_short_circuit", "Resolution cannot short-circuit with a capability result"))
        }
        resolvedStage.rejection?.let { return ArazzoStepExecution.Failed(it) }
        invocation = narrowedInvocation(invocation, resolvedStage.payload)
            ?: return ArazzoStepExecution.Failed(ExecutionFailure("invalid_plugin_target", "Plugin may not widen or replace an Arazzo target"))
        val selectionStage = OrchestrationStageContext(OrchestrationStage.SELECT, invocation, scope,
            pluginState, payload = invocation, lifecycleScope = OrchestrationLifecycleScope.STEP)
        plugins.execute(selectionStage)
        if (selectionStage.shortCircuit != null) {
            return ArazzoStepExecution.Failed(ExecutionFailure("invalid_plugin_short_circuit", "Selection cannot short-circuit with a capability result"))
        }
        selectionStage.rejection?.let { return ArazzoStepExecution.Failed(it) }
        plugins.execute(selectionStage, OrchestrationHookPhase.AFTER)
        if (selectionStage.shortCircuit != null) {
            return ArazzoStepExecution.Failed(ExecutionFailure("invalid_plugin_short_circuit", "Selection cannot short-circuit with a capability result"))
        }
        selectionStage.rejection?.let { return ArazzoStepExecution.Failed(it) }
        invocation = narrowedInvocation(invocation, selectionStage.payload)
            ?: return ArazzoStepExecution.Failed(ExecutionFailure("invalid_plugin_target", "Plugin may not widen or replace an Arazzo target"))
        if (invocation.constituents.isNullOrEmpty()) {
            return ArazzoStepExecution.Failed(ExecutionFailure("plugin_target_rejected", "Plugin excluded the resolved operation"))
        }
        val identity = checkNotNull(invocation.constituents).single()
        val input = try {
            json.parseToJsonElement(inputs)
        } catch (cause: IllegalArgumentException) {
            return ArazzoStepExecution.Failed(ExecutionFailure("invalid_inputs", "Arazzo run inputs are not valid JSON"))
        }
        return try {
            val prepared = binding.resolve(invocation)
            ArazzoStepExecution.Completed(
                withContext(InvocationCallControl(scope, identity, invocation, plugins, pluginState)) {
                    binding.invoke(prepared, input, scope)
                },
            )
        } catch (rejected: InvocationRejectedException) {
            ArazzoStepExecution.Failed(rejected.failure)
        }
    }

    private fun narrowedInvocation(original: CapabilityInvocation, candidate: Any?): CapabilityInvocation? {
        val changed = candidate as? CapabilityInvocation ?: return null
        if (changed.locator != original.locator || changed.policy != original.policy) return null
        val originalTargets = original.constituents
        val changedTargets = changed.constituents ?: return null
        if (originalTargets != null && !originalTargets.containsAll(changedTargets)) return null
        return changed.copy(constituents = changedTargets.toSet())
    }
}
