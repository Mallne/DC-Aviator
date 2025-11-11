package cloud.mallne.dicentra.aviator.core.io.adapter.request

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

object NoBodyAdapter : RequestBodyAdapter<NetworkBody.Empty> {
    override val target: KClass<NetworkBody.Empty> = NetworkBody.Empty::class

    override fun understands(contentType: ContentType?): Boolean = contentType == null

    override fun <O : @Serializable Any, B : @Serializable Any> buildBody(
        from: B?,
        context: AviatorExecutionContext<O, B>
    ): NetworkBody.Empty = NetworkBody.Empty

    override fun <O : @Serializable Any, B : @Serializable Any> getRepresentation(
        body: NetworkBody.Empty,
        context: AviatorExecutionContext<O, B>
    ): String? = null

}