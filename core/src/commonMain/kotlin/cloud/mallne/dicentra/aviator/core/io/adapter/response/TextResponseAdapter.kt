package cloud.mallne.dicentra.aviator.core.io.adapter.response

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.adapter.request.TextAdapter
import io.ktor.http.*
import kotlinx.serialization.Serializable

object TextResponseAdapter : ResponseBodyAdapter {
    override fun <O : @Serializable Any, B : @Serializable Any> deserialize(
        body: ByteArray,
        context: AviatorExecutionContext<O, B>
    ): O? {
        return body.decodeToString() as? O
    }

    override fun <O : @Serializable Any, B : @Serializable Any> understands(
        contentType: ContentType,
        context: AviatorExecutionContext<O, B>
    ): Boolean {
        return context.outputClazz.first == String::class && understands(contentType)
    }

    override fun understands(contentType: ContentType?): Boolean = TextAdapter.understands(contentType)
}