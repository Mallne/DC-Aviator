package cloud.mallne.dicentra.aviator.orchestration.policy

import kotlinx.serialization.Serializable

/** Explicit application policy; no implicit first-match or universal all-success default. */
@Serializable
data class CapabilityPolicy(
    val strategy: ExecutionStrategy,
    val ordering: ConstituentOrdering,
    val partialFailure: PartialFailurePolicy,
    val emptySelection: EmptySelectionPolicy,
)

/** FIRST_USABLE tries constituents in order until the binding's usable-output predicate succeeds. */
@Serializable
enum class ExecutionStrategy { AGGREGATE, FIRST_USABLE }

/** RESOLUTION preserves application order; IDENTITY sorts by source, revision, provider, ID and path. */
@Serializable
enum class ConstituentOrdering { RESOLUTION, IDENTITY }

/**
 * Applies to AGGREGATE. ALLOW_PARTIAL still requires usable output, determined by the binding.
 * FIRST_USABLE instead stops at the first usable result and retains earlier failed attempts.
 */
@Serializable
enum class PartialFailurePolicy { REQUIRE_ALL, ALLOW_PARTIAL }

/** SUCCEED permits an explicitly defined empty aggregate; it does not synthesize a JSON value. */
@Serializable
enum class EmptySelectionPolicy { FAIL, SUCCEED }
