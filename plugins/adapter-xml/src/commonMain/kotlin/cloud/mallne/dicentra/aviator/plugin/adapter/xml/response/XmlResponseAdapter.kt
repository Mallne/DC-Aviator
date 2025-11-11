package cloud.mallne.dicentra.aviator.plugin.adapter.xml.response

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.XmlAdapter.xml
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.request.XmlRequestAdapter
import io.ktor.http.*
import kotlinx.serialization.Serializable

internal object XmlResponseAdapter : ResponseBodyAdapter {
    override fun <O : @Serializable Any, B : @Serializable Any> deserialize(
        body: ByteArray,
        context: AviatorExecutionContext<O, B>
    ): O? {
        return try {
            val string = body.decodeToString()
            context.dataHolder.xml.decodeFromString(context.outputClazz.third, string)
        } catch (e: Exception) {
            context.log("JsonParse") {
                warn("Json could not be parsed to ${context.outputClazz.first}", e)
            }
            null
        }
    }

    override fun understands(contentType: ContentType?): Boolean = XmlRequestAdapter.understands(contentType)
}