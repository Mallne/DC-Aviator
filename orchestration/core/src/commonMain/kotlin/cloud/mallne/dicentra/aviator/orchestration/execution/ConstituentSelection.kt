package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.ConstituentOrdering

/**
 * Select already-materialized operations without inspecting their client or fetching definitions.
 * This is targeting only: authorization and budget checks remain per actual transport attempt.
 */
fun <T> selectConstituents(
    invocation: CapabilityInvocation,
    candidates: List<T>,
    identityOf: (T) -> ConstituentIdentity,
): List<T> {
    val selected = candidates.map { it to identityOf(it) }.filter { invocation.accepts(it.second) }
    val ordered = when (invocation.policy.ordering) {
        ConstituentOrdering.RESOLUTION -> selected
        ConstituentOrdering.IDENTITY -> selected.sortedWith(
            compareBy<Pair<T, ConstituentIdentity>> { it.second.sourceId }
                .thenBy { it.second.sourceRevision }
                .thenBy { it.second.providerId }
                .thenBy { it.second.operationId.orEmpty() }
                .thenBy { it.second.operationPath.orEmpty() },
        )
    }
    return ordered.map { it.first }
}
