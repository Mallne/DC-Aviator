package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.execution.LocatedParameterKey
import cloud.mallne.dicentra.aviator.core.execution.LocatedRequestParameters
import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.orchestration.execution.*
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocatedParametersBindingTest {
    @Test
    fun locatedQueryDoesNotLeakLegacySameNamedValueIntoHeader() = runTest {
        var query: String? = null
        var header: String? = null
        val client = HttpClient(MockEngine { request ->
            query = request.url.parameters["token"]
            header = request.headers["token"]
            respond(ByteReadChannel("{}"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec()) { install(JsonAdapter) }.single()
            val located = LocatedRequestParameters(mapOf(
                LocatedParameterKey("token", ParameterType.query) to RequestParameter.Single("query-value"),
            ))
            val legacy = RequestParameters(mutableMapOf("token" to RequestParameter.Single("legacy-value")))
            withContext(located) { service.request<JsonElement>(requestParams = legacy) }
            assertEquals("query-value", query)
            assertEquals(null, header)
        } finally {
            client.close()
        }
    }

    @Test
    fun pathQueryAndRepeatedHeadersKeepTheirValues() = runTest {
        var path: String? = null
        var query: List<String>? = null
        var headers: List<String>? = null
        var injected: String? = null
        val client = HttpClient(MockEngine { request ->
            path = request.url.encodedPath
            query = request.url.parameters.getAll("token")
            headers = request.headers.getAll("token")
            injected = request.url.parameters["injected"]
            respond(ByteReadChannel("{}"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec(includePath = true)) { install(JsonAdapter) }.single()
            val params = LocatedRequestParameters(mapOf(
                LocatedParameterKey("token", ParameterType.path) to RequestParameter.Single("path-value"),
                LocatedParameterKey("token", ParameterType.query) to RequestParameter.Multi(listOf("q1&injected=yes", "q2")),
                LocatedParameterKey("token", ParameterType.header) to RequestParameter.Multi(listOf("h1", "h2")),
            ))
            withContext(params) { service.request<JsonElement>() }
            assertEquals("/forecast/path-value", path)
            assertEquals(listOf("q1&injected=yes", "q2"), query)
            assertEquals(null, injected)
            assertEquals(listOf("h1", "h2"), headers)
            val compact = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec(includePath = true, explodeQuery = false)) { install(JsonAdapter) }.single()
            withContext(params) { compact.request<JsonElement>() }
            assertEquals(listOf("q1&injected=yes,q2"), query)
            assertEquals(null, injected)
            val reservedPath = LocatedRequestParameters(mapOf(
                LocatedParameterKey("token", ParameterType.path) to RequestParameter.Single("a/b?injected=yes"),
            ))
            withContext(reservedPath) { service.request<JsonElement>() }
            assertEquals("/forecast/a%2Fb%3Finjected=yes", path)
            assertEquals(null, injected)
        } finally {
            client.close()
        }
    }

    @Test
    fun sameNameQueryAndHeaderReachTheirOwnWireLocationsThroughNeutralBinding() = runTest {
        var query: String? = null
        var header: String? = null
        val client = HttpClient(MockEngine { request ->
            query = request.url.parameters["token"]
            header = request.headers["token"]
            respond(ByteReadChannel("{\"ok\":true}"), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec()) { install(JsonAdapter) }.single()
            val identity = ConstituentIdentity(service.serviceLocator, "provider", "source", "revision", "forecast")
            val binding = MaterializedOperationBinding(
                listOf(MaterializedConstituent<Unit>(identity) {
                    val parameters = LocatedRequestParameters(mapOf(
                        LocatedParameterKey("token", ParameterType.query) to RequestParameter.Single("query-value"),
                        LocatedParameterKey("token", ParameterType.header) to RequestParameter.Single("header-value"),
                    ))
                    withContext(parameters) { ConstituentExecution.decodedJson(service.request<JsonElement>()) }
                }), isUsable = { it is BodyValue.JsonValue }, aggregate = { it.single() },
            )
            val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("test"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
            )
            val result = binding.invoke(binding.resolve(CapabilityInvocation(identity.locator, policy)), Unit, scope)
            assertIs<CapabilityOutcome.Succeeded>(result.outcome)
            assertEquals("query-value", query)
            assertEquals("header-value", header)
        } finally {
            client.close()
        }
    }

    private fun spec(includePath: Boolean = false, explodeQuery: Boolean = true) = OpenApiDoc(
        info = OpenApiInfo(title = "test", version = "1"),
        servers = listOf(Server(url = "https://located.test")),
        paths = mapOf((if (includePath) "/forecast/{token}" else "/forecast") to ReferenceOr.Value(PathItem(get = Operation(
            operationId = "forecast",
            parameters = (listOf(ParameterType.query, ParameterType.header) + if (includePath) listOf(ParameterType.path) else emptyList()).map {
                ReferenceOr.Value(Parameter(name = "token", `in` = it, required = it == ParameterType.path,
                    explode = if (it == ParameterType.query) explodeQuery else null,
                    schema = ReferenceOr.Value(JsonSchema(type = JsonType.STRING))))
            },
            responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
            extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather>gather")),
        )))),
        extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
    )
}
