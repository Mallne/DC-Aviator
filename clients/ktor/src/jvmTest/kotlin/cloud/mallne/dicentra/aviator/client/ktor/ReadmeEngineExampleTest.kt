package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.core.engine.*
import cloud.mallne.dicentra.aviator.core.plugins.*
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.openapi.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises the README's included-engine setup through the actual client pipeline. */
class ReadmeEngineExampleTest {
    @Serializable
    private data class Weather(val temperature: Double)

    @Test
    fun includedEngineExecutesReadmeWeatherExample() = runTest {
        val requests = mutableListOf<String>()
        val transport = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond(
                """{"temperature":18.5}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val converter = KtorAviatorServiceConverter(httpClient = transport, json = kotlinx.serialization.json.Json)
        try {
            val api = OpenApiDoc(
                info = OpenApiInfo(title = "Weather", version = "1"),
                servers = listOf(Server(url = "https://weather.example")),
                paths = mapOf(
                    "/weather" to ReferenceOr.Value(PathItem(get = Operation(
                        responses = Responses(),
                        extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather>forecast")),
                    ))),
                    "/unmarked" to ReferenceOr.Value(PathItem(get = Operation(responses = Responses()))),
                ),
                extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
            )
            val engine = weatherEngine(api, converter, EngineDefinitionAuthorization.AllowAll)

            assertEquals(Weather(18.5), readWeather(engine))
            assertEquals(Weather(18.5), readWeather(engine))
            assertEquals(List(2) { "https://weather.example/weather" }, requests)
        } finally {
            converter.httpClient.close()
            transport.close()
        }
    }

    private fun weatherEngine(
        api: OpenApiDoc,
        converter: KtorAviatorServiceConverter,
        authorization: EngineDefinitionAuthorization,
    ): AviatorEngine {
        val definitionConverter: EngineDefinitionConverter<KtorClientDefinition> = KtorEngineDefinitionConverter(converter)
        val definitions = definitionConverter.convert(api)
        assertEquals(ServiceLocator("weather>forecast"), definitions.single().locator)
        return AviatorEngine(
            coordinator = InMemoryDefinitionCoordinator(definitions),
            authorization = authorization,
            outboundTargetPolicy = OutboundTargetPolicy { _, _, targets ->
                if (targets.isNotEmpty() && targets.all { it == "https://weather.example" }) null
                else "Unexpected weather destination"
            },
            plugins = aviatorPluginRegistry {
                plugin(JsonAdapter)
            },
        )
    }

    private suspend fun readWeather(engine: AviatorEngine): Weather {
        val context = engine.invoke<Weather>(ServiceLocator("weather>forecast")).single().result.getOrThrow()
        return checkNotNull(context.result)
    }
}
