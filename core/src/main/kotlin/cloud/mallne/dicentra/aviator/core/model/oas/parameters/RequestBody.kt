package cloud.mallne.dicentra.aviator.core.model.oas.parameters

import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.media.Content
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class RequestBody(
    var description: String? = null,
    var content: Content,
    var required: Boolean = false,
    var extensions: MutableMap<String, JsonObject>? = null,
    var `$ref`: String? = null
) {



    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is RequestBody) {
            return false
        }

        if (description != o.description) {
            return false
        }
        if (content != o.content) {
            return false
        }
        if (required != o.required) {
            return false
        }
        if (extensions != o.extensions) {
            return false
        }
        return `$ref` == o.`$ref`
    }

    override fun hashCode(): Int {
        var result = if (description != null) description.hashCode() else 0
        result = 31 * result + (content.hashCode())
        result = 31 * result + (required.hashCode())
        result = 31 * result + (if (extensions != null) extensions.hashCode() else 0)
        result = 31 * result + (if (`$ref` != null) `$ref`.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        val sb = """class RequestBody {
    description: ${toIndentedString(description)}
    content: ${toIndentedString(content)}
    required: ${toIndentedString(required)}
}"""
        return sb
    }
}

