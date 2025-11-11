package cloud.mallne.dicentra.aviator.core.io.adapter.response

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.adapter.Understandable
import io.ktor.http.*
import kotlinx.serialization.Serializable

interface ResponseBodyAdapter : Understandable {
    fun <O : @Serializable Any, B : @Serializable Any> deserialize(
        body: ByteArray,
        context: AviatorExecutionContext<O, B>
    ): O?

    fun <O : @Serializable Any, B : @Serializable Any> understands(
        contentType: ContentType,
        context: AviatorExecutionContext<O, B>
    ): Boolean = understands(contentType)
}