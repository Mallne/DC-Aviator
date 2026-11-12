package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoCriterion
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEvent
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEventKind
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEventOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEventSink
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionResult
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.ResponseSnapshot
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.redacting
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginState
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationHookPhase
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStageContext
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/** Narrow sequential runner seam; operation resolution and Aviator binding remain application-owned. */
fun interface ArazzoStepExecutor {
    suspend fun execute(step: ArazzoStep, inputs: String, scope: InvocationScope): ArazzoStepExecution
}

/** Optional all-step preflight that must complete before the first operation is invoked. */
fun interface ArazzoWorkflowPreflight {
    suspend fun preflight(workflow: ArazzoWorkflow, scope: InvocationScope): ExecutionFailure?
}

/** A step may fail before a locator/constituent result exists (for example, ambiguous operationId). */
sealed interface ArazzoStepExecution {
    data class Completed(val result: CapabilityResult) : ArazzoStepExecution
    data class Failed(val failure: ExecutionFailure) : ArazzoStepExecution
}

/** Runs validated, ordered operationId steps with one shared, per-run budget scope. */
class ArazzoRunExecutor(
    private val stepExecutor: ArazzoStepExecutor,
    private val now: () -> Instant = { Clock.System.now() },
    private val plugins: OrchestrationPluginRegistry = OrchestrationPluginRegistry.Empty,
) {
    suspend fun execute(
        workflow: ArazzoWorkflow,
        identity: WorkflowIdentity,
        runId: RunId,
        inputs: String = "{}",
        metadata: Map<String, String> = emptyMap(),
        budget: ExecutionBudget = ExecutionBudget(),
        events: ExecutionEventSink = ExecutionEventSink.Ignore,
    ): ExecutionResult {
        val location = workflow.documentLocation
        val locationMetadata = buildMap {
            location?.retrievalUri?.let { put("arazzo.document.retrievalUri", it) }
            location?.baseUri?.let { put("arazzo.document.baseUri", it) }
            location?.selfUri?.let { put("arazzo.document.selfUri", it) }
        }
        val runMetadata = ExecutionMetadata(runId, identity, values = metadata + locationMetadata)
        val runScope = EnforcedInvocationScope(runMetadata, budget, now = now,
            policyPlugins = plugins.invocationPolicyPlugins())
        val pluginState = runScope.pluginState("orchestration")
        val safeEvents = PluginMetadataEventSink(events.redacting(), plugins, runScope, pluginState)
        val completed = mutableListOf<String>()
        var declaredOutputs = JsonObject(emptyMap())
        var activeStep: ExecutionMetadata? = null
        var terminalFailure = false
        var primaryException: Throwable? = null
        var cleanupStarted = false
        suspend fun notifyFailure(failure: ExecutionFailure, stepId: String? = null) {
            plugins.execute(OrchestrationStageContext(OrchestrationStage.FAILURE,
                scope = stepId?.let(runScope::forStep) ?: runScope, state = pluginState,
                payload = failure.copy(workflowId = failure.workflowId ?: identity.documentId, stepId = failure.stepId ?: stepId),
                lifecycleScope = if (stepId == null) OrchestrationLifecycleScope.RUN else OrchestrationLifecycleScope.STEP))
        }
        suspend fun cleanup() {
            if (cleanupStarted) return
            cleanupStarted = true
            plugins.execute(OrchestrationStageContext(OrchestrationStage.CLEANUP,
                scope = runScope, state = pluginState, payload = terminalFailure,
                lifecycleScope = OrchestrationLifecycleScope.RUN))
        }
        try {
            safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_STARTED))
            val preparation = prepareArazzoWorkflow(workflow, inputs, runScope, plugins, pluginState)
            if (preparation is ArazzoPreparation.Failed) {
                val failure = preparation.failure
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            preparation as ArazzoPreparation.Ready
            val executableWorkflow = preparation.workflow
            val executableInputs = preparation.inputs
            val outputMapper = ArazzoOutputMapper(executableInputs)
            if (!outputMapper.hasValidInputs) {
                val failure = ExecutionFailure("invalid_inputs", "Arazzo run inputs are not valid JSON", executableWorkflow.workflowId)
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            (stepExecutor as? ArazzoWorkflowPreflight)?.preflight(executableWorkflow, runScope)?.let { failure ->
                terminalFailure = true
                notifyFailure(failure.copy(workflowId = failure.workflowId ?: executableWorkflow.workflowId), failure.stepId)
                safeEvents.emit(ExecutionEvent(
                    runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED,
                ))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            val outputMappingExecutor = ArazzoOutputMappingStepExecutor(stepExecutor, outputMapper)

            for (step in executableWorkflow.steps) {
                val failure = executeArazzoWorkflowStep(
                    step = step,
                    workflowId = executableWorkflow.workflowId,
                    inputs = executableInputs,
                    executor = outputMappingExecutor,
                    runMetadata = runMetadata,
                    runScope = runScope,
                    pluginState = pluginState,
                    plugins = plugins,
                    events = safeEvents,
                    completed = completed,
                    now = now,
                    setActiveStep = { activeStep = it },
                    notifyFailure = ::notifyFailure,
                )
                if (failure != null) {
                    terminalFailure = true
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
            }
            declaredOutputs = outputMapper.mapWorkflow(executableWorkflow) ?: run {
                val failure = ExecutionFailure(
                    "expression_evaluation",
                    "Arazzo workflow output expression could not be evaluated",
                    workflowId = executableWorkflow.workflowId,
                )
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
            }
            safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.SUCCEEDED))
        } catch (cancelled: CancellationException) {
            primaryException = cancelled
            terminalFailure = true
            try {
                withContext(NonCancellable) {
                    withTimeoutOrNull(CANCELLATION_EVENT_TIMEOUT) {
                        notifyFailure(ExecutionFailure("cancelled", "Execution was cancelled"), activeStep?.stepId)
                    }
                }
            } catch (pluginFailure: Throwable) {
                cancelled.addSuppressed(pluginFailure)
            }
            try {
                withContext(NonCancellable) {
                    // Cancellation notifications are best effort and bounded: a stalled observer must
                    // not pin the caller or turn cancellation into detached background work.
                    withTimeoutOrNull(CANCELLATION_EVENT_TIMEOUT) {
                        activeStep?.let { safeEvents.emit(ExecutionEvent(it, ExecutionEventKind.STEP_FINISHED)) }
                        safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.CANCELLED))
                    }
                }
            } catch (observerFailure: Throwable) {
                cancelled.addSuppressed(observerFailure)
            }
            throw cancelled
        } catch (cause: Throwable) {
            primaryException = cause
            terminalFailure = true
            try {
                withContext(NonCancellable) {
                    withTimeoutOrNull(CANCELLATION_EVENT_TIMEOUT) {
                        notifyFailure(
                            ExecutionFailure(
                                "execution_exception",
                                "Orchestration execution failed",
                                workflowId = identity.documentId,
                                stepId = activeStep?.stepId,
                            ),
                            activeStep?.stepId,
                        )
                    }
                }
            } catch (pluginFailure: Throwable) {
                cause.addSuppressed(pluginFailure)
            }
            throw cause
        } finally {
            if (terminalFailure || primaryException != null) {
                try {
                    withContext(NonCancellable) { withTimeoutOrNull(CANCELLATION_EVENT_TIMEOUT) { cleanup() } }
                } catch (cleanupFailure: Throwable) {
                    primaryException?.addSuppressed(cleanupFailure)
                }
            } else {
                cleanup()
            }
        }

        return ExecutionResult(runId, identity, ExecutionOutcome.Succeeded(declaredOutputs.toString()))
    }

    private companion object {
        val CANCELLATION_EVENT_TIMEOUT: Duration = 250.milliseconds
    }
}

