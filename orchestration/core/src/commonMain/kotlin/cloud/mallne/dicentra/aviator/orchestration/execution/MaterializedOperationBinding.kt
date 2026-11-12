package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.core.execution.AviatorSourceResponse
import cloud.mallne.dicentra.aviator.core.execution.AviatorSourceResponseObserver
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/** An application-owned invocation of one already-materialized Aviator operation. */
class MaterializedConstituent<I>(
    val identity: ConstituentIdentity,
    val invoke: suspend (I) -> ConstituentExecution,
)

/** Decoded or transformed outcome and, separately, its original response when available. */
class ConstituentExecution private constructor(
    val outcome: ConstituentOutcome,
    val response: ResponseSnapshot?,
    private val classifyJson: Boolean,
) {
    constructor(outcome: ConstituentOutcome, response: ResponseSnapshot? = null) : this(outcome, response, false)

    /** Classify nullable decoded JSON against the original source response after the pipeline finishes. */
    companion object {
        fun decodedJson(value: JsonElement?, representation: ResultRepresentation = ResultRepresentation.UNKNOWN): ConstituentExecution = ConstituentExecution(
            if (value == null) ConstituentOutcome.Failed(ExecutionFailure("absent_result", "No decoded result"))
            else ConstituentOutcome.Succeeded(BodyValue.JsonValue(value), representation),
            null,
            true,
        )
    }

    internal fun classifiedOutcome(source: ResponseSnapshot?): ConstituentOutcome {
        if (!classifyJson) return outcome
        val status = source?.http?.status
        if (status != null && status !in 200..299) {
            return ConstituentOutcome.Failed(ExecutionFailure("http_error", "Source response status $status"))
        }
        if (outcome is ConstituentOutcome.Succeeded) return outcome
        return when (source?.body) {
            BodyValue.Empty -> ConstituentOutcome.Succeeded(BodyValue.Empty)
            is BodyValue.BytesValue, is BodyValue.JsonValue -> ConstituentOutcome.Failed(
                ExecutionFailure("decode_failure", "Source response could not be decoded"),
            )
            else -> ConstituentOutcome.Failed(ExecutionFailure("absent_result", "No decoded response available"))
        }
    }
}

/**
 * Client-neutral orchestration of materialized operations. Invocations must use an Aviator client
 * that participates in [cloud.mallne.dicentra.aviator.core.execution.AviatorCallControl] for every
 * actual attempt, including retries; otherwise authorization and call budgets cannot be enforced.
 * The caller supplies aggregation and usability semantics; no aggregate HTTP metadata is created.
 */
class MaterializedOperationBinding<I>(
    constituents: List<MaterializedConstituent<I>>,
    private val isUsable: (BodyValue) -> Boolean,
    private val aggregate: (List<BodyValue>) -> BodyValue,
) : OperationBinding<I> {
    private val candidates = constituents.toList()
    private val owner = Any()

    init {
        require(candidates.map { it.identity }.distinct().size == candidates.size) {
            "Constituent identities must be unique"
        }
    }

    override suspend fun resolve(invocation: CapabilityInvocation): ResolvedOperation {
        val frozen = invocation.copy(constituents = invocation.constituents?.toSet())
        return Prepared(owner, frozen, selectConstituents(frozen, candidates) { it.identity })
    }

    override suspend fun invoke(operation: ResolvedOperation, input: I, scope: InvocationScope): CapabilityResult {
        val prepared = operation as? Prepared<*> ?: error("Invalid resolved operation")
        require(prepared.owner === owner) { "Resolved operation belongs to another binding" }
        @Suppress("UNCHECKED_CAST")
        val selected = prepared.selected as List<MaterializedConstituent<I>>
        val invocation = prepared.invocation
        if (selected.isEmpty()) {
            val outcome = if (invocation.policy.emptySelection == EmptySelectionPolicy.SUCCEED) {
                CapabilityOutcome.Succeeded(BodyValue.Empty)
            } else CapabilityOutcome.Failed(ExecutionFailure("empty_selection", "No matching constituents"))
            return CapabilityResult(invocation.locator, outcome, emptyList())
        }

        val results = mutableListOf<ConstituentResult>()
        val usable = mutableListOf<BodyValue>()
        for (constituent in selected) {
            val sourceObserver = SourceObserver()
            val execution = try {
                withContext(InvocationCallControl(scope, constituent.identity) + sourceObserver) { constituent.invoke(input) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rejected: InvocationRejectedException) {
                ConstituentExecution(ConstituentOutcome.Failed(rejected.failure))
            } catch (_: Exception) {
                ConstituentExecution(ConstituentOutcome.Failed(ExecutionFailure("constituent_failed", "Constituent invocation failed")))
            }
            val source = sourceObserver.response ?: execution.response
            val outcome = execution.classifiedOutcome(source)
            results += ConstituentResult(constituent.identity, outcome, source)
            if (outcome is ConstituentOutcome.Succeeded && isUsable(outcome.value)) usable += outcome.value
            if (invocation.policy.strategy == ExecutionStrategy.FIRST_USABLE && usable.isNotEmpty()) break
        }
        val failed = results.any { it.outcome is ConstituentOutcome.Failed }
        if (usable.isEmpty() || (invocation.policy.strategy == ExecutionStrategy.AGGREGATE && failed &&
                    invocation.policy.partialFailure == PartialFailurePolicy.REQUIRE_ALL)) {
            return CapabilityResult(invocation.locator,
                CapabilityOutcome.Failed(ExecutionFailure("constituents_failed", "No acceptable aggregate result")), results.toList())
        }
        return CapabilityResult(invocation.locator, CapabilityOutcome.Succeeded(aggregate(usable.toList())), results.toList())
    }

    private class Prepared<I>(
        val owner: Any,
        override val invocation: CapabilityInvocation,
        val selected: List<MaterializedConstituent<I>>,
    ) : ResolvedOperation

    private class SourceObserver : AviatorSourceResponseObserver() {
        var response: ResponseSnapshot? = null

        override suspend fun onResponses(responses: List<AviatorSourceResponse>) {
            val source = responses.firstOrNull { it.status in 200..299 } ?: responses.lastOrNull() ?: return
            val body = source.body
            response = ResponseSnapshot(
                body = when {
                    body == null -> BodyValue.Unavailable
                    body.isEmpty() -> BodyValue.Empty
                    else -> BodyValue.BytesValue(body.toList())
                },
                contentType = source.contentType,
                http = HttpResponseMetadata(source.status, source.headers),
            )
        }
    }
}
