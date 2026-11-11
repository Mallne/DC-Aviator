package cloud.mallne.dicentra.aviator.orchestration.policy

import kotlinx.serialization.Serializable

/**
 * Upper bounds shared by all format-specific scheduling behavior. Null means unspecified.
 *
 * Calls, retries and concurrency count actual constituent invocations, not just workflow steps:
 * one ServiceLocator invocation can execute multiple operations. Retained bytes include aggregate
 * and constituent data. Bindings and schedulers must share accounting across fan-out, fallback
 * and nesting; this value object does not implement enforcement.
 */
@Serializable
data class ExecutionBudget(
    val deadlineEpochMillis: Long? = null,
    val maxCalls: Int? = null,
    val maxTransitions: Int? = null,
    val maxRetries: Int? = null,
    val maxNestingDepth: Int? = null,
    val maxConcurrency: Int? = null,
    val maxRetainedBytes: Long? = null,
) {
    init {
        require(maxCalls == null || maxCalls >= 0) { "maxCalls must be nonnegative" }
        require(maxTransitions == null || maxTransitions >= 0) { "maxTransitions must be nonnegative" }
        require(maxRetries == null || maxRetries >= 0) { "maxRetries must be nonnegative" }
        require(maxNestingDepth == null || maxNestingDepth >= 0) { "maxNestingDepth must be nonnegative" }
        require(maxConcurrency == null || maxConcurrency >= 0) { "maxConcurrency must be nonnegative" }
        require(maxRetainedBytes == null || maxRetainedBytes >= 0) { "maxRetainedBytes must be nonnegative" }
    }
}
