package cloud.mallne.dicentra.aviator.core.model.oas

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class ExternalDocumentation(
    var description: String? = null,
    var url: String,
    var extensions: MutableMap<String, JsonObject>? = null
) {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val externalDocumentation = o as ExternalDocumentation
        return this.description == externalDocumentation.description &&
                this.url == externalDocumentation.url &&
                this.extensions == externalDocumentation.extensions
    }

    override fun hashCode(): Int {
        return hashAll(description, url, extensions)
    }
    override fun toString(): String {
        val sb = """class ExternalDocumentation {
    description: ${toIndentedString(description)}
    url: ${toIndentedString(url)}
}"""
        return sb
    }
}

