package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import cloud.mallne.dicentra.aviator.core.execution.AviatorCallControl
import cloud.mallne.dicentra.aviator.core.engine.AviatorEngine
import cloud.mallne.dicentra.aviator.core.engine.InMemoryDefinitionCoordinator
import cloud.mallne.dicentra.aviator.core.engine.EngineDefinitionAuthorization
import cloud.mallne.dicentra.aviator.core.engine.EngineAuthorizationDecision
import cloud.mallne.dicentra.aviator.core.engine.OutboundTargetPolicy
import cloud.mallne.dicentra.aviator.core.engine.InvocationMode
import cloud.mallne.dicentra.aviator.core.engine.OpenApiOperationDefinitionRequest
import cloud.mallne.dicentra.aviator.core.engine.OpenApiClientInput
import cloud.mallne.dicentra.aviator.core.plugins.DefaultAviatorPluginRegistry
import cloud.mallne.dicentra.aviator.core.plugins.ClientPluginRegistration
import cloud.mallne.dicentra.aviator.core.plugins.PluginActivation
import cloud.mallne.dicentra.aviator.core.plugins.aviatorPluginRegistry
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.koas.typed.routes
import io.ktor.openapi.GenericElement
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.openapi.PathItem
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.Response
import io.ktor.openapi.Responses
import io.ktor.openapi.Server
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FetchAviatorServiceTest {
    @Test
    fun adapterPluginsCannotBeDefinitionActivated() {
        assertFailsWith<IllegalArgumentException> {
            aviatorPluginRegistry { clientPlugin(JsonAdapter, PluginActivation.DEFINITION) }
        }
        assertFailsWith<IllegalArgumentException> {
            aviatorPluginRegistry { plugin(JsonAdapter, PluginActivation.DEFINITION) }
        }
    }

    @Test
    fun cancellingResponseBodyReadAbortsSignal() = runTest {
        var signal: dynamic = null
        val reading = CompletableDeferred<Unit>()
        val fetcher: dynamic = { _: String, init: dynamic ->
            signal = init.signal
            val response: dynamic = js("({})")
            response.arrayBuffer = {
                reading.complete(Unit)
                js("new Promise(() => {})")
            }
            js("Promise.resolve(response)")
        }
        val job = launch { service(fetcher).request<WeatherResponse>() }
        reading.await()
        job.cancelAndJoin()
        assertTrue(signal.aborted as Boolean)
    }

    @Test
    fun transportControlCanRejectBeforeFetch() = runTest {
        var calls = 0
        val fetcher: dynamic = { _: String, _: dynamic ->
            calls++
            responsePromise("{}")
        }
        val control = object : AviatorCallControl() {
            override suspend fun <T> execute(retry: Boolean, call: suspend () -> T): T = error("rejected")
        }
        assertFailsWith<IllegalStateException> {
            withContext(control) { service(fetcher).request<WeatherResponse>() }
        }
        assertEquals(0, calls)
    }

    @Test
    fun fetchRedirectPolicyIsFailClosed() = runTest {
        var redirectPolicy: String? = null
        val fetcher: dynamic = { _: String, init: dynamic ->
            redirectPolicy = init.redirect as String
            responsePromise("{}")
        }
        service(fetcher).request<WeatherResponse>()
        assertEquals("error", redirectPolicy)
    }

    @Test
    fun injectedFetcherReceivesResolvedRequestAndItsResponseIsDeserialized() = runTest {
        var requestedUrl: String? = null
        var requestedMethod: String? = null
        val fetcher: dynamic = { url: String, init: dynamic ->
            requestedUrl = url
            requestedMethod = init.method as String
            responsePromise("""{"temperature":18.5}""")
        }

        val result = service(fetcher).request<WeatherResponse>()

        assertEquals("https://weather.test/forecast", requestedUrl)
        assertEquals("GET", requestedMethod)
        assertEquals(18.5, result?.temperature)
    }

    @Test
    fun unsuccessfulHttpResponseReturnsNull() = runTest {
        val fetcher: dynamic = { _: String, _: dynamic -> responsePromise("{}", status = 503) }

        val result = service(fetcher).request<WeatherResponse>()

        assertNull(result)
    }

    @Test
    fun documentConverterReadsLocatorAndPluginConfiguration() {
        val configuration = mapOf("configured" to kotlinx.serialization.json.JsonPrimitive(true))
        val spec = specification("https://engine.test", configuration, locator = "weather>forecast")
        val definition = FetchEngineDefinitionConverter(FetchAviatorServiceConverter()).convert(spec).single()
        assertEquals(ServiceLocator("weather>forecast"), definition.locator)
        assertEquals(configuration, definition.pluginConfiguration)
    }

    @Test
    fun engineDispatchesToFetchClientForPlainOpenApiOperation() = runTest {
        var requestedUrl: String? = null
        val fetcher: dynamic = { url: String, _: dynamic ->
            requestedUrl = url
            responsePromise("""{"temperature":18.5}""")
        }
        val spec = specification("https://engine.test")
        val locator = ServiceLocator("workflow-action")
        val definition = FetchEngineDefinitionConverter(FetchAviatorServiceConverter(fetcher = fetcher)).convert(
            OpenApiOperationDefinitionRequest(spec, spec.routes().single(), locator),
        )
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(definition)),
            EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
            OutboundTargetPolicy { _, _, _ -> null },
            aviatorPluginRegistry { clientPlugin(JsonAdapter) },
        )

        val outcome = engine.invoke<WeatherResponse>(locator).single().result.getOrThrow()

        val context = outcome
        assertEquals(WeatherResponse(18.5), context.result)
        assertEquals("https://engine.test/forecast", requestedUrl)
    }

    @Test
    fun adapterRemainsPassivelyInstalledWhenDefinitionRequestsIt() = runTest {
        var requestedUrl: String? = null
        val fetcher: dynamic = { url: String, _: dynamic ->
            requestedUrl = url
            responsePromise("""{"temperature":18.5}""")
        }
        val spec = specification("https://activated.test")
        val locator = ServiceLocator("workflow-action")
        val service = FetchAviatorServiceConverter(fetcher = fetcher).buildOperation(
            spec, spec.routes().single(), locator,
        )
        val definition = FetchClientDefinition(
            service,
            pluginConfiguration = mapOf(JsonAdapter.identity to kotlinx.serialization.json.JsonObject(emptyMap())),
        )
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(definition)),
            EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
            OutboundTargetPolicy { _, _, _ -> null },
            DefaultAviatorPluginRegistry(
                clients = listOf(ClientPluginRegistration(JsonAdapter.install {}, PluginActivation.PASSIVE)),
            ),
        )

        val result = engine.invoke(
            locator, fetchEngineTemplate<WeatherResponse>(), OpenApiClientInput(), InvocationMode.FIRST,
        ).single().result.getOrThrow()

        assertTrue(result.networkChain.isNotEmpty())
        assertEquals("https://activated.test/forecast", requestedUrl)
    }

    @Test
    fun engineFirstNotFailingFallsBackAfterFetchHttpFailure() = runTest {
        val requestedUrls = mutableListOf<String>()
        val services = listOf("https://first.test", "https://second.test").mapIndexed { index, server ->
            val fetcher: dynamic = { url: String, _: dynamic ->
                requestedUrls += url
                responsePromise("""{"temperature":18.5}""", status = if (index == 0) 503 else 200)
            }
            val spec = specification(server)
            val locator = ServiceLocator("shared")
            FetchClientDefinition(
                FetchAviatorServiceConverter(fetcher = fetcher).buildOperation(spec, spec.routes().single(), locator) {
                    install(JsonAdapter)
                },
            )
        }
        val locator = ServiceLocator("shared")
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(services),
            { EngineAuthorizationDecision.Allowed },
            { _, _, _ -> null },
        )

        val results = engine.invoke(
            locator, fetchEngineTemplate<WeatherResponse>(), OpenApiClientInput(), InvocationMode.FIRST_NOT_FAILING,
        )

        assertEquals(2, results.size)
        assertTrue(results.first().result.isFailure)
        assertTrue(results.last().result.isSuccess)
        assertEquals(listOf("https://first.test/forecast", "https://second.test/forecast"), requestedUrls)
    }

    @Test
    fun responseLargerThanConfiguredLimitFails() = runTest {
        val fetcher: dynamic = { _: String, _: dynamic -> responsePromise("123456") }

        assertFailsWith<IllegalStateException> {
            service(fetcher, maxResponseBytes = 5).request<WeatherResponse>()
        }
    }

    private fun service(
        fetcher: dynamic,
        maxResponseBytes: Int = 16_777_216,
        servers: List<String> = listOf("https://weather.test"),
    ): FetchAviatorService {
        val operation = Operation(
            operationId = "getForecast",
            responses = Responses(
                responses = mapOf(200 to ReferenceOr.Value(Response(description = "Weather response")))
            ),
            extensions = mapOf(
                "x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather-provider")
            ),
        )
        val specification = OpenApiDoc(
            info = OpenApiInfo(title = "Fetch client test", version = "1.0.0"),
            servers = servers.map(::Server),
            paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = operation))),
            extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
        )

        return FetchAviatorServiceConverter(
            fetcher = fetcher,
            maxResponseBytes = maxResponseBytes,
        ).build(specification) {
            install(JsonAdapter)
        }.single() as FetchAviatorService
    }

    private fun specification(
        server: String,
        pluginConfiguration: Map<String, JsonElement> = emptyMap(),
        locator: String? = null,
    ) = OpenApiDoc(
        info = OpenApiInfo(title = "Plain Fetch operation", version = "1"),
        servers = listOf(Server(server)),
        paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = Operation(
            operationId = "getForecast",
            extensions = buildMap {
                put("x-dicentra-aviator-pluginMaterialization", GenericElement(pluginConfiguration))
                if (locator != null) put("x-dicentra-aviator-serviceDelegateCall", GenericElement(locator))
            },
            responses = Responses(
                responses = mapOf(200 to ReferenceOr.Value(Response(description = "Weather response")))
            ),
        )))),
        extensions = if (locator == null) emptyMap() else mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
    )

    private fun responsePromise(body: String, status: Int = 200): dynamic {
        val response: dynamic = js(
            "new Response(body, { status: status, headers: { 'content-type': 'application/json' } })"
        )
        return js("Promise.resolve(response)")
    }

    @Serializable
    private data class WeatherResponse(val temperature: Double)
}
