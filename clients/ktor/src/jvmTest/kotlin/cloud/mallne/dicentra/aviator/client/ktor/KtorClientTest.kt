package cloud.mallne.dicentra.aviator.client.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.openapi.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorClientTest {
    @Test
    fun converterExecutesRequestThroughConfiguredClient() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            respond(ByteReadChannel("hello"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain"))
        })
        try {
            val api = OpenApiDoc(info = OpenApiInfo(title = "test", version = "1"),
                servers = listOf(Server(url = "https://example.test")),
                paths = mapOf("/hello" to ReferenceOr.Value(PathItem(get = Operation(
                    operationId = "hello", responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "OK")))),
                    extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("test>hello")),
                )))), extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")))
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = emptyList()).build(api).single()
            assertEquals("hello", service.request<String>())
            assertEquals(listOf("https://example.test/hello"), urls)
        } finally {
            client.close()
        }
    }
}
