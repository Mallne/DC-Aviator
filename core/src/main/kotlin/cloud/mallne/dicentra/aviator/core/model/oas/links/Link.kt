package cloud.mallne.dicentra.aviator.core.model.oas.links

import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.servers.Server
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
class Link(
    var operationRef: String? = null,
    var operationId: String? = null,
    var parameters: MutableMap<String, String>? = null,
    var requestBody: JsonElement? = null,
    var description: String? = null,
    var `$ref`: String? = null,
    var extensions: MutableMap<String, JsonObject>? = null,
    var server: Server? = null
) {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is Link) {
            return false
        }

        if (operationRef != o.operationRef) {
            return false
        }
        if (operationId != o.operationId) {
            return false
        }
        if (parameters != o.parameters) {
            return false
        }
        if (requestBody != o.requestBody) {
            return false
        }
        if (description != o.description) {
            return false
        }
        if (`$ref` != o.`$ref`) {
            return false
        }
        if (extensions != o.extensions) {
            return false
        }
        return server == o.server
    }

    override fun hashCode(): Int {
        var result = if (operationRef != null) operationRef.hashCode() else 0
        result = 31 * result + (if (operationId != null) operationId.hashCode() else 0)
        result = 31 * result + (if (parameters != null) parameters.hashCode() else 0)
        result = 31 * result + (if (requestBody != null) requestBody.hashCode() else 0)
        result = 31 * result + (if (description != null) description.hashCode() else 0)
        result = 31 * result + (if (`$ref` != null) `$ref`.hashCode() else 0)
        result = 31 * result + (if (extensions != null) extensions.hashCode() else 0)
        result = 31 * result + (if (server != null) server.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        val sb = """class Link {
    operationRef: ${toIndentedString(operationRef)}
    operationId: ${toIndentedString(operationId)}
    parameters: ${toIndentedString(parameters)}
    requestBody: ${toIndentedString(requestBody)}
    description: ${toIndentedString(description)}
    ${"$"}ref: ${toIndentedString(`$ref`)}
}"""
        return sb
    }
}

