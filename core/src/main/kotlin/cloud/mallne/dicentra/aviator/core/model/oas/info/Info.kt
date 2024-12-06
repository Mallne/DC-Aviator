package cloud.mallne.dicentra.aviator.core.model.oas.info

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.annotations.OpenAPI31
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.*

@Serializable
class Info(
    var title: String,
    var description: String? = null,
    var termsOfService: String? = null,
    var contact: Contact? = null,
    var license: License? = null,
    var version: String,
    var extensions: MutableMap<String, JsonObject>? = null,
    var summary: String? = null
) {


    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val info = o as Info
        return this.title == info.title &&
                this.description == info.description &&
                this.termsOfService == info.termsOfService &&
                this.contact == info.contact &&
                this.license == info.license &&
                this.version == info.version &&
                this.extensions == info.extensions &&
                this.summary == info.summary
    }

    override fun hashCode(): Int {
        return hashAll(title, description, termsOfService, contact, license, version, extensions, summary)
    }

    override fun toString(): String {
        val sb = """class Info {
    title: ${toIndentedString(title)}
    description: ${toIndentedString(description)}
    summary: ${toIndentedString(summary)}
    termsOfService: ${toIndentedString(termsOfService)}
    contact: ${toIndentedString(contact)}
    license: ${toIndentedString(license)}
    version: ${toIndentedString(version)}
}"""
        return sb
    }
}

