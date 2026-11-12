package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ConstituentOrdering
import cloud.mallne.dicentra.aviator.orchestration.policy.EmptySelectionPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import kotlin.time.Instant
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionStrategy
import cloud.mallne.dicentra.aviator.orchestration.policy.PartialFailurePolicy

internal fun contractPolicy() = CapabilityPolicy(
    ExecutionStrategy.AGGREGATE, ConstituentOrdering.IDENTITY,
    PartialFailurePolicy.ALLOW_PARTIAL, EmptySelectionPolicy.FAIL,
)

/** Synchronous deterministic contract fixture, not a production concurrent budget implementation. */
internal class ContractScope(
    override val metadata: ExecutionMetadata,
    override val budget: ExecutionBudget = ExecutionBudget(),
    private val denied: Set<ConstituentIdentity> = emptySet(),
    private val now: () -> Instant = { Instant.fromEpochMilliseconds(0L) },
) : InvocationScope {
    private val states = mutableMapOf<String, cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginState>()
    val authorized = mutableListOf<ConstituentIdentity>()
    val admitted = mutableListOf<ConstituentIdentity>()
    private val active = mutableMapOf<ConstituentIdentity, Int>()
    var retries = 0
        private set
    private var transitions = 0

    override suspend fun pluginState(owner: String) =
        states.getOrPut(owner) { cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginState() }

    override suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure? {
        authorized += identity
        return if (identity in denied) rejected("unauthorized") else null
    }

    override suspend fun acquireCall(identity: ConstituentIdentity, retry: Boolean): ExecutionFailure? {
        deadlineFailure()?.let { return it }
        if (budget.maxCalls?.let { admitted.size >= it } == true) return rejected("calls")
        if (budget.maxRetries?.let { retry && retries >= it } == true) return rejected("retries")
        if (budget.maxConcurrency?.let { active.values.sum() >= it } == true) return rejected("concurrency")
        admitted += identity
        if (retry) retries++
        active[identity] = active.getOrElse(identity) { 0 } + 1
        return null
    }

    override suspend fun releaseCall(identity: ConstituentIdentity) {
        val count = checkNotNull(active[identity])
        if (count == 1) active.remove(identity) else active[identity] = count - 1
    }

    override suspend fun checkpoint(transitions: Int, nestingDepth: Int): ExecutionFailure? {
        require(transitions >= 0 && nestingDepth >= 0)
        deadlineFailure()?.let { return it }
        if (budget.maxTransitions?.let { transitions > it - this.transitions } == true) return rejected("transitions")
        if (budget.maxNestingDepth?.let { nestingDepth > it } == true) return rejected("nesting")
        this.transitions += transitions
        return null
    }

    private fun rejected(code: String) = ExecutionFailure(code, "Rejected by test scope")

    private fun deadlineFailure(): ExecutionFailure? =
        if (budget.deadline?.let { now() >= it } == true) rejected("deadline") else null
}
