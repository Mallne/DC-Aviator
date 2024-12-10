package cloud.mallne.dicentra.aviator.koas

import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.io.Schema
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.jvm.JvmInline

@Serializable(with = AdditionalProperties.Companion.Serializer::class)
sealed interface AdditionalProperties {
    @JvmInline
    value class Allowed(val value: Boolean) : AdditionalProperties

    @JvmInline
    value class PSchema(val value: ReferenceOr<Schema>) : AdditionalProperties

    companion object {
        internal object Serializer : KSerializer<AdditionalProperties> {
            override val descriptor =
                buildClassSerialDescriptor("cloud.mallne.dicentra.aviator.koas.AdditionalProperties")

            override fun deserialize(decoder: Decoder): AdditionalProperties {
                decoder as JsonDecoder
                val json = decoder.decodeSerializableValue(JsonElement.serializer())
                return when {
                    json is JsonPrimitive && json.booleanOrNull != null -> Allowed(json.boolean)
                    json is JsonObject ->
                        PSchema(
                            decoder.json.decodeFromJsonElement(
                                ReferenceOr.Companion.serializer(Schema.serializer()),
                                json
                            )
                        )

                    else ->
                        throw SerializationException("AdditionalProperties can only be a boolean or a schema")
                }
            }

            override fun serialize(encoder: Encoder, value: AdditionalProperties) {
                when (value) {
                    is Allowed -> encoder.encodeBoolean(value.value)
                    is PSchema ->
                        encoder.encodeSerializableValue(
                            ReferenceOr.Companion.serializer(Schema.serializer()),
                            value.value
                        )
                }
            }
        }
    }
}
