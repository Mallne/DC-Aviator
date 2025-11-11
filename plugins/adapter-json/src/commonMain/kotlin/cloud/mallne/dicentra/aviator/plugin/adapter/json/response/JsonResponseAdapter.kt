package cloud.mallne.dicentra.aviator.plugin.adapter.json.response

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter.json
import cloud.mallne.dicentra.aviator.plugin.adapter.json.request.JsonRequestAdapter
import io.ktor.http.*
import kotlinx.serialization.Serializable

internal object JsonResponseAdapter : ResponseBodyAdapter {
    override fun <O : @Serializable Any, B : @Serializable Any> deserialize(
        body: ByteArray,
        context: AviatorExecutionContext<O, B>
    ): O? {
        return try {
            val string = body.decodeToString()
            context.dataHolder.json.decodeFromString(context.outputClazz.third, string)
        } catch (e: Exception) {
            context.log("JsonParse") {
                warn("Json could not be parsed to ${context.outputClazz.first}", e)
            }
            null
        }
    }

    override fun understands(contentType: ContentType?): Boolean = JsonRequestAdapter.understands(contentType)
}