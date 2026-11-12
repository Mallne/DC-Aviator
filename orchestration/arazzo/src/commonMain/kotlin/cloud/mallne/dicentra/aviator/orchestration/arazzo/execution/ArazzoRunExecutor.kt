package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/** Narrow sequential runner seam; operation resolution and Aviator binding remain application-owned. */
fun interface ArazzoStepExecutor {
    suspend fun execute(step: ArazzoStep, inputs: String, scope: InvocationScope): ArazzoStepExecution
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
        val runMetadata = ExecutionMetadata(runId, identity, values = metadata.toMap())
        val runScope = EnforcedInvocationScope(runMetadata, budget, now = now,
            policyPlugins = plugins.invocationPolicyPlugins())
        val pluginState = runScope.pluginState("orchestration")
        val safeEvents = PluginMetadataEventSink(events.redacting(), plugins, runScope, pluginState)
        val completed = mutableListOf<String>()
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
            val preparation = OrchestrationStageContext(OrchestrationStage.PREPARE, scope = runScope,
                state = pluginState, payload = workflow, input = inputs, lifecycleScope = OrchestrationLifecycleScope.RUN)
            plugins.execute(preparation, OrchestrationHookPhase.BEFORE)
            plugins.execute(preparation, OrchestrationHookPhase.AFTER)
            if (preparation.shortCircuit != null) {
                val failure = ExecutionFailure("invalid_plugin_short_circuit", "Run preparation cannot short-circuit execution")
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            preparation.rejection?.let { rejection ->
                val failure = rejection.copy(workflowId = workflow.workflowId)
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            val preparedWorkflow = preparation.payload as? ArazzoWorkflow
            val preparedInputs = preparation.input as? String
            if (preparedWorkflow == null || preparedInputs == null) {
                val failure = ExecutionFailure("invalid_plugin_stage_data", "Preparation plugin returned incompatible stage data")
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            val validation = OrchestrationStageContext(OrchestrationStage.VALIDATE, scope = runScope,
                state = pluginState, payload = preparedWorkflow, input = preparedInputs,
                lifecycleScope = OrchestrationLifecycleScope.RUN)
            plugins.execute(validation, OrchestrationHookPhase.BEFORE)
            plugins.execute(validation, OrchestrationHookPhase.AFTER)
            if (validation.shortCircuit != null) {
                val failure = ExecutionFailure("invalid_plugin_short_circuit", "Validation cannot short-circuit execution")
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            validation.rejection?.let { rejection ->
                val failure = rejection.copy(workflowId = preparedWorkflow.workflowId)
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            val executableWorkflow = validation.payload as? ArazzoWorkflow
            val executableInputs = validation.input as? String
            if (executableWorkflow == null || executableInputs == null) {
                val failure = ExecutionFailure("invalid_plugin_stage_data", "Validation plugin returned incompatible stage data")
                terminalFailure = true
                notifyFailure(failure)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, emptyList()))
            }
            val unsupportedStep = executableWorkflow.steps.firstOrNull {
                it.operationId == null || it.operationPath != null || it.workflowId != null ||
                    it.requestBody != null || it.successCriteria.orEmpty().isNotEmpty() || it.outputs != null ||
                    it.dependsOn.isNotEmpty()
            }
            if (unsupportedStep != null || executableWorkflow.outputs != null || executableWorkflow.inputs != null || executableWorkflow.dependsOn.isNotEmpty()) {
                val failure = ExecutionFailure(
                    code = "unsupported_step",
                    message = "Workflow contains executable features outside the supported sequential operationId profile",
                    workflowId = executableWorkflow.workflowId,
                    stepId = unsupportedStep?.stepId,
                )
                val outcome = ExecutionOutcome.Failed(failure, completed.toList())
                terminalFailure = true
                notifyFailure(failure, unsupportedStep?.stepId)
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, outcome)
            }

            for (step in executableWorkflow.steps) {
                val stepMetadata = runMetadata.copy(stepId = step.stepId)
                val stepScope = runScope.forStep(step.stepId)
                val budgetFailure = stepScope.checkpoint(transitions = 1)
                if (budgetFailure != null) {
                    val outcome = ExecutionOutcome.Failed(
                        budgetFailure.copy(workflowId = workflow.workflowId, stepId = step.stepId), completed.toList(),
                    )
                    terminalFailure = true
                    notifyFailure(budgetFailure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, outcome)
                }
                activeStep = stepMetadata
                safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_STARTED))
                val scheduling = OrchestrationStageContext(OrchestrationStage.SCHEDULE, scope = stepScope,
                    state = pluginState, payload = step, input = executableInputs,
                    lifecycleScope = OrchestrationLifecycleScope.STEP)
                plugins.execute(scheduling, OrchestrationHookPhase.BEFORE)
                plugins.execute(scheduling, OrchestrationHookPhase.AFTER)
                if (scheduling.shortCircuit != null) {
                    val failure = ExecutionFailure("invalid_plugin_short_circuit", "Arazzo scheduling cannot return a capability result")
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
                scheduling.rejection?.let { rejection ->
                    val failure = rejection.copy(workflowId = executableWorkflow.workflowId, stepId = step.stepId)
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
                val scheduledStep = scheduling.payload as? ArazzoStep
                val scheduledInputs = scheduling.input as? String
                if (scheduledStep == null || scheduledInputs == null) {
                    val failure = ExecutionFailure("invalid_plugin_stage_data", "Scheduling plugin returned incompatible stage data",
                        workflowId = executableWorkflow.workflowId, stepId = step.stepId)
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
                val mapping = OrchestrationStageContext(OrchestrationStage.MAP_INPUT, scope = stepScope,
                    state = pluginState, payload = scheduledStep, input = scheduledInputs,
                    lifecycleScope = OrchestrationLifecycleScope.STEP)
                plugins.execute(mapping, OrchestrationHookPhase.BEFORE)
                plugins.execute(mapping, OrchestrationHookPhase.AFTER)
                if (mapping.shortCircuit != null) {
                    val failure = ExecutionFailure("invalid_plugin_short_circuit", "Input mapping cannot short-circuit Arazzo")
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
                mapping.rejection?.let { rejection ->
                    val failure = rejection.copy(workflowId = executableWorkflow.workflowId, stepId = step.stepId)
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
                val mappedStep = mapping.payload as? ArazzoStep
                val mappedInputs = mapping.input as? String
                if (mappedStep == null || mappedInputs == null) {
                    val failure = ExecutionFailure("invalid_plugin_stage_data", "Input mapping plugin returned incompatible stage data",
                        workflowId = executableWorkflow.workflowId, stepId = step.stepId)
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
                val remainingDeadline = stepScope.enforcedDeadline?.let { (it - now()).coerceAtLeast(Duration.ZERO) }
                val execution = try {
                    if (remainingDeadline == null) {
                        stepExecutor.execute(mappedStep, mappedInputs, EventingInvocationScope(stepScope, safeEvents))
                    } else {
                        withTimeoutOrNull(remainingDeadline) {
                            stepExecutor.execute(mappedStep, mappedInputs, EventingInvocationScope(stepScope, safeEvents))
                        } ?: ArazzoStepExecution.Failed(
                            ExecutionFailure("deadline", "Execution budget exhausted: deadline"),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    // Do not leak provider exception messages, credentials, or response bodies.
                    ArazzoStepExecution.Failed(
                        ExecutionFailure("operation_failed", "Arazzo step execution failed"),
                    )
                }
                val stepFailure = when (execution) {
                    is ArazzoStepExecution.Failed -> execution.failure
                    is ArazzoStepExecution.Completed -> (execution.result.outcome as? CapabilityOutcome.Failed)?.failure
                }
                if (stepFailure != null) {
                    val failure = stepFailure.copy(workflowId = executableWorkflow.workflowId, stepId = step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    val outcome = ExecutionOutcome.Failed(failure, completed.toList())
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, outcome)
                }
                completed += step.stepId
                val completion = OrchestrationStageContext(OrchestrationStage.COMPLETE, scope = stepScope,
                    state = pluginState, payload = step, result = (execution as? ArazzoStepExecution.Completed)?.result,
                    lifecycleScope = OrchestrationLifecycleScope.STEP)
                plugins.execute(completion, OrchestrationHookPhase.BEFORE)
                plugins.execute(completion, OrchestrationHookPhase.AFTER)
                if (completion.shortCircuit != null) {
                    val failure = ExecutionFailure("invalid_plugin_short_circuit", "Arazzo completion cannot replace the workflow result")
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
                completion.rejection?.let { rejection ->
                    val failure = rejection.copy(workflowId = executableWorkflow.workflowId, stepId = step.stepId)
                    terminalFailure = true
                    notifyFailure(failure, step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, ExecutionOutcome.Failed(failure, completed.toList()))
                }
                safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                activeStep = null
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

        return ExecutionResult(runId, identity, ExecutionOutcome.Succeeded())
    }

    private companion object {
        val CANCELLATION_EVENT_TIMEOUT: Duration = 250.milliseconds
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
