package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.NetworkRequest
import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

class MockedStagedExecutor :
    StagedExecutor<MockExecutionContext, JsonElement, NoBody> {

    override suspend fun onPathMatching(context: MockExecutionContext) {
        val l = AviatorServiceUtils.catchPaths(context.dataHolder, context.requestParams).map {
            NetworkChain<NetworkRequest, NetworkResponse>(it)
        }
        context.networkChain.addAll(l)
    }

    override suspend fun onFormingRequest(context: MockExecutionContext) {
        val route = context.dataHolder.route
        context.networkChain.forEach { net ->
            net.request = object : NetworkRequest {
                override val method: HttpMethod = route.method
                override val url: Url = Url(net.url)
                override val headers: NetworkHeader = object : NetworkHeader {
                    override val values: Map<String, List<String>> = emptyMap()
                }
                override val outgoingContent: JsonElement? = if (context.body != null && context.bodyClazz != null) {
                    context.dataHolder.json.encodeToJsonElement(context.bodyClazz!!.third, context.body!!)
                } else {
                    null
                }
            }
        }
    }

    override suspend fun onRequesting(context: MockExecutionContext) {
        val chain = context.networkChain.filter { it.request != null && it.response == null }
        AviatorServiceUtils.manualPipeline(chain) { net, next ->
            val response = object : NetworkResponse {
                override val content: JsonElement? =
                    context.dataHolder.json.encodeToJsonElement(serializer(), context.dataHolder.route.nested)
                override val headers: NetworkHeader = object : NetworkHeader {
                    override val values: Map<String, List<String>> = emptyMap()
                }
                override val status: HttpStatusCode = HttpStatusCode.OK
                override val time: GMTDate = GMTDate()
            }
            net.response = response
        }
    }

    override suspend fun onPaintingResponse(context: MockExecutionContext) {
        val successful = context.networkChain.find { (it.response?.status?.value ?: 500) < 400 }
        context.result = try {
            successful?.response?.parseBody(context.outputClazz.third, context.dataHolder.json)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}