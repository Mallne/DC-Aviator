package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorSourceResponse
import cloud.mallne.dicentra.aviator.core.execution.AviatorSourceResponseObserver
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import cloud.mallne.dicentra.aviator.orchestration.execution.*
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SourceResponseObserverTest {
    @Test
    fun transportFailureIsRetainedWithoutInventingSourceResponse() = runTest {
        val client = HttpClient(MockEngine { throw IllegalStateException("controlled transport failure") })
        try {
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec()) { install(JsonAdapter) }.single()
            val identity = ConstituentIdentity(service.serviceLocator, "provider", "source", "revision", "getSource")
            val binding = MaterializedOperationBinding(
                listOf(MaterializedConstituent<Unit>(identity) {
                    ConstituentExecution.decodedJson(service.request<JsonElement>())
                }), isUsable = { true }, aggregate = { it.single() },
            )
            val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("test"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
            )
            val result = binding.invoke(binding.resolve(CapabilityInvocation(identity.locator, policy)), Unit, scope)
            assertIs<CapabilityOutcome.Failed>(result.outcome)
            assertEquals("constituent_failed", assertIs<ConstituentOutcome.Failed>(result.constituents.single().outcome).failure.code)
            assertEquals(null, result.constituents.single().response)
        } finally {
            client.close()
        }
    }

    @Test
    fun neutralBindingPreservesSourceWithoutByteReservations() = runTest {
        val body = "{\"source\":true}"
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel(body), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec()) { install(JsonAdapter) }.single()
            val identity = ConstituentIdentity(service.serviceLocator, "provider", "source", "revision", "getSource")
            val binding = MaterializedOperationBinding(
                listOf(MaterializedConstituent<Unit>(identity) {
                    val result = service.request<JsonElement>()
                    ConstituentExecution(ConstituentOutcome.Succeeded(BodyValue.JsonValue(checkNotNull(result))))
                }), isUsable = { true }, aggregate = { it.single() },
            )
            val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("test"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
            )
            val result = binding.invoke(binding.resolve(CapabilityInvocation(identity.locator, policy)), Unit, scope)
            assertIs<CapabilityOutcome.Succeeded>(result.outcome)
            assertEquals(BodyValue.BytesValue(body.encodeToByteArray().toList()), result.constituents.single().response?.body)
        } finally {
            client.close()
        }
    }

    @Test
    fun observerFailurePropagatesToCaller() = runTest {
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel("{\"source\":true}"), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec()) { install(JsonAdapter) }.single()
            var delivered = false
            val observer = object : AviatorSourceResponseObserver() {
                override suspend fun onResponses(responses: List<AviatorSourceResponse>) {
                    delivered = true
                    throw IllegalStateException("observer failed")
                }
            }
            withContext(observer) {
                assertFailsWith<IllegalStateException> { service.requestContextful<JsonElement, JsonElement>() }
            }
            assertEquals(true, delivered)
        } finally {
            client.close()
        }
    }

    @Test
    fun neutralBindingDistinguishesNonSuccessEmptyUndecodableAndJsonNullResponses() = runTest {
        val cases = listOf(
            Triple(HttpStatusCode.ServiceUnavailable, "unavailable", BodyValue.BytesValue("unavailable".encodeToByteArray().toList())),
            Triple(HttpStatusCode.Found, "{\"redirect\":true}", BodyValue.BytesValue("{\"redirect\":true}".encodeToByteArray().toList())),
            Triple(HttpStatusCode.NoContent, "", BodyValue.Empty),
            Triple(HttpStatusCode.OK, "{\"broken\":}", BodyValue.BytesValue("{\"broken\":}".encodeToByteArray().toList())),
            Triple(HttpStatusCode.OK, "null", BodyValue.BytesValue("null".encodeToByteArray().toList())),
        )
        for ((status, body, expectedBody) in cases) {
            val client = HttpClient(MockEngine {
                respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
            })
            try {
                val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                    .build(spec()) { install(JsonAdapter) }.single()
                val identity = ConstituentIdentity(service.serviceLocator, "provider", "source", "revision", "getSource")
                val binding = MaterializedOperationBinding(
                    listOf(MaterializedConstituent<Unit>(identity) {
                        val context = service.requestContextful<JsonElement, JsonElement>()
                        ConstituentExecution.decodedJson(context.result)
                    }), isUsable = { true }, aggregate = { it.single() },
                )
                val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                    PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
                val scope = EnforcedInvocationScope(
                    ExecutionMetadata(RunId("test"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
                )
                val result = binding.invoke(binding.resolve(CapabilityInvocation(identity.locator, policy)), Unit, scope)
                assertEquals(status.value, result.constituents.single().response?.http?.status)
                assertEquals(expectedBody, result.constituents.single().response?.body)
                val expectedOutcome = when (status) {
                    HttpStatusCode.ServiceUnavailable -> ConstituentOutcome.Failed(ExecutionFailure("http_error", "Source response status 503"))
                    HttpStatusCode.Found -> ConstituentOutcome.Failed(ExecutionFailure("http_error", "Source response status 302"))
                    HttpStatusCode.NoContent -> ConstituentOutcome.Succeeded(BodyValue.Empty)
                    else -> if (body == "null") ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonNull))
                        else ConstituentOutcome.Failed(ExecutionFailure("decode_failure", "Source response could not be decoded"))
                }
                assertEquals(expectedOutcome, result.constituents.single().outcome)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun neutralBindingRetainsSourceBytesSeparatelyFromTransformedPipelineResult() = runTest {
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel("{\"source\":true}"), HttpStatusCode.OK, headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                "X-Provider" to listOf("first", "second"),
            ))
        })
        try {
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec()) { install(JsonAdapter) }.single()
            val transformed = service.copy(plugins = service.plugins + TransformPlugin())
            val identity = ConstituentIdentity(service.serviceLocator, "provider", "source", "revision", "getSource")
            val binding = MaterializedOperationBinding(
                listOf(MaterializedConstituent<Unit>(identity) {
                    val context = transformed.requestContextful<JsonElement, JsonElement>()
                    ConstituentExecution.decodedJson(context.result, ResultRepresentation.TRANSFORMED)
                }),
                isUsable = { true }, aggregate = { it.single() },
            )
            val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("test"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
            )
            val result = binding.invoke(binding.resolve(CapabilityInvocation(identity.locator, policy)), Unit, scope)
            val constituent = result.constituents.single()
            assertEquals(ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive("transformed")), ResultRepresentation.TRANSFORMED), constituent.outcome)
            assertEquals(BodyValue.BytesValue("{\"source\":true}".encodeToByteArray().toList()), constituent.response?.body)
            assertEquals(listOf("first", "second"), constituent.response?.http?.headers?.get("X-Provider"))
            assertEquals(200, constituent.response?.http?.status)
        } finally {
            client.close()
        }
    }

    @Test
    fun orchestrationComposesWithInstalledAviatorPlugin() = runTest {
        val sourceBody = "{\"source\":true}"
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel(sourceBody), HttpStatusCode.OK, headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                "X-Provider" to listOf("real-source"),
            ))
        })
        try {
            val baseService = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec()) { install(JsonAdapter) }.single()
            val service = baseService.copy(plugins = baseService.plugins + ResponseTransformPlugin())
            val identity = ConstituentIdentity(service.serviceLocator, "provider", "source", "revision", "getSource")
            val binding = MaterializedOperationBinding(
                listOf(MaterializedConstituent<Unit>(identity) {
                    val context = service.requestContextful<JsonElement, JsonElement>()
                    ConstituentExecution.decodedJson(context.result, ResultRepresentation.TRANSFORMED)
                }),
                isUsable = { it is BodyValue.JsonValue }, aggregate = { it.single() },
            )
            val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("real-plugin-composition"), WorkflowIdentity("test", "1", "source", "revision")),
                ExecutionBudget(),
            )

            val result = binding.invoke(binding.resolve(CapabilityInvocation(identity.locator, policy)), Unit, scope)

            assertEquals(BodyValue.JsonValue(JsonPrimitive("operation-plugin-result")),
                assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
            val constituent = result.constituents.single()
            assertEquals(identity, constituent.identity)
            assertEquals(ResultRepresentation.TRANSFORMED,
                assertIs<ConstituentOutcome.Succeeded>(constituent.outcome).representation)
            assertEquals(BodyValue.BytesValue(sourceBody.encodeToByteArray().toList()), constituent.response?.body)
            assertEquals(200, constituent.response?.http?.status)
            assertEquals(listOf("real-source"), constituent.response?.http?.headers?.get("X-Provider"))
        } finally {
            client.close()
        }
    }

    @Test
    fun observerCopiesSourceBeforeResponsePluginTransformsResultAndBody() = runTest {
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel("{\"source\":true}"), HttpStatusCode.OK, headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                "X-Provider" to listOf("first", "second"),
            ))
        })
        try {
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec()) { install(JsonAdapter) }.single()
            val transformed = service.copy(plugins = service.plugins + TransformPlugin())
            val observer = RecordingObserver()
            val context = withContext(observer) { transformed.requestContextful<JsonElement, JsonElement>() }

            assertEquals(JsonPrimitive("transformed"), context.result)
            val source = observer.responses.single()
            assertEquals(200, source.status)
            assertEquals(listOf("first", "second"), source.headers["X-Provider"])
            assertEquals("{\"source\":true}", source.body?.decodeToString())
            assertEquals("{\"changed\":true}", context.networkChain.single().response?.content?.decodeToString())
        } finally {
            client.close()
        }
    }

    private class RecordingObserver : AviatorSourceResponseObserver() {
        var responses: List<AviatorSourceResponse> = emptyList()
        override suspend fun onResponses(responses: List<AviatorSourceResponse>) { this.responses = responses }
    }

    private class TransformPlugin : AviatorPluginInstance {
        override val identity = "test:transform"
        override val configurationBundle = object : AviatorPluginConfigScope {
            override var serviceFilter = mutableListOf<ServiceLocator>()
        }
        override val x = object : PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any> {
            override suspend fun afterRequesting(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
                context.networkChain.single().response?.content = "{\"changed\":true}".encodeToByteArray()
            }
            override suspend fun afterPaintingResponse(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
                context.result = JsonPrimitive("transformed")
            }
        }
    }

    private class ResponseTransformPlugin : AviatorPluginInstance {
        override val identity = "test:response-transform"
        override val configurationBundle = object : AviatorPluginConfigScope {
            override var serviceFilter = mutableListOf<ServiceLocator>()
        }
        override val x = object : PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any> {
            override suspend fun afterPaintingResponse(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
                context.result = JsonPrimitive("operation-plugin-result")
            }
        }
    }

    private fun spec() = OpenApiDoc(
        info = OpenApiInfo(title = "test", version = "1"),
        servers = listOf(Server(url = "https://source.test")),
        paths = mapOf("/" to ReferenceOr.Value(PathItem(get = Operation(
            operationId = "getSource",
            responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
            extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("source>gather")),
        )))),
        extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
    )
}
