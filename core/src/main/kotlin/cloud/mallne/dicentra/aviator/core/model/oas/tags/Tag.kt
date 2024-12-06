package cloud.mallne.dicentra.aviator.core.model.oas.tags

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.ExternalDocumentation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class Tag(
    var name: String,
    var description: String? = null,
    var externalDocs: ExternalDocumentation? = null,
    var extensions: MutableMap<String, JsonObject>? = null
) {


    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val tag = o as Tag
        return this.name == tag.name &&
                this.description == tag.description &&
                this.externalDocs == tag.externalDocs &&
                this.extensions == tag.extensions
    }

    override fun hashCode(): Int {
        return hashAll(name, description, externalDocs, extensions)
    }

    override fun toString(): String {
        val sb = """class Tag {
    name: ${toIndentedString(name)}
    description: ${toIndentedString(description)}
    externalDocs: ${toIndentedString(externalDocs)}
}"""
        return sb
    }
}

