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
        val safeEvents = events.redacting()
        val runMetadata = ExecutionMetadata(runId, identity, values = metadata.toMap())
        val runScope = EnforcedInvocationScope(runMetadata, budget, now = now)
        val completed = mutableListOf<String>()
        var activeStep: ExecutionMetadata? = null
        try {
            safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_STARTED))
            val unsupportedStep = workflow.steps.firstOrNull {
                it.operationId == null || it.operationPath != null || it.workflowId != null ||
                    it.requestBody != null || it.successCriteria.orEmpty().isNotEmpty() || it.outputs != null ||
                    it.dependsOn.isNotEmpty()
            }
            if (unsupportedStep != null || workflow.outputs != null || workflow.inputs != null || workflow.dependsOn.isNotEmpty()) {
                val failure = ExecutionFailure(
                    code = "unsupported_step",
                    message = "Workflow contains executable features outside the supported sequential operationId profile",
                    workflowId = workflow.workflowId,
                    stepId = unsupportedStep?.stepId,
                )
                val outcome = ExecutionOutcome.Failed(failure, completed.toList())
                safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                return ExecutionResult(runId, identity, outcome)
            }

            for (step in workflow.steps) {
                val stepMetadata = runMetadata.copy(stepId = step.stepId)
                val stepScope = runScope.forStep(step.stepId)
                val budgetFailure = stepScope.checkpoint(transitions = 1)
                if (budgetFailure != null) {
                    val outcome = ExecutionOutcome.Failed(
                        budgetFailure.copy(workflowId = workflow.workflowId, stepId = step.stepId), completed.toList(),
                    )
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, outcome)
                }
                activeStep = stepMetadata
                safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_STARTED))
                val remainingDeadline = budget.deadline?.let { (it - now()).coerceAtLeast(Duration.ZERO) }
                val execution = try {
                    if (remainingDeadline == null) {
                        stepExecutor.execute(step, inputs, EventingInvocationScope(stepScope, safeEvents))
                    } else {
                        withTimeoutOrNull(remainingDeadline) {
                            stepExecutor.execute(step, inputs, EventingInvocationScope(stepScope, safeEvents))
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
                    val failure = stepFailure.copy(workflowId = workflow.workflowId, stepId = step.stepId)
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                    activeStep = null
                    val outcome = ExecutionOutcome.Failed(failure, completed.toList())
                    safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.FAILED))
                    return ExecutionResult(runId, identity, outcome)
                }
                completed += step.stepId
                safeEvents.emit(ExecutionEvent(stepMetadata, ExecutionEventKind.STEP_FINISHED))
                activeStep = null
            }
            safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.SUCCEEDED))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                // Cancellation notifications are best effort and bounded: a stalled observer must
                // not pin the caller or turn cancellation into detached background work.
                withTimeoutOrNull(CANCELLATION_EVENT_TIMEOUT) {
                    activeStep?.let { safeEvents.emit(ExecutionEvent(it, ExecutionEventKind.STEP_FINISHED)) }
                    safeEvents.emit(ExecutionEvent(runMetadata, ExecutionEventKind.RUN_FINISHED, terminal = ExecutionEventOutcome.CANCELLED))
                }
            }
            throw cancelled
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
