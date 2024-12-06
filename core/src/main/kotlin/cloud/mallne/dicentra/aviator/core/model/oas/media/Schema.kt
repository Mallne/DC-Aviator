package cloud.mallne.dicentra.aviator.core.model.oas.media

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.Extendable
import cloud.mallne.dicentra.aviator.core.model.oas.ExternalDocumentation
import cloud.mallne.dicentra.aviator.core.model.oas.Referenceable
import cloud.mallne.dicentra.aviator.core.model.oas.SpecVersion
import cloud.mallne.dicentra.aviator.core.model.oas.annotations.OpenAPI30
import cloud.mallne.dicentra.aviator.core.model.oas.annotations.OpenAPI31
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject


@Serializable
open class Schema(
    @OpenAPI30
    var type: String? = null,
    @OpenAPI31
    private var types: MutableSet<String> = mutableSetOf(),
    var items: Schema? = null,
    override var `$ref`: String? = null,
    override var description: String? = null,
    override var summary: String? = null,
    var default: JsonElement? = null,
    var additionalProperties: JsonElement? = null,
    var allOf: MutableList<Schema> = mutableListOf(),
    var anyOf: MutableList<Schema> = mutableListOf(),
    var oneOf: MutableList<Schema> = mutableListOf(),
    var properties: MutableMap<String, Schema> = mutableMapOf(),
    var writeOnly: Boolean = false,
    var format: String? = null,
    var required: MutableList<String> = mutableListOf(),
    var uniqueItems: Boolean = false,
    var enum: MutableList<JsonElement> = mutableListOf(),
    var title: String? = null,
    @OpenAPI31
    var examples: MutableList<JsonElement> = mutableListOf(),
    var readOnly: Boolean = false,
    var externalDocs: ExternalDocumentation? = null,
    var deprecated: Boolean = false,
    override var extensions: MutableMap<String, JsonElement> = mutableMapOf(),
    var discriminator: Discriminator? = null
): Referenceable, Extendable {

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val schema = o as Schema
        return this.title == schema.title &&
                this.uniqueItems == schema.uniqueItems &&
                this.required == schema.required &&
                this.type == schema.type &&
                this.properties == schema.properties &&
                this.additionalProperties == schema.additionalProperties &&
                this.description == schema.description &&
                this.format == schema.format &&
                this.`$ref` == schema.`$ref` &&
                this.readOnly == schema.readOnly &&
                this.writeOnly == schema.writeOnly &&
                this.externalDocs == schema.externalDocs &&
                this.deprecated == schema.deprecated &&
                this.extensions == schema.extensions &&
                this.discriminator == schema.discriminator &&
                this.enum == schema.enum &&
                this.types == schema.types &&
                this.allOf == schema.allOf &&
                this.anyOf == schema.anyOf &&
                this.oneOf == schema.oneOf &&
                this.default == schema.default &&
                this.examples == schema.examples &&
                this.items == schema.items
    }

    override fun hashCode(): Int {
        return hashAll(
            title,
            uniqueItems,
            required,
            type,
            properties,
            additionalProperties,
            description,
            format,
            `$ref`,
            readOnly,
            writeOnly,
            externalDocs,
            deprecated,
            extensions,
            discriminator,
            enum,
            default,
            types,
            allOf,
            anyOf,
            oneOf,
            examples,
            items
        )
    }
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("class Schema {\n")
        sb.append("    format: ").append(toIndentedString(format)).append("\n")
        sb.append("    \$ref: ").append(toIndentedString(`$ref`)).append("\n")
        sb.append("    description: ").append(toIndentedString(description)).append("\n")
        sb.append("    title: ").append(toIndentedString(title)).append("\n")
        sb.append("    uniqueItems: ").append(toIndentedString(uniqueItems)).append("\n")
        sb.append("    required: ").append(toIndentedString(required)).append("\n")
        sb.append("    properties: ").append(toIndentedString(properties)).append("\n")
        sb.append("    additionalProperties: ").append(toIndentedString(additionalProperties)).append("\n")
        sb.append("    readOnly: ").append(toIndentedString(readOnly)).append("\n")
        sb.append("    writeOnly: ").append(toIndentedString(writeOnly)).append("\n")
        sb.append("    externalDocs: ").append(toIndentedString(externalDocs)).append("\n")
        sb.append("    deprecated: ").append(toIndentedString(deprecated)).append("\n")
        sb.append("    discriminator: ").append(toIndentedString(discriminator)).append("\n")

        sb.append("}")
        return sb.toString()
    }

    enum class BynaryStringConversion(private val value: String) {
        BINARY_STRING_CONVERSION_BASE64("base64"),
        BINARY_STRING_CONVERSION_DEFAULT_CHARSET("default"),
        BINARY_STRING_CONVERSION_STRING_SCHEMA("string-schema");

        override fun toString(): String {
            return value.toString()
        }
    }

    enum class SchemaResolution(private val value: String) {
        @SerialName("default")
        DEFAULT("default"),

        @SerialName("inline")
        INLINE("inline"),

        @SerialName("all-of")
        ALL_OF("all-of"),

        @SerialName("all-of-ref")
        ALL_OF_REF("all-of-ref");

        override fun toString(): String {
            return value.toString()
        }
    }

    companion object {
        const val BIND_TYPE_AND_TYPES: String = "bind-type"
        const val BINARY_STRING_CONVERSION_PROPERTY: String = "binary-string-conversion"
        const val SCHEMA_RESOLUTION_PROPERTY: String = "schema-resolution"
        const val APPLY_SCHEMA_RESOLUTION_PROPERTY: String = "apply-schema-resolution"
    }
}

