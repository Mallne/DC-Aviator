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
    sourceIdsByName: Map<String, String> = emptyMap(),
): ArazzoOperationResolution {
    val reference = step.operationId
        ?: return ArazzoOperationResolution.Failed("missing_operation_id", "Arazzo step has no operationId")
    val parsed = parseSourceQualifiedOperationId(reference)
    if (parsed == null && reference.startsWith("$" + "sourceDescriptions.")) {
        return ArazzoOperationResolution.Failed("invalid_operation_reference", "Arazzo operationId reference is malformed")
    }
    val (operationId, sourceId) = parsed?.let { (sourceName, id) ->
        val resolvedSourceId = sourceIdsByName[sourceName]
            ?: return ArazzoOperationResolution.Failed("source_not_found", "No supplied source matches '$sourceName'")
        id to resolvedSourceId
    } ?: (reference to null)
    val matches = operationIndex.filter { it.operationId == operationId && (sourceId == null || it.sourceId == sourceId) }
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

private fun parseSourceQualifiedOperationId(reference: String): Pair<String, String>? {
    val prefix = "$" + "sourceDescriptions."
    if (!reference.startsWith(prefix)) return null
    val separator = reference.indexOf('.', prefix.length)
    if (separator <= prefix.length || separator == reference.lastIndex) return null
    return reference.substring(prefix.length, separator) to reference.substring(separator + 1)
}
