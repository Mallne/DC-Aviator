package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionRequest
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionResult
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEventSink
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityCheck
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityRequirements
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.orchestration.policy.OrchestratorCapabilities

/** Format-specific orchestration boundary. Execution is caller-owned and cancellation propagates
 * through the caller's coroutine; implementations do not create detached work.
 */
interface Orchestrator {
    val capabilities: OrchestratorCapabilities

    fun checkCapabilities(requirements: CapabilityRequirements): CapabilityCheck

    suspend fun execute(
        request: ExecutionRequest,
        budget: ExecutionBudget = ExecutionBudget(),
        events: ExecutionEventSink = ExecutionEventSink.Ignore,
    ): ExecutionResult
}

/** Application-owned resolution and invocation boundaries. Implementations enforce policy at
 * invocation time even when a target was successfully resolved earlier.
 *
 * [ServiceLocator], including its flavour, is the native execution identity. A locator can select
 * multiple operations with different OpenAPI operation IDs whose results Aviator aggregates.
 * This applies to embedded and hosted execution; resolution must not assume one operation per
 * locator or require Synapse. Selection, aggregation and fallback policy belong to the application
 * binding, including ordering, empty selections, partial failures and usable-result criteria.
 *
 * Format-specific references map to bindings with explicit targeting restrictions. A standard
 * Arazzo concrete-operation reference must not silently fan out to every matching locator;
 * an explicitly referenced aggregate gateway operation retains its public aggregate contract.
 * An operationId must never be implicitly treated as a ServiceLocator.
 *
 * Results contain aggregate values and constituent outcomes/provenance, not necessarily
 * one HTTP response. Bindings must not invent aggregate HTTP status/headers. They authorize each
 * selected constituent, isolate its request context and account for actual constituent calls,
 * retries, concurrency and retained bytes. These interfaces do not themselves enforce policy
 * by themselves; production bindings and budget enforcement remain separate implementation work.
 */
interface OperationBinding<in I> {
    suspend fun resolve(invocation: CapabilityInvocation): ResolvedOperation

    suspend fun invoke(
        operation: ResolvedOperation,
        input: I,
        scope: InvocationScope,
    ): CapabilityResult
}

/** Opaque prepared ServiceLocator binding, possibly covering multiple operations. Its lifetime,
 * safe reuse, constituent restrictions and selection policy are owned by the binding implementation.
 * Invocation delegates execution/aggregation to Aviator; preparation does not grant authorization.
 * A handle is owned by its producing binding and must not be transferred to another binding.
 * It contains no per-run mutable state or authorization grant. Concurrent reuse requires explicit
 * support from its binding; otherwise resolve separately per invocation.
 */
interface ResolvedOperation {
    /** Immutable-by-convention target/policy snapshot; it must not widen on invocation. */
    val invocation: CapabilityInvocation
}
