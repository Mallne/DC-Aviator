package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder.Companion.json
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

class MockedStagedExecutor :
    StagedExecutor<MockExecutionContext, JsonElement, NoBody> {

    override suspend fun onPathMatching(context: MockExecutionContext) {
        val l = AviatorServiceUtils.catchPaths(context.dataHolder, context.requestParams).map {
            NetworkChain<MockedRequest, MockedResponse, MockedHeader>(it)
        }
        context.networkChain.addAll(l)
    }

    override suspend fun onFormingRequest(context: MockExecutionContext) {
        val route = context.dataHolder.route
        context.networkChain.forEach { net ->
            net.request = MockedRequest(
                method = route.method,
                url = Url(net.url),
                outgoingContent = NetworkBody.Empty
            )
        }
    }

    override suspend fun onRequesting(context: MockExecutionContext) {
        val chain = context.networkChain.filter { it.request != null && it.response == null }
        AviatorServiceUtils.manualPipeline(chain) { net, next ->
            val response = MockedResponse(
                content = context.dataHolder.json.encodeToString(
                    serializer(),
                    context.dataHolder.route.nested
                ).toByteArray(),
                status = HttpStatusCode.OK,
                time = GMTDate(),
                contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)
            )
            net.response = response
        }
    }

    override suspend fun onPaintingResponse(context: MockExecutionContext) {
        val successful = context.networkChain.find { (it.response?.status?.value ?: 500) < 400 }
        context.result = try {
            val deserializer = successful?.response?.let { context.deserializerFor(it.contentType) }
            successful?.response?.content?.let { deserializer?.deserialize(it, context) }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}