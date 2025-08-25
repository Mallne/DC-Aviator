package cloud.mallne.dicentra.aviator.koas.extensions

import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr.Reference
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.jvm.JvmInline

private const val RefKey = "\$ref"

/**
 * Defines Union [A] | [Reference]. A lot of types like Header, Schema, MediaType, etc. can be
 * either a direct value or a reference to a definition.
 */
@Serializable(with = ReferenceOr.Companion.Serializer::class)
sealed interface ReferenceOr<out A> {
    @Serializable
    data class Reference(@SerialName(RefKey) val ref: String) : ReferenceOr<Nothing>

    @JvmInline
    value class Value<A>(val value: A) : ReferenceOr<A>

    fun valueOrNull(): A? =
        when (this) {
            is Reference -> null
            is Value -> value
        }

    companion object {
        private const val schema: String = "#/components/schemas/"
        private const val responses: String = "#/components/responses/"
        private const val parameters: String = "#/components/parameters/"
        private const val requestBodies: String = "#/components/requestBodies/"
        private const val pathItems: String = "#/components/pathItems/"

        fun schema(name: String): Reference = Reference("$schema$name")
        fun responses(name: String): Reference = Reference("$responses$name")
        fun parameters(name: String): Reference = Reference("$parameters$name")
        fun requestBodies(name: String): Reference = Reference("$requestBodies$name")
        fun pathItems(name: String): Reference = Reference("$pathItems$name")

        fun <A> value(value: A): ReferenceOr<A> = Value(value)


        //TODO-low Why the actual Fuck does the param have to be nullable???????????
        internal class Serializer<T>(private val dataSerializer: KSerializer<T>?) :
            KSerializer<ReferenceOr<T>> {

            private val refDescriptor =
                buildClassSerialDescriptor("Reference") { element<String>(RefKey) }

            override val descriptor: SerialDescriptor =
                buildClassSerialDescriptor("cloud.mallne.dicentra.aviator.koas.extensions.Reference") {
                    element("Ref", refDescriptor)
                    dataSerializer?.descriptor?.let { element("description", it) }
                }

            override fun serialize(encoder: Encoder, value: ReferenceOr<T>) {
                when (value) {
                    is Value -> encoder.encodeSerializableValue(dataSerializer!!, value.value)
                    is Reference -> encoder.encodeSerializableValue(Reference.serializer(), value)
                }
            }

            override fun deserialize(decoder: Decoder): ReferenceOr<T> {
                decoder as JsonDecoder
                val json = decoder.decodeSerializableValue(JsonElement.Companion.serializer())
                return if ((json as JsonObject).contains(RefKey))
                    Reference(json[RefKey]!!.jsonPrimitive.content)
                else Value(decoder.json.decodeFromJsonElement(dataSerializer!!, json))
            }
        }
    }
}