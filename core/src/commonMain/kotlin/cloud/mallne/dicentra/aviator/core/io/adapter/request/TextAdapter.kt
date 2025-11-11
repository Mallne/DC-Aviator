package cloud.mallne.dicentra.aviator.core.io.adapter.request

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object TextAdapter : RequestBodyAdapter<NetworkBody.Text> {
    override val target: KClass<NetworkBody.Text> = NetworkBody.Text::class
    override fun understands(contentType: ContentType?): Boolean {
        return contentType?.match(ContentType.Text.Plain) == true
    }

    override fun <O : @Serializable Any, B : @Serializable Any> buildBody(
        from: B?,
        context: AviatorExecutionContext<O, B>
    ): NetworkBody.Text {
        return NetworkBody.Text(from.toString())
    }

    override fun <O : @Serializable Any, B : @Serializable Any> getRepresentation(
        body: NetworkBody.Text,
        context: AviatorExecutionContext<O, B>
    ): String {
        return body.string
    }
}
