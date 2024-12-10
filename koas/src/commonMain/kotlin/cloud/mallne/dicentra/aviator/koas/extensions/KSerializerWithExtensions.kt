package cloud.mallne.dicentra.aviator.koas.extensions

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

internal abstract class KSerializerWithExtensions<T : Extendable>(
    private val serializer: KSerializer<T>,
    private val extensions: (T) -> Map<String, JsonElement>,
    private val withExtensions: (T, Map<String, JsonElement>) -> T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): T {
        decoder as JsonDecoder
        val jsObject = decoder.decodeSerializableValue(JsonElement.Companion.serializer())
        val value =
            decoder.json.decodeFromJsonElement(
                serializer,
                JsonObject(jsObject.jsonObject.filterNot { (key, _) -> key.startsWith("x-") })
            )
        val extensions = jsObject.jsonObject.filter { (key, _) -> key.startsWith("x-") }
        return withExtensions(value, extensions)
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder as JsonEncoder
        val jsObject = encoder.json.encodeToJsonElement(serializer, value).jsonObject - "extensions"
        encoder.encodeSerializableValue(
            JsonElement.Companion.serializer(),
            JsonObject(jsObject + extensions(value))
        )
    }
}