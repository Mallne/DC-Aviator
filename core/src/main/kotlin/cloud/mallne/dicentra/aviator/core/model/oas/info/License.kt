package cloud.mallne.dicentra.aviator.core.model.oas.info

import cloud.mallne.dicentra.aviator.core.helper.equality
import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class License(
    var name: String,
    var url: String? = null,
    var identifier: String? = null,
    var extensions: MutableMap<String, JsonObject>? = null
) {

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val license = o as License
        return equality(this.name, license.name) &&
                equality(this.url, license.url) &&
                equality(this.identifier, license.identifier) &&
                equality(this.extensions, license.extensions)
    }

    override fun hashCode(): Int {
        return hashAll(name, url, identifier, extensions)
    }

    override fun toString(): String {
        val sb = """class License {
    name: ${toIndentedString(name)}
    url: ${toIndentedString(url)}
    identifier: ${toIndentedString(identifier)}
}"""
        return sb
    }
}

