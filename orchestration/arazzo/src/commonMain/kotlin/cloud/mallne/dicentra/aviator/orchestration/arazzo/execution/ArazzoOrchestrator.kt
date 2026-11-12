package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.arazzo.ArazzoSupport
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.execution.OperationBinding
import cloud.mallne.dicentra.aviator.orchestration.execution.Orchestrator
import cloud.mallne.dicentra.aviator.orchestration.execution.OrchestratorBuilder
import cloud.mallne.dicentra.aviator.orchestration.execution.OrchestratorEnvironment
import cloud.mallne.dicentra.aviator.orchestration.execution.OrchestrationDsl
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid

/** Configure an Arazzo engine. The host owns workflow lookup, binding and authorization policy. */
fun OrchestratorBuilder.arazzo(configure: ArazzoOrchestratorBuilder.() -> Unit) {
    val config = ArazzoOrchestratorBuilder().apply(configure)
    engine { environment -> config.build(environment) }
}

@OrchestrationDsl
class ArazzoOrchestratorBuilder internal constructor() {
    /** Resolve an exact workflow identity; return null if it is not available. */
    var workflow: (suspend (WorkflowIdentity) -> ArazzoWorkflow?)? = null
    var binding: ((OrchestratorEnvironment) -> OperationBinding<JsonElement>)? = null
    var operationIndex: List<ConstituentIdentity> = emptyList()
    var policy: CapabilityPolicy? = null
    var sourceIdsByName: Map<String, String> = emptyMap()
    var json: Json = Json
    var newRunId: () -> RunId = { RunId(Uuid.random().toString()) }
    /** Required host authorization, rechecked at dispatch; return null to allow. */
    var authorize: (suspend (ConstituentIdentity) -> ExecutionFailure?)? = null

    internal fun build(environment: OrchestratorEnvironment): Orchestrator {
        val lookup = checkNotNull(workflow) { "Arazzo workflow lookup must be configured" }
        val bindingFactory = checkNotNull(binding) { "Arazzo operation binding must be configured" }
        val executionPolicy = checkNotNull(policy) { "Arazzo capability policy must be configured" }
        val authorization = checkNotNull(authorize) { "Arazzo host authorization must be configured" }
        val steps = ArazzoBindingStepExecutor(bindingFactory(environment), operationIndex.toList(), executionPolicy,
            sourceIdsByName.toMap(), json, environment.plugins)
        val runner = ArazzoRunExecutor(steps, plugins = environment.plugins, authorize = authorization)
        return ArazzoOrchestrator(runner, lookup, newRunId)
    }
}

private class ArazzoOrchestrator(
    private val runner: ArazzoRunExecutor,
    private val lookup: suspend (WorkflowIdentity) -> ArazzoWorkflow?,
    private val newRunId: () -> RunId,
) : Orchestrator {
    override val capabilities = OrchestratorCapabilities(
        formats = setOf(WorkflowFormatCapability("arazzo", setOf(ArazzoSupport.SPECIFICATION_VERSION))),
        executionModes = setOf(ExecutionMode.IN_PROCESS),
    )

    override fun checkCapabilities(requirements: CapabilityRequirements): CapabilityCheck = capabilities.check(requirements)

    override suspend fun execute(request: ExecutionRequest, budget: ExecutionBudget, events: ExecutionEventSink): ExecutionResult {
        val runId = newRunId()
        val check = checkCapabilities(CapabilityRequirements(request.workflow.format, request.workflow.formatVersion))
        if (check is CapabilityCheck.Unsupported) {
            return ExecutionResult(runId, request.workflow, ExecutionOutcome.Failed(
                ExecutionFailure("unsupported_workflow", check.reasons.joinToString("; "))))
        }
        val workflow = lookup(request.workflow) ?: return ExecutionResult(runId, request.workflow,
            ExecutionOutcome.Failed(ExecutionFailure("workflow_not_found", "Workflow not found")))
        return runner.execute(workflow, request.workflow, runId, request.inputs, request.metadata, budget, events)
    }
}
