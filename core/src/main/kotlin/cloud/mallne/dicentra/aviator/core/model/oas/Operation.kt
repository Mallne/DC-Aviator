package cloud.mallne.dicentra.aviator.core.model.oas

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.callbacks.Callback
import cloud.mallne.dicentra.aviator.core.model.oas.parameters.Parameter
import cloud.mallne.dicentra.aviator.core.model.oas.parameters.RequestBody
import cloud.mallne.dicentra.aviator.core.model.oas.responses.ApiResponses
import cloud.mallne.dicentra.aviator.core.model.oas.security.SecurityRequirement
import cloud.mallne.dicentra.aviator.core.model.oas.servers.Server
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class Operation(
    var tags: MutableList<String>? = null,
    var summary: String? = null,
    var description: String? = null,
    var externalDocs: ExternalDocumentation? = null,
    var operationId: String? = null,
    var parameters: MutableList<Parameter?>? = null,
    var requestBody: RequestBody? = null,
    var responses: ApiResponses? = null,
    var callbacks: MutableMap<String, Callback>? = null,
    var deprecated: Boolean? = null,
    var security: MutableList<SecurityRequirement?>? = null,
    var servers: MutableList<Server?>? = null,
    override var extensions: MutableMap<String?, JsonObject?>? = null
): Extendable {

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val operation = o as Operation
        return this.tags == operation.tags &&
                this.summary == operation.summary &&
                this.description == operation.description &&
                this.externalDocs == operation.externalDocs &&
                this.operationId == operation.operationId &&
                this.parameters == operation.parameters &&
                this.requestBody == operation.requestBody &&
                this.responses == operation.responses &&
                this.callbacks == operation.callbacks &&
                this.deprecated == operation.deprecated &&
                this.security == operation.security &&
                this.servers == operation.servers &&
                this.extensions == operation.extensions
    }

    override fun hashCode(): Int {
        return hashAll(
            tags,
            summary,
            description,
            externalDocs,
            operationId,
            parameters,
            requestBody,
            responses,
            callbacks,
            deprecated,
            security,
            servers,
            extensions
        )
    }

    override fun toString(): String {
        val sb = "class Operation {\n" +
                "    tags: " + toIndentedString(tags) + "\n" +
                "    summary: " + toIndentedString(summary) + "\n" +
                "    description: " + toIndentedString(description) + "\n" +
                "    externalDocs: " + toIndentedString(externalDocs) + "\n" +
                "    operationId: " + toIndentedString(operationId) + "\n" +
                "    parameters: " + toIndentedString(parameters) + "\n" +
                "    requestBody: " + toIndentedString(requestBody) + "\n" +
                "    responses: " + toIndentedString(responses) + "\n" +
                "    callbacks: " + toIndentedString(callbacks) + "\n" +
                "    deprecated: " + toIndentedString(deprecated) + "\n" +
                "    security: " + toIndentedString(security) + "\n" +
                "    servers: " + toIndentedString(servers) + "\n" +
                "}"
        return sb
    }
}

