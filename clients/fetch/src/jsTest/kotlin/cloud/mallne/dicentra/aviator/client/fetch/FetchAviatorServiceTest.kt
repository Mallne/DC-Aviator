package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
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
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FetchAviatorServiceTest {
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
    fun responseLargerThanConfiguredLimitFails() = runTest {
        val fetcher: dynamic = { _: String, _: dynamic -> responsePromise("123456") }

        assertFailsWith<IllegalStateException> {
            service(fetcher, maxResponseBytes = 5).request<WeatherResponse>()
        }
    }

    private fun service(fetcher: dynamic, maxResponseBytes: Int = 16_777_216): FetchAviatorService {
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
            servers = listOf(Server(url = "https://weather.test")),
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

    private fun responsePromise(body: String, status: Int = 200): dynamic {
        val response: dynamic = js(
            "new Response(body, { status: status, headers: { 'content-type': 'application/json' } })"
        )
        return js("Promise.resolve(response)")
    }

    @Serializable
    private data class WeatherResponse(val temperature: Double)
}
