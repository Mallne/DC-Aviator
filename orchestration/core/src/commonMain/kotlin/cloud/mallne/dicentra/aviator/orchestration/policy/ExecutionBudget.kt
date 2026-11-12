package cloud.mallne.dicentra.aviator.orchestration.policy

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Upper bounds shared by all format-specific scheduling behavior. Null means unspecified.
 *
 * Calls, retries and concurrency count actual constituent invocations, not just workflow steps:
 * one ServiceLocator invocation can execute multiple operations. Bindings and schedulers must
 * share accounting across fan-out, fallback and nesting; this value object does not implement
 * enforcement. Payload size and heap-memory accounting are not part of this contract.
 */
@Serializable
data class ExecutionBudget(
    val deadline: Instant? = null,
    val maxCalls: Int? = null,
    val maxTransitions: Int? = null,
    val maxRetries: Int? = null,
    val maxNestingDepth: Int? = null,
    val maxConcurrency: Int? = null,
    val maxCallsPerStep: Int? = null,
    val maxRetriesPerStep: Int? = null,
) {
    init {
        require(maxCalls == null || maxCalls >= 0) { "maxCalls must be nonnegative" }
        require(maxTransitions == null || maxTransitions >= 0) { "maxTransitions must be nonnegative" }
        require(maxRetries == null || maxRetries >= 0) { "maxRetries must be nonnegative" }
        require(maxNestingDepth == null || maxNestingDepth >= 0) { "maxNestingDepth must be nonnegative" }
        require(maxConcurrency == null || maxConcurrency >= 0) { "maxConcurrency must be nonnegative" }
        require(maxCallsPerStep == null || maxCallsPerStep >= 0) { "maxCallsPerStep must be nonnegative" }
        require(maxRetriesPerStep == null || maxRetriesPerStep >= 0) { "maxRetriesPerStep must be nonnegative" }
    }
}
