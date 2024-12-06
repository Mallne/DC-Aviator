package cloud.mallne.dicentra.aviator.core.model.oas.servers

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class ServerVariables(
    var extensions: MutableMap<String, JsonObject>? = null
) : LinkedHashMap<String, ServerVariable>() {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val serverVariables = o as ServerVariables
        return this.extensions == serverVariables.extensions &&
                super.equals(o)
    }

    override fun hashCode(): Int {
        return hashAll(extensions, super.hashCode())
    }

    override fun toString(): String {
        val sb = """class ServerVariables {
    ${toIndentedString(super.toString())}
}"""
        return sb
    }
}
