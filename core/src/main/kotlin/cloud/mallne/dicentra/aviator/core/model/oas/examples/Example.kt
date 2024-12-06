package cloud.mallne.dicentra.aviator.core.model.oas.examples

import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
class Example(
    var summary: String? = null,
    var description: String? = null,
    var value: JsonElement? = null,
    var externalValue: String? = null,
    var `$ref`: String? = null,
    var extensions: MutableMap<String, JsonObject>? = null
) {
    val valueSetFlag: Boolean
        get() = value != null

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is Example) {
            return false
        }

        if (summary != o.summary) {
            return false
        }
        if (description != o.description) {
            return false
        }
        if (value != o.value) {
            return false
        }
        if (externalValue != o.externalValue) {
            return false
        }
        if (`$ref` != o.`$ref`) {
            return false
        }
        return extensions == o.extensions
    }

    override fun hashCode(): Int {
        var result = if (summary != null) summary.hashCode() else 0
        result = 31 * result + (if (description != null) description.hashCode() else 0)
        result = 31 * result + (if (value != null) value.hashCode() else 0)
        result = 31 * result + (if (externalValue != null) externalValue.hashCode() else 0)
        result = 31 * result + (if (`$ref` != null) `$ref`.hashCode() else 0)
        result = 31 * result + (if (extensions != null) extensions.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        val sb = """class Example {
    summary: ${toIndentedString(summary)}
    description: ${toIndentedString(description)}
    value: ${toIndentedString(value)}
    externalValue: ${toIndentedString(externalValue)}
    ${"$"}ref: ${toIndentedString(`$ref`)}
}"""
        return sb
    }
}

