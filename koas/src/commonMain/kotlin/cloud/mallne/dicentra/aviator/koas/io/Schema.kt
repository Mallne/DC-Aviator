package cloud.mallne.dicentra.aviator.koas.io

import cloud.mallne.dicentra.aviator.koas.AdditionalProperties
import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPIConstraintViolation
import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPISerializationException
import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.info.ExternalDocs
import cloud.mallne.dicentra.polyfill.ensure
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
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

/**
 * The Schema Object allows the definition of input and output data types. These types can be
 * objects, but also primitives and arrays. This object is an extended subset of the
 * [JSON Schema Specification Wright Draft 00](https://json-schema.org/). For more information about
 * the properties, see [JSON Schema Core](https://tools.ietf.org/html/draft-wright-json-schema-00)
 * and [JSON Schema Validation](https://tools.ietf.org/html/draft-wright-json-schema-validation-00).
 * Unless stated otherwise, the property definitions follow the JSON Schema.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Schema.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Schema(
    val title: String? = null,
    val description: String? = null,
    /** required is an object-level attribute, not a property attribute. */
    val required: List<String>? = null,
    val nullable: Boolean? = null,
    val allOf: List<ReferenceOr<Schema>> = emptyList(),
    val oneOf: List<ReferenceOr<Schema>> = emptyList(),
    val not: ReferenceOr<Schema>? = null,
    val anyOf: List<ReferenceOr<Schema>> = emptyList(),
    val properties: Map<String, ReferenceOr<Schema>> = emptyMap(),
    val additionalProperties: AdditionalProperties? = null,
    val discriminator: Discriminator? = null,
    val readOnly: Boolean? = null,
    val writeOnly: Boolean? = null,
    val xml: Xml? = null,
    val externalDocs: ExternalDocs? = null,
    val examples: List<ExampleValue> = emptyList(),
    val deprecated: Boolean = false,
    val maxProperties: Int? = null,
    val minProperties: Int? = null,
    /** Unlike JSON Schema this value MUST conform to the defined type for this parameter. */
    val default: DefaultValue? = null,
    val type: Type? = null,
    val format: String? = null,
    val items: ReferenceOr<Schema>? = null,
    val maximum: Double? = null,
    val exclusiveMaximum: Boolean? = null,
    val minimum: Double? = null,
    val exclusiveMinimum: Boolean? = null,
    val maxLength: Int? = null,
    val minLength: Int? = null,
    val pattern: String? = null,
    val maxItems: Int? = null,
    val minItems: Int? = null,
    val uniqueItems: Boolean? = null,
    val enum: List<String> = emptyList(),
    val multipleOf: Double? = null,
    @SerialName("\$id") val id: String? = null,
    @SerialName("\$anchor") val anchor: String? = null,
    override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {
    init {
        ensure(required?.isEmpty() != true) {
            OpenAPIConstraintViolation("An empty list required: [] is not valid. If all properties are optional, do not specify the required keyword.")
        }
    }

    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Schema>(
                generatedSerializer(),
                Schema::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }

    @Serializable(with = Type.Serializer::class)
    sealed interface Type {

        data class Array(val types: List<Basic>) : Type

        enum class Basic(val value: kotlin.String) : Type {
            @SerialName("array")
            Array("array"),

            @SerialName("object")
            Object("object"),

            @SerialName("number")
            Number("number"),

            @SerialName("boolean")
            Boolean("boolean"),

            @SerialName("integer")
            Integer("integer"),

            @SerialName("null")
            Null("null"),

            @SerialName("string")
            String("string");

            companion object {
                fun fromString(value: kotlin.String): Basic? =
                    entries.find { it.value.equals(value, ignoreCase = true) }
            }
        }

        object Serializer : KSerializer<Type> {
            override val descriptor: SerialDescriptor =
                buildClassSerialDescriptor("cloud.mallne.dicentra.aviator.koas.io.Schema.Type")

            override fun deserialize(decoder: Decoder): Type {
                val json = decoder.decodeSerializableValue(JsonElement.Companion.serializer())
                return when {
                    json is JsonArray ->
                        Array(
                            decoder
                                .decodeSerializableValue(ListSerializer(String.Companion.serializer()))
                                .mapNotNull(Basic.Companion::fromString)
                        )

                    json is JsonPrimitive && json.isString ->
                        Basic.fromString(json.content)
                            ?: throw OpenAPISerializationException("Invalid Basic.Type value: ${json.content}")

                    else -> throw OpenAPISerializationException("Schema.Type can only be a string or an array")
                }
            }

            override fun serialize(encoder: Encoder, value: Type) {
                when (value) {
                    is Array ->
                        encoder.encodeSerializableValue(
                            ListSerializer(String.serializer()),
                            value.types.map { it.value }
                        )

                    is Basic -> encoder.encodeString(value.value)
                }
            }
        }
    }
}