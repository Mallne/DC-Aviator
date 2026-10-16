package cloud.mallne.dicentra.aviator.plugin.adapter.xml.response

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.XmlAdapter.xml
import cloud.mallne.dicentra.aviator.plugin.adapter.xml.request.XmlRequestAdapter
import io.ktor.http.*
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.charsets.encode
import io.ktor.utils.io.charsets.forName
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal object XmlResponseAdapter : ResponseBodyAdapter {
    override fun <O : @Serializable Any, B : @Serializable Any> deserialize(
        body: ByteArray,
        context: AviatorExecutionContext<O, B>
    ): O? {
        return try {
            val contentType = context.networkChain.lastOrNull()?.response?.contentType
            val string = decodeXml(body, contentType)
            if (context.outputClazz.first == JsonElement::class) {
                // Generic gateway callers have no provider-specific XML model. Preserve the wire
                // document as a JSON string; provider plugins can still replace this result later.
                @Suppress("UNCHECKED_CAST")
                JsonPrimitive(string) as O
            } else {
                context.dataHolder.xml.decodeFromString(context.outputClazz.third, string)
            }
        } catch (e: Exception) {
            context.log("XMLParse") {
                warn("XML could not be parsed to ${context.outputClazz.first}", e)
            }
            null
        }
    }

    override fun understands(contentType: ContentType?): Boolean = XmlRequestAdapter.understands(contentType)

    internal fun decodeXml(body: ByteArray, contentType: ContentType?): String {
        // xmlutil accepts character input, not bytes. Use only the HTTP charset (UTF-8 by
        // default) for this boundary conversion; XML parsing and typed deserialization remain
        // the responsibility of the kotlinx.serialization XML plugin above.
        val charset = contentType?.parameter("charset")?.let(Charsets::forName) ?: Charsets.UTF_8
        val source = Buffer().apply { write(body) }
        val decoded = charset.newDecoder().decode(source)
        val encoded = charset.newEncoder().encode(decoded).readByteArray()
        require(body.contentEquals(encoded)) { "XML response contains malformed or non-round-trippable text" }
        return decoded
    }
}
