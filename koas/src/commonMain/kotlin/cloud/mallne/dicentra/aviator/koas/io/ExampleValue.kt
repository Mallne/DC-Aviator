package cloud.mallne.dicentra.aviator.koas.io

import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPISerializationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.JvmInline

typealias DefaultValue = ExampleValue

@Serializable(with = ExampleValue.Companion.Serializer::class)
sealed interface ExampleValue {

    @JvmInline
    value class Single(val value: String) : ExampleValue {
        override fun toString(): String = value
    }

    @JvmInline
    value class Multiple(val values: List<String>) : ExampleValue {
        override fun toString(): String = values.toString()
    }

    companion object {

        internal class Serializer : KSerializer<ExampleValue> {
            private val multipleSerializer = ListSerializer(String.Companion.serializer())

            override val descriptor: SerialDescriptor =
                buildClassSerialDescriptor("cloud.mallne.dicentra.aviator.koas.ExampleValueSerializer")

            override fun serialize(encoder: Encoder, value: ExampleValue) {
                when (value) {
                    is Single -> encoder.encodeString(value.value)
                    is Multiple -> encoder.encodeSerializableValue(multipleSerializer, value.values)
                }
            }

            override fun deserialize(decoder: Decoder): ExampleValue {
                return when (val json = decoder.decodeSerializableValue(JsonElement.Companion.serializer())) {
                    is JsonArray -> Multiple(decoder.decodeSerializableValue(multipleSerializer))
                    is JsonPrimitive -> Single(json.content)
                    else ->
                        throw OpenAPISerializationException(
                            "ExampleValue can only be a primitive or an array, found $json"
                        )
                }
            }
        }

        operator fun invoke(v: String): ExampleValue = Single(v)
    }
}