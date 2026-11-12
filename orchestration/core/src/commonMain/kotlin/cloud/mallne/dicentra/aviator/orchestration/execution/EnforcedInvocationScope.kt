package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/** Shared run context and installed-policy runtime. Construct one instance per run and pass it
 * through every binding, retry, fan-out and nested invocation. Optional accounting is instantiated
 * only by explicitly installed policy plugins. Callers release admitted calls in `finally`.
 */
class EnforcedInvocationScope private constructor(
    override val metadata: ExecutionMetadata,
    override val budget: ExecutionBudget,
    private val authorizeCall: suspend (ConstituentIdentity) -> ExecutionFailure?,
    private val now: () -> Instant,
    private val policyPlugins: List<InvocationPolicyPlugin>,
    private val policies: List<InvocationPolicy>,
    private val state: State,
) : InvocationScope {
    override val enforcedDeadline: Instant? get() = policies.firstNotNullOfOrNull { it.enforcedDeadline }

    constructor(
        metadata: ExecutionMetadata,
        budget: ExecutionBudget,
        authorizeCall: suspend (ConstituentIdentity) -> ExecutionFailure? = { null },
        now: () -> Instant = { Clock.System.now() },
        policyPlugins: List<InvocationPolicyPlugin> = emptyList(),
    ) : this(metadata, budget, authorizeCall, now, policyPlugins,
        policyPlugins.map { it.create(metadata, budget, now) }, State()) {
        require(policyPlugins.size <= 1) { "At most one atomic invocation policy plugin may be installed per run" }
    }

    /** Creates a step-scoped view sharing all run-wide reservations and counters. */
    fun forStep(stepId: String): EnforcedInvocationScope {
        require(stepId.isNotBlank()) { "stepId must not be blank" }
        return EnforcedInvocationScope(metadata.copy(stepId = stepId), budget, authorizeCall, now,
            policyPlugins, policies, state)
    }

    override suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure? {
        currentCoroutineContext().ensureActive()
        authorizeCall(identity)?.let { return it }
        for (policy in policies) policy.authorize(identity)?.let { return it }
        return null
    }

    override suspend fun pluginState(owner: String): OrchestrationPluginState {
        require(owner.isNotBlank()) { "Plugin state owner must not be blank" }
        return state.mutex.withLock {
            state.pluginStates.getOrPut(owner) { OrchestrationPluginState() }
        }
    }

    override suspend fun acquireCall(identity: ConstituentIdentity, retry: Boolean): ExecutionFailure? {
        currentCoroutineContext().ensureActive()
        for (policy in policies) policy.acquireCall(metadata, identity, retry)?.let { return it }
        return null
    }

    override suspend fun releaseCall(identity: ConstituentIdentity) {
        withContext(NonCancellable) { policies.forEach { it.releaseCall(identity) } }
    }

    override suspend fun checkpoint(transitions: Int, nestingDepth: Int): ExecutionFailure? {
        require(transitions >= 0 && nestingDepth >= 0)
        currentCoroutineContext().ensureActive()
        for (policy in policies) policy.checkpoint(metadata, transitions, nestingDepth)?.let { return it }
        return null
    }

    private class State {
        val mutex = Mutex()
        val pluginStates = mutableMapOf<String, OrchestrationPluginState>()
    }
}
