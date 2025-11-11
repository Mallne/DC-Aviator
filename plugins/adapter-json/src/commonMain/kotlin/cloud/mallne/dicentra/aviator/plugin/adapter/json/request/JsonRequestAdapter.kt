package cloud.mallne.dicentra.aviator.plugin.adapter.json.request

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter.json
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonBody
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

internal object JsonRequestAdapter : RequestBodyAdapter<JsonBody> {
    override fun <O : @Serializable Any, B : @Serializable Any> buildBody(
        from: B?,
        context: AviatorExecutionContext<O, B>
    ): JsonBody? {
        return try {
            JsonBody(
                context.dataHolder.json.encodeToJsonElement(
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
        body: JsonBody,
        context: AviatorExecutionContext<O, B>
    ): String {
        return try {
            context.dataHolder.json.encodeToString(body.json)
        } catch (e: Exception) {
            context.log("JSONDecoding") {
                warn("Json Decoding failed", e)
            }
            "{}"
        }
    }

    override val target: KClass<JsonBody> = JsonBody::class

    override fun understands(contentType: ContentType?): Boolean =
        contentType?.match(ContentType.Application.Json) == true
}