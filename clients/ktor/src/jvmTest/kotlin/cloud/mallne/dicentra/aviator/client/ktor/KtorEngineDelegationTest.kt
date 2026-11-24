package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.engine.AviatorEngine
import cloud.mallne.dicentra.aviator.core.engine.EngineDefinitionAuthorization
import cloud.mallne.dicentra.aviator.core.engine.EngineAuthorizationException
import cloud.mallne.dicentra.aviator.core.engine.EngineAuthorizationDecision
import cloud.mallne.dicentra.aviator.core.engine.OutboundTargetPolicy
import cloud.mallne.dicentra.aviator.core.engine.OutboundTargetDeniedException
import cloud.mallne.dicentra.aviator.core.engine.InMemoryDefinitionCoordinator
import cloud.mallne.dicentra.aviator.core.engine.InvocationMode
import cloud.mallne.dicentra.aviator.core.engine.NoMatchingDefinitionException
import cloud.mallne.dicentra.aviator.core.engine.OpenApiOperationDefinitionRequest
import cloud.mallne.dicentra.aviator.core.engine.OpenApiClientInput
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.openapi.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class KtorEngineDelegationTest {
    private val locator = ServiceLocator("workflow-action")
    @Serializable
    private data class TestResponse(val value: Int)

    @Test
    fun definitionConverterPreservesOperationPluginConfiguration() {
        val configuration = mapOf("configured" to kotlinx.serialization.json.JsonPrimitive(true))
        val client = HttpClient(MockEngine { error("No network request expected") })
        try {
            val api = api("https://example.test", configuration)
            val definition = KtorEngineDefinitionConverter(
                KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json),
            ).convert(
                OpenApiOperationDefinitionRequest(api, api.routes().single(), locator),
            )
            assertEquals(configuration, definition.pluginConfiguration)
        } finally {
            client.close()
        }
    }

    @Test
    fun actionInvokesPlainOpenApiOperationThroughClientPlugins() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            respond(ByteReadChannel("""{"value":42}"""), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val api = api("https://example.test")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorEngineDefinitionConverter(converter).convert(
                OpenApiOperationDefinitionRequest(api, api.routes().single(), locator) {
                    install(JsonAdapter)
                },
            )
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                { EngineAuthorizationDecision.Allowed },
                { _, _, _ -> null },
            )
            val context = engine.invoke(
                locator, ktorEngineTemplate<TestResponse>(), OpenApiClientInput(), InvocationMode.FIRST,
            ).single().result.getOrThrow()
            assertEquals(TestResponse(42), context.result)
            assertEquals(locator, context.dataHolder.serviceLocator)
            assertEquals(listOf("https://example.test/hello"), urls)
        } finally {
            client.close()
        }
    }

    @Test
    fun locatorWithoutDefinitionFailsBeforeTransport() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            error("No request should be sent without a matching definition")
        })
        try {
            val api = api("https://example.test")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorEngineDefinitionConverter(converter).convert(
                OpenApiOperationDefinitionRequest(api, api.routes().single(), locator) {
                    install(JsonAdapter)
                },
            )
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, _ -> null },
            )

            assertFailsWith<NoMatchingDefinitionException> {
                engine.invoke(
                    ServiceLocator("unknown-service"),
                    ktorEngineTemplate<TestResponse>(),
                    OpenApiClientInput(),
                    InvocationMode.FIRST,
                )
            }
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun unsuccessfulHttpResponseAllowsFirstNotFailingFallback() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            if (request.url.host == "first.test") {
                respond(ByteReadChannel("""{"error":"failed"}"""), HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(ByteReadChannel("""{"value":42}"""), HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })
        try {
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definitions = listOf("https://first.test", "https://second.test").map { base ->
                val api = api(base)
                KtorClientDefinition(converter.buildOperation(api, api.routes().single(), locator) { install(JsonAdapter) })
            }
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(definitions),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, _ -> null },
            )
            val results = engine.invoke(
                locator, ktorEngineTemplate<TestResponse>(), OpenApiClientInput(), InvocationMode.FIRST_NOT_FAILING,
            )
            assertEquals(2, results.size)
            val failure = assertIs<KtorClientInvocationException>(results.first().result.exceptionOrNull())
            assertEquals(500, failure.statusCode)
            assertTrue(results.last().result.isSuccess)
            assertEquals(listOf("https://first.test/hello", "https://second.test/hello"), urls)
        } finally {
            client.close()
        }
    }

    @Test
    fun firstModeDoesNotFallbackAfterTheFirstMatchingDefinitionFails() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            respond(
                ByteReadChannel("""{"error":"failed"}"""),
                HttpStatusCode.InternalServerError,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        try {
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definitions = listOf("https://first.test", "https://second.test").map { base ->
                val api = api(base)
                KtorClientDefinition(
                    converter.buildOperation(api, api.routes().single(), locator) { install(JsonAdapter) },
                )
            }
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(definitions),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, _ -> null },
            )

            val outcomes = engine.invoke(
                locator,
                ktorEngineTemplate<TestResponse>(),
                OpenApiClientInput(),
                InvocationMode.FIRST,
            )

            assertEquals(1, outcomes.size)
            val failure = assertIs<KtorClientInvocationException>(outcomes.single().result.exceptionOrNull())
            assertEquals(HttpStatusCode.InternalServerError.value, failure.statusCode)
            assertEquals(listOf("https://first.test/hello"), urls)
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationPropagatesWhileKtorRequestIsInFlight() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            requestStarted.complete(Unit)
            awaitCancellation()
        })
        try {
            val api = api("https://example.test")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorEngineDefinitionConverter(converter).convert(
                OpenApiOperationDefinitionRequest(api, api.routes().single(), locator) {
                    install(JsonAdapter)
                },
            )
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, _ -> null },
            )
            val invocation = async {
                engine.invoke(
                    locator,
                    ktorEngineTemplate<TestResponse>(),
                    OpenApiClientInput(),
                    InvocationMode.FIRST,
                )
            }

            requestStarted.await()
            invocation.cancel()
            assertFailsWith<CancellationException> {
                invocation.await()
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun missingAdapterIsAClientFailureRatherThanFalseSuccess() = runTest {
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel("""{"value":42}"""), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val api = api("https://example.test")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorClientDefinition(converter.buildOperation(api, api.routes().single(), locator))
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, _ -> null },
            )
            val outcome = engine.invoke(
                locator, ktorEngineTemplate<TestResponse>(), OpenApiClientInput(), InvocationMode.FIRST,
            ).single()
            assertIs<KtorClientInvocationException>(outcome.result.exceptionOrNull())
        } finally {
            client.close()
        }
    }

    @Test
    fun nonEmptySuccessfulResponseThatCannotDecodeIsAClientFailure() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                ByteReadChannel("""{"unexpected":42}"""),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        try {
            val api = api("https://example.test")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorEngineDefinitionConverter(converter).convert(
                OpenApiOperationDefinitionRequest(api, api.routes().single(), locator) {
                    install(JsonAdapter)
                },
            )
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, _ -> null },
            )

            val outcome = engine.invoke(
                locator,
                ktorEngineTemplate<TestResponse>(),
                OpenApiClientInput(),
                InvocationMode.FIRST,
            ).single()

            val failure = assertIs<KtorClientInvocationException>(outcome.result.exceptionOrNull())
            assertEquals(HttpStatusCode.OK.value, failure.statusCode)
            assertEquals("Could not decode client response", failure.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun operationFromAnotherDocumentIsRejected() {
        val client = HttpClient(MockEngine { error("No network request expected") })
        try {
            val first = api("https://first.test")
            val other = OpenApiDoc(
                info = OpenApiInfo(title = "other", version = "1"),
                servers = listOf(Server(url = "https://other.test")),
                paths = mapOf("/other" to ReferenceOr.Value(PathItem(get = Operation(
                    operationId = "other", responses = Responses(),
                )))),
            )
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            assertFailsWith<IllegalArgumentException> { converter.buildOperation(first, other.routes().single(), locator) }
        } finally {
            client.close()
        }
    }

    @Test
    fun outboundTargetPolicyDeniesBeforeTransport() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond(ByteReadChannel("""{"value":42}"""), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val api = api("http://untrusted.example")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorClientDefinition(converter.buildOperation(api, api.routes().single(), locator) {
                install(JsonAdapter)
            })
            var observedTargets: List<String>? = null
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, targets ->
                    observedTargets = targets
                    if (targets.any { it.startsWith("http://") }) "Insecure outbound scheme" else null
                },
            )

            val result = engine.invoke(
                locator, ktorEngineTemplate<TestResponse>(), OpenApiClientInput(), InvocationMode.FIRST,
            ).single().result

            assertIs<OutboundTargetDeniedException>(result.exceptionOrNull())
            assertEquals(listOf("http://untrusted.example"), observedTargets)
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun definitionAuthorizationDenialPreventsKtorTransport() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            error("A denied definition must not reach the transport")
        })
        try {
            val api = api("https://example.test")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorEngineDefinitionConverter(converter).convert(
                OpenApiOperationDefinitionRequest(api, api.routes().single(), locator) {
                    install(JsonAdapter)
                },
            )
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Denied("not permitted") },
                OutboundTargetPolicy { _, _, _ -> null },
            )

            val outcome = engine.invoke(
                locator,
                ktorEngineTemplate<TestResponse>(),
                OpenApiClientInput(),
                InvocationMode.FIRST,
            ).single()

            assertIs<EngineAuthorizationException>(outcome.result.exceptionOrNull())
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun redirectResponseIsNotFollowedByEngineClient() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requestedUrls += request.url.toString()
            respond(
                ByteReadChannel(""),
                HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://redirected.example/secret"),
            )
        })
        try {
            val api = api("https://allowed.example")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorClientDefinition(
                converter.buildOperation(api, api.routes().single(), locator) { install(JsonAdapter) },
            )
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, _ -> null },
            )

            val result = engine.invoke(
                locator, ktorEngineTemplate<TestResponse>(), OpenApiClientInput(), InvocationMode.FIRST,
            ).single().result

            val failure = assertIs<KtorClientInvocationException>(result.exceptionOrNull())
            assertEquals(302, failure.statusCode)
            assertEquals(listOf("https://allowed.example/hello"), requestedUrls)
        } finally {
            client.close()
        }
    }

    @Test
    fun successfulEmptyResponseRemainsASuccessWithNullResult() = runTest {
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel(""), HttpStatusCode.NoContent)
        })
        try {
            val api = api("https://empty.example")
            val converter = KtorAviatorServiceConverter(httpClient = client, json = kotlinx.serialization.json.Json)
            val definition = KtorClientDefinition(
                converter.buildOperation(api, api.routes().single(), locator) { install(JsonAdapter) },
            )
            val engine = AviatorEngine(
                InMemoryDefinitionCoordinator(listOf(definition)),
                EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
                OutboundTargetPolicy { _, _, _ -> null },
            )

            val context = engine.invoke(
                locator, ktorEngineTemplate<TestResponse>(), OpenApiClientInput(), InvocationMode.FIRST,
            ).single().result.getOrThrow()

            assertEquals(HttpStatusCode.NoContent.value, context.networkChain.last().response?.status?.value)
            assertEquals(null, context.result)
        } finally {
            client.close()
        }
    }

    private fun api(
        base: String,
        pluginConfiguration: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ) = OpenApiDoc(
        info = OpenApiInfo(title = "test", version = "1"),
        servers = listOf(Server(url = base)),
        paths = mapOf("/hello" to ReferenceOr.Value(PathItem(get = Operation(
            operationId = "hello",
            extensions = mapOf(
                cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.PluginMaterialization.O.key to
                    GenericElement(pluginConfiguration),
            ),
            responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "OK")))),
        )))),
    )
}
