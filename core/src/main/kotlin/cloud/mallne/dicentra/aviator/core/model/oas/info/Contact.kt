package cloud.mallne.dicentra.aviator.core.model.oas.info

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class Contact(
    var name: String? = null,
    var url: String? = null,
    var email: String? = null,
    var extensions: MutableMap<String, JsonObject>? = null
) {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val contact = o as Contact
        return this.name == contact.name &&
                this.url == contact.url &&
                this.email == contact.email &&
                this.extensions == contact.extensions
    }

    override fun hashCode(): Int {
        return hashAll(name, url, email, extensions)
    }

    override fun toString(): String {
        val sb = """class Contact {
    name: ${toIndentedString(name)}
    url: ${toIndentedString(url)}
    email: ${toIndentedString(email)}
}"""
        return sb
    }
}

