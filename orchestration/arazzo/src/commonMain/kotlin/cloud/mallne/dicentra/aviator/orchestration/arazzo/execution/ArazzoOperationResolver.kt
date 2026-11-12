package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy

/** Resolves an operationId against the application's source-qualified operation index. */
sealed interface ArazzoOperationResolution {
    data class Resolved(val invocation: CapabilityInvocation) : ArazzoOperationResolution
    data class Failed(val code: String, val message: String) : ArazzoOperationResolution
}

/**
 * Standard Arazzo operation references must resolve uniquely, then restrict the native binding
 * to that exact source-qualified constituent rather than fanning out by locator.
 */
fun resolveArazzoOperation(
    step: ArazzoStep,
    operationIndex: List<ConstituentIdentity>,
    policy: CapabilityPolicy,
): ArazzoOperationResolution {
    val operationId = step.operationId
        ?: return ArazzoOperationResolution.Failed("missing_operation_id", "Arazzo step has no operationId")
    val matches = operationIndex.filter { it.operationId == operationId }
    return when (matches.size) {
        0 -> ArazzoOperationResolution.Failed("operation_not_found", "No source operation matches '$operationId'")
        1 -> ArazzoOperationResolution.Resolved(
            CapabilityInvocation(matches.single().locator, policy, constituents = setOf(matches.single())),
        )
        else -> ArazzoOperationResolution.Failed(
            "ambiguous_operation_id", "Operation '$operationId' matches multiple source-qualified operations",
        )
    }
}
