package cloud.mallne.dicentra.aviator.plugin.orchestration.budget

import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationPolicy
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationPolicyPlugin
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPlugin
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginConfig
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginInstance
import cloud.mallne.dicentra.aviator.orchestration.plugin.orchestrationPlugin
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/** Optional Aviator-style plugin artifact for atomic, per-run non-byte execution budgets. */
object ExecutionBudgetPlugin : OrchestrationPlugin {
    override val identity: String = "execution-budget"

    override fun install(config: OrchestrationPluginConfig.() -> Unit): OrchestrationPluginInstance {
        val configured = OrchestrationPluginConfig().apply(config)
        configured.invocationPolicy = InvocationPolicyPlugin { _, budget, now -> BudgetPolicy(budget, now) }
        return orchestrationPlugin(identity) {
            serviceFilter += configured.serviceFilter
            stages += configured.stages
            phases += configured.phases
            lifecycleScopes += configured.lifecycleScopes
            executor = configured.executor
            metadataProvider = configured.metadataProvider
            invocationPolicy = configured.invocationPolicy
        }
    }
}

/** Convenience for installing the standard budget policy without a plugin-specific configuration block. */
fun executionBudgetPolicyPlugin() = ExecutionBudgetPlugin.install()

private class BudgetPolicy(
    private val budget: ExecutionBudget,
    private val now: () -> Instant,
) : InvocationPolicy {
    override val enforcedDeadline: Instant? get() = budget.deadline

    private val mutex = Mutex()
    private val activeCalls = mutableMapOf<ConstituentIdentity, Int>()
    private val stepCalls = mutableMapOf<String?, Int>()
    private val stepRetries = mutableMapOf<String?, Int>()
    private var calls = 0
    private var retries = 0
    private var transitions = 0

    override suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure? = deadlineFailure()

    override suspend fun acquireCall(metadata: ExecutionMetadata, identity: ConstituentIdentity, retry: Boolean): ExecutionFailure? = mutex.withLock {
        deadlineFailure()?.let { return@withLock it }
        if (budget.maxCalls?.let { calls >= it } == true) return@withLock failure("calls")
        if (budget.maxCallsPerStep?.let { (stepCalls[metadata.stepId] ?: 0) >= it } == true) return@withLock failure("step_calls")
        if (retry && budget.maxRetries?.let { retries >= it } == true) return@withLock failure("retries")
        if (retry && budget.maxRetriesPerStep?.let { (stepRetries[metadata.stepId] ?: 0) >= it } == true) return@withLock failure("step_retries")
        if (budget.maxConcurrency?.let { activeCalls.values.sum() >= it } == true) return@withLock failure("concurrency")
        calls++
        stepCalls[metadata.stepId] = (stepCalls[metadata.stepId] ?: 0) + 1
        if (retry) {
            retries++
            stepRetries[metadata.stepId] = (stepRetries[metadata.stepId] ?: 0) + 1
        }
        activeCalls[identity] = (activeCalls[identity] ?: 0) + 1
        null
    }

    override suspend fun releaseCall(identity: ConstituentIdentity) = mutex.withLock {
        val count = checkNotNull(activeCalls[identity]) { "No admitted call to release" }
        if (count == 1) activeCalls.remove(identity) else activeCalls[identity] = count - 1
        Unit
    }

    override suspend fun checkpoint(metadata: ExecutionMetadata, transitions: Int, nestingDepth: Int): ExecutionFailure? = mutex.withLock {
        deadlineFailure()?.let { return@withLock it }
        if (budget.maxTransitions?.let { transitions > it - this.transitions } == true) return@withLock failure("transitions")
        if (budget.maxNestingDepth?.let { nestingDepth > it } == true) return@withLock failure("nesting")
        this.transitions += transitions
        null
    }

    private fun deadlineFailure(): ExecutionFailure? =
        if (budget.deadline?.let { now() >= it } == true) failure("deadline") else null

    private fun failure(code: String) = ExecutionFailure(code, "Execution budget exhausted: $code")
}
