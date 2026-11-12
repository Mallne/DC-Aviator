package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoBindingStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoRunExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.execution.ConstituentExecution
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.MaterializedConstituent
import cloud.mallne.dicentra.aviator.orchestration.execution.MaterializedOperationBinding
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.openapi.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MaterializedArazzoPipelineTest {
    @Test
    fun standardOperationIdInvokesOnlyItsSameLocatorKtorConstituent() = runTest {
        val hosts = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            hosts += request.url.host
            respond(ByteReadChannel("{\"ok\":true}"), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val converter = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
            val a = converter.build(spec("forecast.test", "forecast")) { install(JsonAdapter) }.single()
            val b = converter.build(spec("weather.test", "weather")) { install(JsonAdapter) }.single()
            val locator = ServiceLocator("weather>gather")
            val first = ConstituentIdentity(locator, "provider-a", "source-a", "rev-a", "forecast")
            val second = ConstituentIdentity(locator, "provider-b", "source-b", "rev-b", "weather")
            val binding = MaterializedOperationBinding(
                listOf(first to a, second to b).map { (identity, service) ->
                    MaterializedConstituent<JsonElement>(identity) {
                        val result = checkNotNull(service.request<JsonElement>())
                        ConstituentExecution(ConstituentOutcome.Succeeded(BodyValue.JsonValue(result)))
                    }
                },
                isUsable = { it is BodyValue.JsonValue },
                aggregate = { values -> BodyValue.JsonValue(JsonArray(values.map { (it as BodyValue.JsonValue).value })) },
            )
            val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
            val runner = ArazzoRunExecutor(ArazzoBindingStepExecutor(binding, listOf(first, second), policy))
            val result = runner.execute(
                ArazzoWorkflow("flow", listOf(ArazzoStep("fetch", operationId = "forecast"))),
                WorkflowIdentity("arazzo", "1", "workflow", "revision"), RunId("exact"),
                budget = ExecutionBudget(maxCalls = 1),
            )
            assertIs<ExecutionOutcome.Succeeded>(result.outcome)
            assertEquals(listOf("forecast.test"), hosts)

            hosts.clear()
            val invocation = CapabilityInvocation(locator, policy)
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("aggregate"), WorkflowIdentity("test", "1", "workflow", "revision")),
                ExecutionBudget(maxCalls = 2),
            )
            val aggregate = binding.invoke(binding.resolve(invocation), kotlinx.serialization.json.JsonNull, scope)
            assertIs<CapabilityOutcome.Succeeded>(aggregate.outcome)
            assertEquals(listOf("forecast.test", "weather.test"), hosts)
            assertEquals(listOf(first, second), aggregate.constituents.map { it.identity })
            assertEquals(listOf("rev-a", "rev-b"), aggregate.constituents.map { it.identity.sourceRevision })
            assertEquals(listOf(200, 200), aggregate.constituents.map { it.response?.http?.status })
        } finally {
            client.close()
        }
    }

    private fun spec(host: String, operation: String) = OpenApiDoc(
        info = OpenApiInfo(title = "test", version = "1"),
        servers = listOf(Server(url = "https://$host")),
        paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = Operation(
            operationId = operation,
            responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
            extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather>gather")),
        )))),
        extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
    )
}