private sealed interface ArazzoPreparation {
    data class Ready(val workflow: ArazzoWorkflow, val inputs: String) : ArazzoPreparation
    data class Failed(val failure: ExecutionFailure) : ArazzoPreparation
}

private suspend fun prepareArazzoWorkflow(
    workflow: ArazzoWorkflow,
    inputs: String,
    scope: InvocationScope,
    plugins: OrchestrationPluginRegistry,
    state: OrchestrationPluginState,
): ArazzoPreparation {
    val preparation = OrchestrationStageContext(
        OrchestrationStage.PREPARE, scope = scope, state = state, payload = workflow, input = inputs,
        lifecycleScope = OrchestrationLifecycleScope.RUN,
    )
    plugins.execute(preparation, OrchestrationHookPhase.BEFORE)
    plugins.execute(preparation, OrchestrationHookPhase.AFTER)
    if (preparation.shortCircuit != null) {
        return ArazzoPreparation.Failed(ExecutionFailure("invalid_plugin_short_circuit", "Run preparation cannot short-circuit execution"))
    }
    preparation.rejection?.let { return ArazzoPreparation.Failed(it.copy(workflowId = workflow.workflowId)) }
    val preparedWorkflow = preparation.payload as? ArazzoWorkflow
        ?: return ArazzoPreparation.Failed(ExecutionFailure("invalid_plugin_stage_data", "Preparation plugin returned incompatible stage data"))
    val preparedInputs = preparation.input as? String
        ?: return ArazzoPreparation.Failed(ExecutionFailure("invalid_plugin_stage_data", "Preparation plugin returned incompatible stage data"))

    val validation = OrchestrationStageContext(
        OrchestrationStage.VALIDATE, scope = scope, state = state, payload = preparedWorkflow, input = preparedInputs,
        lifecycleScope = OrchestrationLifecycleScope.RUN,
    )
    plugins.execute(validation, OrchestrationHookPhase.BEFORE)
    plugins.execute(validation, OrchestrationHookPhase.AFTER)
    if (validation.shortCircuit != null) {
        return ArazzoPreparation.Failed(ExecutionFailure("invalid_plugin_short_circuit", "Validation cannot short-circuit execution"))
    }
    validation.rejection?.let { return ArazzoPreparation.Failed(it.copy(workflowId = preparedWorkflow.workflowId)) }
    val executableWorkflow = validation.payload as? ArazzoWorkflow
        ?: return ArazzoPreparation.Failed(ExecutionFailure("invalid_plugin_stage_data", "Validation plugin returned incompatible stage data"))
    val executableInputs = validation.input as? String
        ?: return ArazzoPreparation.Failed(ExecutionFailure("invalid_plugin_stage_data", "Validation plugin returned incompatible stage data"))

    val unsupportedStep = executableWorkflow.steps.firstOrNull {
        it.operationId == null || it.operationPath != null || it.workflowId != null ||
            it.dependsOn.isNotEmpty() || it.successCriteria?.let { criteria ->
                criteria.isEmpty() || criteria.any { criterion ->
                    (criterion.type != null && criterion.type != "simple") || criterion.context != null
                }
            } == true
    }
    if (unsupportedStep != null || executableWorkflow.inputs != null || executableWorkflow.dependsOn.isNotEmpty()) {
        return ArazzoPreparation.Failed(ExecutionFailure(
            code = "unsupported_step",
            message = "Workflow contains executable features outside the supported sequential operationId profile",
            workflowId = executableWorkflow.workflowId,
            stepId = unsupportedStep?.stepId,
        ))
    }
    return ArazzoPreparation.Ready(executableWorkflow, executableInputs)
}

