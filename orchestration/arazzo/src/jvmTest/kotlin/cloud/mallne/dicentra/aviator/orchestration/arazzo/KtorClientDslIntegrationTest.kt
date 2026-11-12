package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.client.ktor.KtorAviatorService
import cloud.mallne.dicentra.aviator.client.ktor.KtorAviatorServiceConverter
import cloud.mallne.dicentra.aviator.orchestration.execution.ConstituentExecution
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test

class KtorClientDslIntegrationTest {
    @Test
    fun existingKtorConverterWorksWithDslAndPolicies() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(ByteReadChannel("{\"ok\":true}"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            exerciseClientDsl(KtorAviatorServiceConverter(httpClient = client, serializers = listOf(Json)),
                callCount = { calls }, aviatorPlugins = { install(JsonAdapter) }) { service, _ ->
                ConstituentExecution.decodedJson((service as KtorAviatorService).request<JsonElement>())
            }
        } finally {
            client.close()
        }
    }
}
