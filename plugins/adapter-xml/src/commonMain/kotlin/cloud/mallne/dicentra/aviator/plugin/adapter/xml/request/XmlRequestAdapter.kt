package cloud.mallne.dicentra.aviator.plugin.adapter.xml.request

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.XmlAdapter.encodeToElement
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.XmlAdapter.xml
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.XmlBody
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.reflect.KClass

internal object XmlRequestAdapter : RequestBodyAdapter<XmlBody> {
    override fun <O : @Serializable Any, B : @Serializable Any> buildBody(
        from: B?,
        context: AviatorExecutionContext<O, B>
    ): XmlBody? {
        return try {
            XmlBody(
                context.dataHolder.xml.encodeToElement(
                    context.bodyClazz!!.third,
                    context.body!!
                )
            )
        } catch (e: Exception) {
            context.log("JSONEncoding") {
                warn("Json Encoding failed", e)
            }
            null
        }
    }

    override fun <O : @Serializable Any, B : @Serializable Any> getRepresentation(
        body: XmlBody,
        context: AviatorExecutionContext<O, B>
    ): String {
        return try {
            context.dataHolder.xml.encodeToString(body.xml)
        } catch (e: Exception) {
            context.log("JSONDecoding") {
                warn("Json Decoding failed", e)
            }
            "{}"
        }
    }

    override val target: KClass<XmlBody> = XmlBody::class

    override fun understands(contentType: ContentType?): Boolean =
        contentType?.match(ContentType.Application.Xml) == true || contentType?.match(ContentType.Text.Xml) == true
}