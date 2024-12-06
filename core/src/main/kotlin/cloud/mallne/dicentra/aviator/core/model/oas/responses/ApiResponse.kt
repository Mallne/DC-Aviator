package cloud.mallne.dicentra.aviator.core.model.oas.responses

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.headers.Header
import cloud.mallne.dicentra.aviator.core.model.oas.links.Link
import cloud.mallne.dicentra.aviator.core.model.oas.media.Content
import cloud.mallne.dicentra.aviator.core.model.oas.media.MediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class ApiResponse(
    var description: String,
    var headers: MutableMap<String, Header>? = null,
    var content: MutableMap<String, MediaType>? = null,
    var links: MutableMap<String, Link>? = null,
    var extensions: MutableMap<String, JsonObject>? = null,
    var `$ref`: String? = null
) {


    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val apiResponse = o as ApiResponse
        return this.description == apiResponse.description &&
                this.headers == apiResponse.headers &&
                this.content == apiResponse.content &&
                this.links == apiResponse.links &&
                this.extensions == apiResponse.extensions &&
                this.`$ref` == apiResponse.`$ref`
    }

    override fun hashCode(): Int {
        return hashAll(description, headers, content, links, extensions, `$ref`)
    }

    override fun toString(): String {
        val sb = """class ApiResponse {
    description: ${toIndentedString(description)}
    headers: ${toIndentedString(headers)}
    content: ${toIndentedString(content)}
    links: ${toIndentedString(links)}
    extensions: ${toIndentedString(extensions)}
    ${"$"}ref: ${toIndentedString(`$ref`)}
}"""
        return sb
    }
}

