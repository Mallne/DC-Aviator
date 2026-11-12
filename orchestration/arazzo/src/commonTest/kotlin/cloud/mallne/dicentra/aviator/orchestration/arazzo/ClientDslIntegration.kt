package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.execution.logging.LoggingPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.arazzo
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.execution.*
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginExecutor
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.plugin.orchestration.authorization.constituentAuthorizationPlugin
import cloud.mallne.dicentra.aviator.plugin.orchestration.budget.executionBudgetPolicyPlugin
import io.ktor.openapi.*
import kotlinx.serialization.json.JsonElement
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal fun clientSpec(operationId: String = "forecast") = OpenApiDoc(
    info = OpenApiInfo(title = "test", version = "1"),
    servers = listOf(Server(url = "https://weather.test")),
    paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = Operation(
        operationId = operationId, responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "OK")))),
        extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather>get"),
            "x-dicentra-aviator-serviceOptions" to GenericElement("{}")),
    )))),
    extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
)

/** Exercises the same DSL, policy and transport boundaries against each client implementation. */
internal suspend fun exerciseClientDsl(
    converter: APIToServiceConverter,
    callCount: () -> Int,
    aviatorPlugins: AviatorPluginActivationScope.() -> Unit = {},
    invoke: suspend (AviatorServiceDataHolder, JsonElement) -> ConstituentExecution,
) {
    val constituent = ConstituentIdentity(ServiceLocator("weather>get"), "provider", "source", "rev", "forecast")
    val workflow = WorkflowIdentity("arazzo", "1.1.0", "doc", "rev")
    var hostAllows = true
    var pluginAllows = true
    var constituentFailure: String? = null
    val engine = orchestrator {
        aviator(converter, clientSpec()) {
            install(LoggingPlugin) { silentLoggingTags += "fixture" }
            aviatorPlugins(this)
        }
        plugins {
            install(executionBudgetPolicyPlugin())
            install(constituentAuthorizationPlugin("authorization") {
                if (pluginAllows) null else ExecutionFailure("plugin_denied", "Denied")
            })
            install("capture-failure") {
                stages += OrchestrationStage.HANDLE_RESULT
                executor = OrchestrationPluginExecutor { context ->
                    constituentFailure = (context.result?.constituents?.singleOrNull()?.outcome as? ConstituentOutcome.Failed)?.failure?.code
                }
            }
        }
        arazzo {
            this.workflow = { ArazzoWorkflow("flow", listOf(ArazzoStep("fetch", operationId = "forecast"))) }
            operationIndex = listOf(constituent)
            policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
            authorize = { if (hostAllows) null else ExecutionFailure("host_denied", "Denied") }
            binding = { env ->
                val service = env.services.single()
                assertTrue(service.plugins.any { it.identity == LoggingPlugin.identity && "fixture" in it.configurationBundle.silentLoggingTags })
                MaterializedOperationBinding(
                    listOf(MaterializedConstituent<JsonElement>(constituent) { invoke(service, it) }),
                    isUsable = { true }, aggregate = { it.single() }, plugins = env.plugins,
                )
            }
        }
    }
    assertIs<ExecutionOutcome.Succeeded>(engine.execute(ExecutionRequest(workflow), ExecutionBudget(maxCalls = 1)).outcome)
    assertEquals(1, callCount())
    assertIs<ExecutionOutcome.Failed>(engine.execute(ExecutionRequest(workflow), ExecutionBudget(maxCalls = 0)).outcome)
    assertEquals("calls", constituentFailure)
    assertEquals(1, callCount(), "Budget rejection must occur before transport")
    pluginAllows = false
    assertIs<ExecutionOutcome.Failed>(engine.execute(ExecutionRequest(workflow)).outcome)
    assertEquals("plugin_denied", constituentFailure)
    assertEquals(1, callCount(), "Plugin denial must occur before transport")
    pluginAllows = true
    hostAllows = false
    val denied = assertIs<ExecutionOutcome.Failed>(engine.execute(ExecutionRequest(workflow)).outcome)
    assertEquals("host_denied", denied.failure.code)
    assertEquals(1, callCount(), "Host denial must occur before transport")
}
