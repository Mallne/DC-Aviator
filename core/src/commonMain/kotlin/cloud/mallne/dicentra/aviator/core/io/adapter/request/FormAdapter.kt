package cloud.mallne.dicentra.aviator.core.io.adapter.request

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.koas.typed.Route
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object FormAdapter : RequestBodyAdapter<NetworkBody.Form> {
    override val target: KClass<NetworkBody.Form> = NetworkBody.Form::class
    override fun understands(contentType: ContentType?): Boolean {
        return contentType?.match(ContentType.MultiPart.FormData) == true || contentType?.match(ContentType.Application.FormUrlEncoded) == true
    }

    override fun <O : @Serializable Any, B : @Serializable Any> buildBody(
        from: B?,
        context: AviatorExecutionContext<O, B>
    ): NetworkBody.Form? {
        return buildBody(context)?.first
    }

    fun <O : @Serializable Any, B : @Serializable Any> buildBody(
        context: AviatorExecutionContext<O, B>
    ): Pair<NetworkBody.Form, ContentType>? {
        val route = context.dataHolder.route
        val formBody = route.body.filter { understands(it.key) }
        return if (formBody.isNotEmpty()) {
            formBody.mapNotNull {
                tryFormData(it.key, it.value, context.requestParams)
            }.firstOrNull()
        } else null
    }

    fun tryFormData(
        contentType: ContentType,
        body: Route.Bodies.Body,
        params: RequestParameters
    ): Pair<NetworkBody.Form, ContentType>? {
        if ((understands(contentType))) {
            val pairs = body.parameters.mapNotNull { (name, type) ->
                params[name]?.let {
                    name to it.toString()
                }
            }
            return NetworkBody.Form(pairs) to contentType
        } else {
            return null
        }
    }

    override fun <O : @Serializable Any, B : @Serializable Any> getRepresentation(
        body: NetworkBody.Form,
        context: AviatorExecutionContext<O, B>
    ): String {
        context.log("FormAdapter.getRepresentation") {
            trace("Somebody tried to get the String Representation for $body, for Formdata requests let the client handle the Data Properly.")
        }
        return body.formData.joinToString("&") { (string, string1) ->
            val key = string.encodeURLParameter()
            val value = string1.encodeURLParameter()
            "$key=$value"
        }
    }
}