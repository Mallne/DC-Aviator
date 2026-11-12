package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.client.fetch.FetchAviatorService
import cloud.mallne.dicentra.aviator.client.fetch.FetchAviatorServiceConverter
import cloud.mallne.dicentra.aviator.orchestration.execution.ConstituentExecution
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test

class FetchClientDslIntegrationTest {
    @Test
    fun existingFetchConverterWorksWithDslAndPolicies() = runTest {
        var calls = 0
        val fetcher: dynamic = { _: String, _: dynamic ->
            calls++
            js("Promise.resolve(new Response('{\"ok\":true}', {status: 200, headers: {'content-type': 'application/json'}}))")
        }
        exerciseClientDsl(FetchAviatorServiceConverter(fetcher = fetcher), callCount = { calls },
            aviatorPlugins = { install(JsonAdapter) }) { service, _ ->
            ConstituentExecution.decodedJson((service as FetchAviatorService).request<JsonElement>())
        }
    }
}
