package cloud.mallne.dicentra.aviator.core.model.oas.parameters

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.examples.Example
import cloud.mallne.dicentra.aviator.core.model.oas.media.Content
import cloud.mallne.dicentra.aviator.core.model.oas.media.Schema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
open class Parameter(
    var name: String,
    var `in`: String,
    var description: String? = null,
    open var required: Boolean,
    var deprecated: Boolean = false,
    var allowEmptyValue: Boolean? = null,
    var `$ref`: String? = null,
    var style: StyleEnum? = null,
    var explode: Boolean? = null,
    var allowReserved: Boolean = false,
    var schema: Schema? = null,
    var examples: MutableMap<String, Example> = mutableMapOf(),
    var example: JsonObject? = null,
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
        val parameter = o as Parameter
        return this.name == parameter.name &&
                this.`in` == parameter.`in` &&
                this.description == parameter.description &&
                this.required == parameter.required &&
                this.deprecated == parameter.deprecated &&
                this.allowEmptyValue == parameter.allowEmptyValue &&
                this.style == parameter.style &&
                this.explode == parameter.explode &&
                this.allowReserved == parameter.allowReserved &&
                this.schema == parameter.schema &&
                this.examples == parameter.examples &&
                this.example == parameter.example &&
                this.content == parameter.content &&
                this.`$ref` == parameter.`$ref` &&
                this.extensions == parameter.extensions
    }

    override fun hashCode(): Int {
        return hashAll(
            name,
            `in`,
            description,
            required,
            deprecated,
            allowEmptyValue,
            style,
            explode,
            allowReserved,
            schema,
            examples,
            example,
            content,
            `$ref`,
            extensions
        )
    }

    override fun toString(): String {
        val sb = """class Parameter {
    name: ${toIndentedString(name)}
    in: ${toIndentedString(`in`)}
    description: ${
            toIndentedString(
                description
            )
        }
    required: ${toIndentedString(required)}
    deprecated: ${
            toIndentedString(
                deprecated
            )
        }
    allowEmptyValue: ${
            toIndentedString(
                allowEmptyValue
            )
        }
    style: ${toIndentedString(style)}
    explode: ${toIndentedString(explode)}
    allowReserved: ${
            toIndentedString(
                allowReserved
            )
        }
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
        @SerialName("matrix") MATRIX("matrix"),
        @SerialName("label") LABEL("label"),
        @SerialName("form") FORM("form"),
        @SerialName("simple") SIMPLE("simple"),
        @SerialName("spaceDelimited") SPACEDELIMITED("spaceDelimited"),
        @SerialName("pipeDelimited") PIPEDELIMITED("pipeDelimited"),
        @SerialName("deepObject") DEEPOBJECT("deepObject");
    }
}

