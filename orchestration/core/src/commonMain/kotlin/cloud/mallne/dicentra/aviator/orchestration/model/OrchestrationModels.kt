package cloud.mallne.dicentra.aviator.orchestration.model

import kotlinx.serialization.Serializable

/** Stable identity of a workflow definition, independent of any execution of it. */
@Serializable
data class WorkflowIdentity(
    val format: String,
    val formatVersion: String,
    val documentId: String,
    val revision: String,
)

/** Identity allocated to one execution. It must not be used as a workflow revision. */
@Serializable
@JvmInline
value class RunId(val value: String)

/** Namespaced, immutable-by-convention metadata for correlating runs and steps. */
@Serializable
data class ExecutionMetadata(
    val runId: RunId,
    val workflow: WorkflowIdentity,
    val stepId: String? = null,
    val values: Map<String, String> = emptyMap(),
)

/** Inputs are an opaque JSON value to the format-independent contract. */
@Serializable
data class ExecutionRequest(
    val workflow: WorkflowIdentity,
    val inputs: String = "{}",
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
sealed interface ExecutionOutcome {
    @Serializable
    data class Succeeded(val outputs: String = "{}") : ExecutionOutcome

    /** Failure may follow side effects; the contract does not promise rollback. */
    @Serializable
    data class Failed(
        val failure: ExecutionFailure,
        val completedSteps: List<String> = emptyList(),
        val outputs: String? = null,
    ) : ExecutionOutcome

    @Serializable
    data class Cancelled(
        val completedSteps: List<String> = emptyList(),
        val outputs: String? = null,
    ) : ExecutionOutcome
}

@Serializable
data class ExecutionFailure(
    val code: String,
    val message: String,
    val workflowId: String? = null,
    val stepId: String? = null,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class ExecutionResult(
    val runId: RunId,
    val workflow: WorkflowIdentity,
    val outcome: ExecutionOutcome,
)

/** Safe correlation fields only: events must not contain bodies, credentials or raw exceptions. */
@Serializable
data class ExecutionEvent(
    val metadata: ExecutionMetadata,
    val kind: ExecutionEventKind,
    val constituent: ConstituentIdentity? = null,
)

@Serializable
enum class ExecutionEventKind {
    RUN_STARTED, STEP_STARTED, CONSTITUENT_STARTED, CONSTITUENT_FINISHED, STEP_FINISHED, RUN_FINISHED,
}

/**
 * Suspending delivery provides backpressure and preserves caller cancellation. Implementations
 * emit in run order; concurrent constituent events may interleave. A sink failure aborts delivery
 * and propagates to the caller rather than silently dropping events. Persistence is not implied.
 */
fun interface ExecutionEventSink {
    suspend fun emit(event: ExecutionEvent)

    object Ignore : ExecutionEventSink {
        override suspend fun emit(event: ExecutionEvent) = Unit
    }
}
