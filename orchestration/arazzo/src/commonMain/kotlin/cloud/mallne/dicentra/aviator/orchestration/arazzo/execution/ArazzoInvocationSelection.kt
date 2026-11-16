package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.plugin.*

/** Both dispatch paths expose identical hooks and permit only narrowing the resolved target. */
internal suspend fun selectArazzoInvocation(
    original: CapabilityInvocation,
    scope: InvocationScope,
    plugins: OrchestrationPluginRegistry,
): CapabilityInvocation {
    var invocation = original
    val state = scope.pluginState("orchestration")
    for (stage in listOf(OrchestrationStage.RESOLVE, OrchestrationStage.SELECT)) {
        val context = OrchestrationStageContext(
            stage, invocation, scope, state, payload = invocation,
            lifecycleScope = OrchestrationLifecycleScope.STEP,
        )
        for (phase in listOf(OrchestrationHookPhase.BEFORE, OrchestrationHookPhase.AFTER)) {
            plugins.execute(context, phase)
            if (context.shortCircuit != null) {
                throw InvocationRejectedException(ExecutionFailure(
                    "invalid_plugin_short_circuit", "$stage cannot short-circuit with a capability result",
                ))
            }
            context.rejection?.let { throw InvocationRejectedException(it) }
        }
        val narrowed = context.payload as? CapabilityInvocation
        val targets = narrowed?.constituents
        if (narrowed == null || targets == null ||
            narrowed.locator != invocation.locator || narrowed.policy != invocation.policy ||
            invocation.constituents?.containsAll(targets) == false
        ) {
            throw InvocationRejectedException(ExecutionFailure(
                "invalid_plugin_target", "Plugin may not widen or replace an Arazzo target",
            ))
        }
        invocation = narrowed.copy(constituents = targets.toSet())
    }
    if (invocation.constituents.isNullOrEmpty()) {
        throw InvocationRejectedException(ExecutionFailure(
            "plugin_target_rejected", "Plugin excluded the resolved operation",
        ))
    }
    return invocation
}