private suspend fun executeArazzoWorkflowStep(
    step: ArazzoStep,
    workflowId: String,
    inputs: String,
    executor: ArazzoStepExecutor,
    runMetadata: ExecutionMetadata,
    runScope: EnforcedInvocationScope,
    pluginState: OrchestrationPluginState,
    plugins: OrchestrationPluginRegistry,
    events: ExecutionEventSink,
    completed: MutableList<String>,
    now: () -> Instant,
    setActiveStep: (ExecutionMetadata?) -> Unit,
    notifyFailure: suspend (ExecutionFailure, String?) -> Unit,
): ExecutionFailure? {
    val stepMetadata = runMetadata.copy(stepId = step.stepId)
    val stepScope = runScope.forStep(step.stepId)
    val budgetFailure = stepScope.checkpoint(transitions = 1)
    if (budgetFailure != null) {
        val failure = budgetFailure.copy(workflowId = workflowId, stepId = step.stepId)
        notifyFailure(budgetFailure, step.stepId)
        events.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
        return failure
    }

    setActiveStep(stepMetadata)
    events.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_STARTED))
    suspend fun rejectStep(failure: ExecutionFailure, finishRunWithStepMetadata: Boolean = true): ExecutionFailure {
        notifyFailure(failure, step.stepId)
        events.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
        setActiveStep(null)
        val metadata = if (finishRunWithStepMetadata) stepMetadata else runMetadata
        events.emit(ExecutionEvent(metadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
        return failure
    }

    val scheduling = OrchestrationStageContext(
        OrchestrationStage.SCHEDULE, scope = stepScope, state = pluginState, payload = step, input = inputs,
        lifecycleScope = OrchestrationLifecycleScope.STEP,
    )
    plugins.execute(scheduling, OrchestrationHookPhase.BEFORE)
    plugins.execute(scheduling, OrchestrationHookPhase.AFTER)
    if (scheduling.shortCircuit != null) {
        return rejectStep(ExecutionFailure("invalid_plugin_short_circuit", "Arazzo scheduling cannot return a capability result"))
    }
    scheduling.rejection?.let { return rejectStep(it.copy(workflowId = workflowId, stepId = step.stepId)) }
    val scheduledStep = scheduling.payload as? ArazzoStep
    val scheduledInputs = scheduling.input as? String
    if (scheduledStep == null || scheduledInputs == null) {
        return rejectStep(ExecutionFailure("invalid_plugin_stage_data", "Scheduling plugin returned incompatible stage data",
            workflowId = workflowId, stepId = step.stepId))
    }

    val mapping = OrchestrationStageContext(
        OrchestrationStage.MAP_INPUT, scope = stepScope, state = pluginState, payload = scheduledStep,
        input = scheduledInputs, lifecycleScope = OrchestrationLifecycleScope.STEP,
    )
    plugins.execute(mapping, OrchestrationHookPhase.BEFORE)
    plugins.execute(mapping, OrchestrationHookPhase.AFTER)
    if (mapping.shortCircuit != null) {
        return rejectStep(ExecutionFailure("invalid_plugin_short_circuit", "Input mapping cannot short-circuit Arazzo"))
    }
    mapping.rejection?.let {
        return rejectStep(it.copy(workflowId = workflowId, stepId = step.stepId), finishRunWithStepMetadata = false)
    }
    val mappedStep = mapping.payload as? ArazzoStep
    val mappedInputs = mapping.input as? String
    if (mappedStep == null || mappedInputs == null) {
        return rejectStep(ExecutionFailure("invalid_plugin_stage_data", "Input mapping plugin returned incompatible stage data",
            workflowId = workflowId, stepId = step.stepId))
    }

    val deadline = stepScope.enforcedDeadline?.let { (it - now()).coerceAtLeast(Duration.ZERO) }
    val execution = executeArazzoStep(executor, mappedStep, mappedInputs, stepScope, events, deadline)
    val stepFailure = when (execution) {
        is ArazzoStepExecution.Failed -> execution.failure
        is ArazzoStepExecution.Completed -> (execution.result.outcome as? CapabilityOutcome.Failed)?.failure
    }
    val result = (execution as? ArazzoStepExecution.Completed)?.result
    if (stepFailure != null) {
        val failure = stepFailure.copy(workflowId = workflowId, stepId = step.stepId)
        events.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
        setActiveStep(null)
        notifyFailure(failure, step.stepId)
        events.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
        return failure
    }

    completed += step.stepId
    val completion = OrchestrationStageContext(
        OrchestrationStage.COMPLETE, scope = stepScope, state = pluginState, payload = step, result = result,
        lifecycleScope = OrchestrationLifecycleScope.STEP,
    )
    plugins.execute(completion, OrchestrationHookPhase.BEFORE)
    plugins.execute(completion, OrchestrationHookPhase.AFTER)
    if (completion.shortCircuit != null) {
        return rejectStep(ExecutionFailure("invalid_plugin_short_circuit", "Arazzo completion cannot replace the workflow result"))
    }
    completion.rejection?.let { return rejectStep(it.copy(workflowId = workflowId, stepId = step.stepId), false) }
    events.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
    setActiveStep(null)
    return null
}

private suspend fun executeArazzoStep(
    executor: ArazzoStepExecutor,
    step: ArazzoStep,
    inputs: String,
    scope: InvocationScope,
    events: ExecutionEventSink,
    deadline: Duration?,
): ArazzoStepExecution = try {
    val invocationScope = EventingInvocationScope(scope, events)
    if (deadline == null) executor.execute(step, inputs, invocationScope)
    else withTimeoutOrNull(deadline) { executor.execute(step, inputs, invocationScope) }
        ?: ArazzoStepExecution.Failed(ExecutionFailure("deadline", "Execution budget exhausted: deadline"))
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    currentCoroutineContext().ensureActive()
    ArazzoStepExecution.Failed(ExecutionFailure("operation_failed", "Arazzo step execution failed"))
}

private class ArazzoOutputMappingStepExecutor(
    private val delegate: ArazzoStepExecutor,
    private val outputMapper: ArazzoOutputMapper,
) : ArazzoStepExecutor {
    override suspend fun execute(step: ArazzoStep, inputs: String, scope: InvocationScope): ArazzoStepExecution {
        val mappedInputs = outputMapper.mapStepInputs(step, inputs)
            ?: return ArazzoStepExecution.Failed(ExecutionFailure(
                "input_mapping",
                "Arazzo step input expression could not be evaluated",
                stepId = step.stepId,
            ))
        val execution = delegate.execute(step, mappedInputs, scope)
        val result = (execution as? ArazzoStepExecution.Completed)?.result ?: return execution
        if (result.outcome is CapabilityOutcome.Failed && result.constituents.singleOrNull()?.response == null) return execution
        outputMapper.criteriaFailure(step, result)?.let { return ArazzoStepExecution.Failed(it) }
        if (result.outcome is CapabilityOutcome.Failed) return execution
        if (outputMapper.mapStep(step, result) != null) return execution
        return ArazzoStepExecution.Failed(ExecutionFailure(
            "expression_evaluation",
            "Arazzo step output expression could not be evaluated",
            stepId = step.stepId,
        ))
    }
}

private class ArazzoOutputMapper(inputs: String) {
    private val parsedInputs = try {
        Json.parseToJsonElement(inputs)
    } catch (_: IllegalArgumentException) {
        null
    }
    private val outputs = linkedMapOf<String, JsonObject>()
    private val responses = linkedMapOf<String, ResponseSnapshot>()

    val hasValidInputs: Boolean get() = parsedInputs != null

    fun mapStepInputs(step: ArazzoStep, inputs: String): String? {
        if (step.parameters.isEmpty() && step.requestBody == null) return inputs
        val runInputs = try {
            Json.parseToJsonElement(inputs)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val parameterValues = linkedMapOf<String, MutableMap<String, JsonElement>>()
        val parameterNames = linkedMapOf<String, MutableSet<String>>()
        return try {
            step.parameters.forEach { parameter ->
                val location = requireNotNull(parameter.`in`) { "Parameter location is required" }
                val values = parameterValues.getOrPut(location) { linkedMapOf() }
                val normalizedName = if (location == "header") parameter.name.lowercase() else parameter.name
                val names = parameterNames.getOrPut(location) { linkedSetOf() }
                require(names.add(normalizedName)) { "Duplicate parameter target" }
                values[parameter.name] = mapValue(parameter.value, runInputs)
            }
            val mappedParameters = kotlinx.serialization.json.buildJsonObject {
                parameterValues.forEach { (location, values) -> put(location, JsonObject(values)) }
            }
            val body = step.requestBody?.let { mapValue(it, runInputs) }
            kotlinx.serialization.json.buildJsonObject {
                put("inputs", runInputs)
                put("parameters", mappedParameters)
                if (body != null) put("requestBody", body)
            }.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun mapStep(step: ArazzoStep, result: CapabilityResult): JsonObject? {
        val snapshot = result.constituents.singleOrNull()?.response
        if (snapshot != null) responses[step.stepId] = snapshot
        val mapped = map(step.outputs.orEmpty(), context(snapshot)) ?: return null
        outputs[step.stepId] = mapped
        return mapped
    }

    fun mapWorkflow(workflow: ArazzoWorkflow): JsonObject? = map(workflow.outputs.orEmpty(), context())

    fun criteriaFailure(step: ArazzoStep, result: CapabilityResult): ExecutionFailure? {
        val snapshot = result.constituents.singleOrNull()?.response
        val responseContext = context(snapshot)
        val passed = try {
            val criteria = step.successCriteria
            if (criteria == null) {
                snapshot?.http?.status?.let { it in 200..299 } == true
            } else {
                criteria.isNotEmpty() && criteria.all { evaluateArazzoCriterion(it, responseContext) }
            }
        } catch (_: Exception) {
            false
        }
        return if (passed) null else ExecutionFailure(
            "success_criteria",
            "Arazzo step response did not satisfy its success criteria",
            stepId = step.stepId,
            details = snapshot?.http?.status?.let { mapOf("status" to it.toString()) }.orEmpty(),
        )
    }

    private fun context(current: ResponseSnapshot? = null) = ArazzoExpressionContext(
        inputs = checkNotNull(parsedInputs),
        stepOutputs = outputs,
        responseBody = current?.body ?: BodyValue.Unavailable,
        response = current?.http,
        stepResponses = responses,
    )

    private fun mapValue(value: JsonElement, inputs: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (_, child) -> mapValue(child, inputs) })
        is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(value.map { mapValue(it, inputs) })
        is kotlinx.serialization.json.JsonPrimitive -> {
            if (value.isString && value.content.startsWith("\$")) {
                evaluateArazzoExpression(value.content, context().copy(inputs = inputs))
            } else value
        }
    }

    private fun map(expressions: Map<String, String>, context: ArazzoExpressionContext): JsonObject? = try {
        kotlinx.serialization.json.buildJsonObject {
            expressions.forEach { (name, expression) -> put(name, evaluateArazzoExpression(expression, context)) }
        }
    } catch (_: Exception) {
        null
    }
}

private class EventingInvocationScope(
    private val delegate: InvocationScope,
    private val events: ExecutionEventSink,
) : InvocationScope by delegate {
    override suspend fun acquireCall(identity: ConstituentIdentity, retry: Boolean): ExecutionFailure? {
        val failure = delegate.acquireCall(identity, retry)
        if (failure != null) return failure
        try {
            events.emit(ExecutionEvent(metadata.copy(values = metadata.values.toMap()), ExecutionEventKind.CONSTITUENT_STARTED, identity))
        } catch (cause: Throwable) {
            delegate.releaseCall(identity)
            throw cause
        }
        return null
    }

    override suspend fun releaseCall(identity: ConstituentIdentity) {
        delegate.releaseCall(identity)
        events.emit(ExecutionEvent(metadata.copy(values = metadata.values.toMap()), ExecutionEventKind.CONSTITUENT_FINISHED, identity))
    }
}

private class PluginMetadataEventSink(
    private val delegate: ExecutionEventSink,
    private val plugins: OrchestrationPluginRegistry,
    private val scope: InvocationScope,
    private val state: OrchestrationPluginState,
) : ExecutionEventSink {
    override suspend fun emit(event: ExecutionEvent) {
        val stage = when (event.kind) {
            ExecutionEventKind.RUN_STARTED -> OrchestrationStage.PREPARE
            ExecutionEventKind.STEP_STARTED -> OrchestrationStage.SCHEDULE
            ExecutionEventKind.CONSTITUENT_STARTED -> OrchestrationStage.BEFORE_ATTEMPT
            ExecutionEventKind.CONSTITUENT_FINISHED -> OrchestrationStage.AFTER_ATTEMPT
            ExecutionEventKind.STEP_FINISHED, ExecutionEventKind.RUN_FINISHED -> OrchestrationStage.COMPLETE
        }
        val context = OrchestrationStageContext(stage, scope = scope, state = state,
            constituent = event.constituent, payload = event,
            lifecycleScope = when (event.kind) {
                ExecutionEventKind.RUN_STARTED, ExecutionEventKind.RUN_FINISHED -> OrchestrationLifecycleScope.RUN
                ExecutionEventKind.STEP_STARTED, ExecutionEventKind.STEP_FINISHED -> OrchestrationLifecycleScope.STEP
                ExecutionEventKind.CONSTITUENT_STARTED, ExecutionEventKind.CONSTITUENT_FINISHED -> OrchestrationLifecycleScope.ATTEMPT
            })
        val enriched = event.copy(metadata = event.metadata.copy(values =
            event.metadata.values + plugins.metadata(context)))
        delegate.emit(enriched)
    }
}
