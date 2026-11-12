package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.core.execution.AviatorSourceResponse
import cloud.mallne.dicentra.aviator.core.execution.AviatorSourceResponseObserver
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStageContext
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginState
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationHookPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.milliseconds

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
    private val plugins: OrchestrationPluginRegistry = OrchestrationPluginRegistry.Empty,
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
        val state = scope.pluginState("orchestration")
        var failure: Throwable? = null
        try {
            val result = invokeStages(operation, input, scope, state)
            val mapped = OrchestrationStageContext(OrchestrationStage.MAP_OUTPUT,
                invocation = (operation as Prepared<*>).invocation, scope = scope, state = state, result = result,
                input = (result.outcome as? CapabilityOutcome.Succeeded)?.aggregate)
            plugins.execute(mapped, OrchestrationHookPhase.BEFORE)
            val mappedResult = applyOutputMapping(result, mapped)
            val afterMapping = OrchestrationStageContext(OrchestrationStage.MAP_OUTPUT,
                invocation = (operation as Prepared<*>).invocation, scope = scope, state = state,
                result = mappedResult, input = (mappedResult.outcome as? CapabilityOutcome.Succeeded)?.aggregate,
                phase = OrchestrationHookPhase.AFTER)
            plugins.execute(afterMapping)
            val completed = applyOutputMapping(mappedResult, afterMapping)
            val terminal = OrchestrationStageContext(OrchestrationStage.COMPLETE,
                invocation = preparedInvocation(operation), scope = scope, state = state, result = completed)
            plugins.execute(terminal)
            val terminalResult = terminal.rejection?.let { completed.copy(outcome = CapabilityOutcome.Failed(it)) }
                ?: terminal.result ?: terminal.shortCircuit ?: completed
            return trustedPluginResult(result, terminalResult)
        } catch (cause: Throwable) {
            failure = cause
            try {
                val invocation = (operation as? Prepared<*>)?.invocation
                if (invocation != null) {
                    val failureHook = suspend {
                        plugins.execute(OrchestrationStageContext(OrchestrationStage.FAILURE,
                            invocation = invocation, scope = scope, state = state, payload = cause))
                    }
                    if (cause is CancellationException) {
                        withContext(NonCancellable) { withTimeoutOrNull(CLEANUP_TIMEOUT) { failureHook() } }
                    } else {
                        failureHook()
                    }
                }
            } catch (pluginFailure: Throwable) {
                cause.addSuppressed(pluginFailure)
            }
            throw cause
        } finally {
            try {
                val invocation = (operation as? Prepared<*>)?.invocation
                if (invocation != null) withContext(NonCancellable) {
                    withTimeoutOrNull(CLEANUP_TIMEOUT) {
                        plugins.execute(OrchestrationStageContext(OrchestrationStage.CLEANUP,
                            invocation = invocation, scope = scope, state = state, payload = failure))
                    }
                }
            } catch (cleanupFailure: Throwable) {
                if (failure == null) throw cleanupFailure
                failure.addSuppressed(cleanupFailure)
            }
        }
    }

    private suspend fun invokeStages(
        operation: ResolvedOperation,
        input: I,
        scope: InvocationScope,
        pluginState: OrchestrationPluginState,
    ): CapabilityResult {
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
            val inputStage = OrchestrationStageContext(OrchestrationStage.MAP_INPUT, invocation, scope,
                pluginState, constituent.identity, input = input)
            plugins.execute(inputStage)
            if (inputStage.shortCircuit != null) {
                results += ConstituentResult(constituent.identity, ConstituentOutcome.Failed(
                    ExecutionFailure("invalid_plugin_short_circuit", "Input mapping cannot short-circuit execution")))
                continue
            }
            inputStage.rejection?.let { rejection ->
                val denied = scope.authorize(constituent.identity)
                results += ConstituentResult(constituent.identity,
                    ConstituentOutcome.Failed(denied ?: rejection))
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val mappedInput = inputStage.input as I
            val cachedStage = OrchestrationStageContext(OrchestrationStage.SCHEDULE, invocation, scope,
                pluginState, constituent.identity, input = mappedInput)
            plugins.execute(cachedStage)
            cachedStage.rejection?.let { rejection ->
                val denied = scope.authorize(constituent.identity)
                results += ConstituentResult(constituent.identity,
                    ConstituentOutcome.Failed(denied ?: rejection))
                continue
            }
            val cached = cachedStage.shortCircuit
            if (cached != null) {
                val denied = scope.authorize(constituent.identity)
                val safeCached = if (denied == null) cached else CapabilityResult(invocation.locator,
                    CapabilityOutcome.Failed(denied), emptyList())
                val cachedOutcome = safeCached.outcome
                if (cachedOutcome is CapabilityOutcome.Succeeded && isUsable(cachedOutcome.aggregate)) {
                    usable += cachedOutcome.aggregate
                    // A plugin-supplied/cached value is not a constituent response. Keep its
                    // aggregate value but never invent operation provenance or HTTP metadata.
                } else if (cachedOutcome is CapabilityOutcome.Failed) {
                    results += ConstituentResult(constituent.identity,
                        ConstituentOutcome.Failed(cachedOutcome.failure))
                }
                if (invocation.policy.strategy == ExecutionStrategy.FIRST_USABLE && usable.isNotEmpty()) break
                continue
            }
            val sourceObserver = SourceObserver()
            val execution = try {
                withContext(InvocationCallControl(scope, constituent.identity, invocation, plugins, pluginState) + sourceObserver) {
                    constituent.invoke(mappedInput)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rejected: InvocationRejectedException) {
                ConstituentExecution(ConstituentOutcome.Failed(rejected.failure))
            } catch (_: Exception) {
                ConstituentExecution(ConstituentOutcome.Failed(ExecutionFailure("constituent_failed", "Constituent invocation failed")))
            }
            val source = sourceObserver.response ?: execution.response
            val outcome = execution.classifiedOutcome(source)
            val initialResult = CapabilityResult(invocation.locator,
                if (outcome is ConstituentOutcome.Succeeded) CapabilityOutcome.Succeeded(outcome.value)
                else CapabilityOutcome.Failed((outcome as ConstituentOutcome.Failed).failure),
                listOf(ConstituentResult(constituent.identity, outcome, source)))
            val afterAttempt = OrchestrationStageContext(OrchestrationStage.HANDLE_RESULT, invocation, scope,
                pluginState, constituent.identity, input = mappedInput, result = initialResult)
            plugins.execute(afterAttempt)
            val handled = afterAttempt.rejection?.let {
                initialResult.copy(outcome = CapabilityOutcome.Failed(it))
            } ?: afterAttempt.result ?: afterAttempt.shortCircuit ?: initialResult
            val originalConstituent = initialResult.constituents.single()
            val handledConstituent = when {
                originalConstituent.outcome is ConstituentOutcome.Failed -> originalConstituent
                handled.outcome is CapabilityOutcome.Failed -> originalConstituent.copy(
                    outcome = ConstituentOutcome.Failed(handled.outcome.failure),
                )
                handled.outcome is CapabilityOutcome.Succeeded &&
                    handled.outcome.aggregate != (originalConstituent.outcome as ConstituentOutcome.Succeeded).value ->
                    originalConstituent.copy(outcome = ConstituentOutcome.Succeeded(
                        handled.outcome.aggregate, ResultRepresentation.TRANSFORMED,
                    ))
                else -> originalConstituent
            }
            results += handledConstituent
            if (handled.outcome is CapabilityOutcome.Succeeded && isUsable(handled.outcome.aggregate)) usable += handled.outcome.aggregate
            if (invocation.policy.strategy == ExecutionStrategy.FIRST_USABLE && usable.isNotEmpty()) break
        }
        val failed = results.any { it.outcome is ConstituentOutcome.Failed }
        if (usable.isEmpty() || (invocation.policy.strategy == ExecutionStrategy.AGGREGATE && failed &&
                    invocation.policy.partialFailure == PartialFailurePolicy.REQUIRE_ALL)) {
            return CapabilityResult(invocation.locator,
                CapabilityOutcome.Failed(ExecutionFailure("constituents_failed", "No acceptable aggregate result")), results.toList())
        }
        val aggregate = aggregate(usable.toList())
        val aggregateStage = OrchestrationStageContext(OrchestrationStage.AGGREGATE, invocation, scope,
            pluginState, input = aggregate)
        plugins.execute(aggregateStage)
        aggregateStage.rejection?.let { rejection ->
            return CapabilityResult(invocation.locator, CapabilityOutcome.Failed(rejection), results.toList())
        }
        if (aggregateStage.shortCircuit != null) {
            return CapabilityResult(invocation.locator,
                CapabilityOutcome.Failed(ExecutionFailure("invalid_plugin_short_circuit", "Aggregation cannot short-circuit execution")),
                results.toList())
        }
        if (aggregateStage.input != aggregate && aggregateStage.input !is BodyValue) {
            return CapabilityResult(invocation.locator,
                CapabilityOutcome.Failed(ExecutionFailure("invalid_plugin_stage_data", "Aggregation plugin returned an incompatible value")),
                results.toList())
        }
        val transformedAggregate = (aggregateStage.input as? BodyValue) ?: aggregate
        return CapabilityResult(invocation.locator, CapabilityOutcome.Succeeded(transformedAggregate), results.toList())
    }

    /** Plugins may derive values, but cannot rewrite the execution target or source evidence. */
    private fun trustedPluginResult(original: CapabilityResult, proposed: CapabilityResult): CapabilityResult {
        if (original.outcome is CapabilityOutcome.Failed) return original
        val outcome = when (val changed = proposed.outcome) {
            is CapabilityOutcome.Failed -> changed
            is CapabilityOutcome.Succeeded -> CapabilityOutcome.Succeeded(changed.aggregate)
        }
        return CapabilityResult(original.locator, outcome, original.constituents)
    }

    private fun applyOutputMapping(original: CapabilityResult, context: OrchestrationStageContext): CapabilityResult {
        val originalOutput = (original.outcome as? CapabilityOutcome.Succeeded)?.aggregate
        val invalidOutput = context.input != originalOutput && context.input !is BodyValue
        val failure = context.rejection ?: when {
            invalidOutput -> ExecutionFailure("invalid_plugin_stage_data", "Output mapping plugin returned an incompatible value")
            context.result == null -> ExecutionFailure("invalid_plugin_stage_data", "Output mapping plugin removed the capability result")
            context.shortCircuit != null -> ExecutionFailure("invalid_plugin_short_circuit", "Output mapping cannot short-circuit execution")
            else -> null
        }
        if (failure != null) return original.copy(outcome = CapabilityOutcome.Failed(failure))
        return when {
            context.result != original -> context.result!!
            context.input != originalOutput -> original.copy(outcome = CapabilityOutcome.Succeeded(context.input as BodyValue))
            else -> original
        }
    }

    private fun preparedInvocation(operation: ResolvedOperation) = (operation as Prepared<*>).invocation

    private companion object { val CLEANUP_TIMEOUT = 250.milliseconds }

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
