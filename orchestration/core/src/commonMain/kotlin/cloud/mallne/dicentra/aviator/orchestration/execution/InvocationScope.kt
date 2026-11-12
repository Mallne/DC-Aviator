package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginState
import kotlin.time.Instant

/** Shared run services, not credentials or state stored in a globally reusable prepared handle. */
interface InvocationScope {
    val metadata: ExecutionMetadata
    val budget: ExecutionBudget
    /** An active timeout only when an installed policy explicitly enforces the budget deadline. */
    val enforcedDeadline: Instant? get() = null

    /** State owned by this run scope and shared by child steps/bindings; keyed by plugin owner. */
    suspend fun pluginState(owner: String): OrchestrationPluginState = OrchestrationPluginState()

    /** Rechecked immediately before each actual call, including retries. Null means authorized. */
    suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure?

    /**
     * Invoke installed policy plugins to reserve an actual call (and retry when applicable) and any
     * configured concurrency slot. Null means admitted. With no optional policy plugin, this is an
     * admission no-op; host authorization is still checked independently. A binding must call
     * [releaseCall] in finally after admission, including cancellation. Release must complete even
     * when the caller's Job is cancelled. Cumulative counts are never refunded by policy plugins.
     */
    suspend fun acquireCall(identity: ConstituentIdentity, retry: Boolean = false): ExecutionFailure?

    /** Release only the active-call slot, never cumulative call/retry counts. */
    suspend fun releaseCall(identity: ConstituentIdentity)

    /** Invoke installed policies to check deadlines/charge transitions and validate nesting. */
    suspend fun checkpoint(transitions: Int = 0, nestingDepth: Int = 0): ExecutionFailure?
}

/** Runtime policy created by an explicitly installed orchestration policy plugin. */
interface InvocationPolicy {
    val enforcedDeadline: Instant? get() = null
    suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure? = null
    suspend fun acquireCall(metadata: ExecutionMetadata, identity: ConstituentIdentity, retry: Boolean): ExecutionFailure?
    suspend fun releaseCall(identity: ConstituentIdentity)
    suspend fun checkpoint(metadata: ExecutionMetadata, transitions: Int, nestingDepth: Int): ExecutionFailure?
}

/** Factory retained by an installed policy plugin and instantiated once per run. */
fun interface InvocationPolicyPlugin {
    fun create(metadata: ExecutionMetadata, budget: ExecutionBudget, now: () -> Instant): InvocationPolicy
}
