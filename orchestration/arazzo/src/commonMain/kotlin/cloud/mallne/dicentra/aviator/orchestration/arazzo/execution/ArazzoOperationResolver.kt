package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import cloud.mallne.dicentra.aviator.core.engine.AviatorEngine
import cloud.mallne.dicentra.aviator.core.engine.EngineTemplate
import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.core.engine.InvocationMode
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import kotlinx.serialization.json.JsonElement

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

/**
 * Adapts the standard operationId resolver to engine dispatch. The input mapper remains caller
 * supplied because Arazzo parameter/request-body interpretation is an application concern.
 * [matchesDefinition] must match the complete source-qualified identity, not just its locator
 * or operationId. The host owns this association because core definitions keep provenance opaque.
 */
suspend fun <I : Any, O : Any> resolveArazzoEngineAction(
    step: ArazzoStep,
    inputs: JsonElement,
    operationIndex: List<ConstituentIdentity>,
    policy: CapabilityPolicy,
    sourceIdsByName: Map<String, String> = emptyMap(),
    template: EngineTemplate<I, O>,
    matchesDefinition: (ConstituentIdentity, ExecutableDefinition) -> Boolean,
    inputFor: suspend (ArazzoStep, JsonElement, ConstituentIdentity) -> I,
    mapResult: (O) -> cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult,
): ArazzoEngineActionResolution = when (
    val resolution = resolveArazzoOperation(step, operationIndex, policy, sourceIdsByName)
) {
    is ArazzoOperationResolution.Failed ->
        ArazzoEngineActionResolution.Failed(resolution.code, resolution.message)
    is ArazzoOperationResolution.Resolved -> {
        val identity = checkNotNull(resolution.invocation.constituents).single()
        val input = inputFor(step, inputs, identity)
        ArazzoEngineActionResolution.Resolved(
            ArazzoEngineAction(
                locator = resolution.invocation.locator,
                identity = identity,
            ) { engine ->
                // A received response belongs to the workflow's success criteria, not the
                // client's default HTTP/decode classification. Actual execution failures
                // still fail and retain normal engine fallback and authorization semantics.
                val responseTemplate = object : EngineTemplate<I, O> by template {
                    override fun classifyFailure(output: O): Exception? = null
                }
                val outcomes = engine.invoke(
                    resolution.invocation.locator,
                    responseTemplate,
                    input,
                    InvocationMode.FIRST_NOT_FAILING,
                    selectDefinition = { definition -> matchesDefinition(identity, definition) },
                )
                val success = outcomes.firstOrNull { it.result.isSuccess }
                if (success != null) {
                    mapResult(success.result.getOrThrow())
                } else {
                    outcomes.firstNotNullOfOrNull {
                        it.result.exceptionOrNull() as? InvocationRejectedException
                    }?.let { throw it }
                    null
                }
            },
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
