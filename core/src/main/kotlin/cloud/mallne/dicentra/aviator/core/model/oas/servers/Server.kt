package cloud.mallne.dicentra.aviator.core.model.oas.servers

import cloud.mallne.dicentra.aviator.core.helper.equality
import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class Server(
    var url: String,
    var description: String? = null,
    var variables: ServerVariables? = null,
    var extensions: MutableMap<String, JsonObject>? = null
) {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val server = o as Server
        return equality(this.url, server.url) &&
                equality(this.description, server.description) &&
                equality(this.variables, server.variables) &&
                equality(this.extensions, server.extensions)
    }

    override fun hashCode(): Int {
        return hashAll(url, description, variables, extensions)
    }

    override fun toString(): String {
        val sb = """class Server {
    url: ${toIndentedString(url)}
    description: ${toIndentedString(description)}
    variables: ${toIndentedString(variables)}
}"""
        return sb
    }
}

