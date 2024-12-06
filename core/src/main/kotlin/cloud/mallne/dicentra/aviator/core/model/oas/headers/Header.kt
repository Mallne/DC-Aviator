package cloud.mallne.dicentra.aviator.core.model.oas.headers

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.examples.Example
import cloud.mallne.dicentra.aviator.core.model.oas.media.Content
import cloud.mallne.dicentra.aviator.core.model.oas.media.Schema
import cloud.mallne.dicentra.aviator.core.model.oas.parameters.Parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
class Header(
    var description: String? = null,
    var `$ref`: String? = null,
    var required: Boolean? = null,
    var deprecated: Boolean? = null,
    var style: StyleEnum = StyleEnum.SIMPLE,
    var explode: Boolean? = null,
    var schema: Schema? = null,
    var examples: MutableMap<String, Example> = mutableMapOf(),
    var example: JsonElement? = null,
    var content: Content? = null,
    var extensions: MutableMap<String, JsonObject>? = null
) {


    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val header = o as Header
        return this.description == header.description &&
                this.required == header.required &&
                this.deprecated == header.deprecated &&
                this.style == header.style &&
                this.explode == header.explode &&
                this.schema == header.schema &&
                this.examples == header.examples &&
                this.example == header.example &&
                this.content == header.content &&
                this.extensions == header.extensions &&
                this.`$ref` == header.`$ref`
    }

    override fun hashCode(): Int {
        return hashAll(
            description,
            required,
            deprecated,
            style,
            explode,
            schema,
            examples,
            example,
            content,
            extensions,
            `$ref`
        )
    }

    override fun toString(): String {
        val sb = """class Header {
    description: ${toIndentedString(description)}
    required: ${toIndentedString(required)}
    deprecated: ${toIndentedString(deprecated)}
    style: ${toIndentedString(style)}
    explode: ${toIndentedString(explode)}
    schema: ${toIndentedString(schema)}
    examples: ${toIndentedString(examples)}
    example: ${toIndentedString(example)}
    content: ${toIndentedString(content)}
    ${"$"}ref: ${toIndentedString(`$ref`)}
}"""
        return sb
    }

    /**
     * Gets or Sets style
     */
    enum class StyleEnum(private val value: String) {
        @SerialName("simple") SIMPLE("simple");

        override fun toString(): String {
            return value.toString()
        }
    }
}

