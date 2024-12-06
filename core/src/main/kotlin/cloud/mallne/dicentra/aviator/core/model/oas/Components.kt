package cloud.mallne.dicentra.aviator.core.model.oas

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.annotations.OpenAPI31
import cloud.mallne.dicentra.aviator.core.model.oas.callbacks.Callback
import cloud.mallne.dicentra.aviator.core.model.oas.examples.Example
import cloud.mallne.dicentra.aviator.core.model.oas.headers.Header
import cloud.mallne.dicentra.aviator.core.model.oas.links.Link
import cloud.mallne.dicentra.aviator.core.model.oas.media.Schema
import cloud.mallne.dicentra.aviator.core.model.oas.parameters.Parameter
import cloud.mallne.dicentra.aviator.core.model.oas.parameters.RequestBody
import cloud.mallne.dicentra.aviator.core.model.oas.responses.ApiResponse
import cloud.mallne.dicentra.aviator.core.model.oas.security.SecurityScheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject


@Serializable
class Components(
    var schemas: MutableMap<String, Schema>? = null,
    var responses: MutableMap<String, ApiResponse>? = null,
    var parameters: MutableMap<String, Parameter>? = null,
    var examples: MutableMap<String, Example>? = null,
    var requestBodies: MutableMap<String, RequestBody>? = null,
    var headers: MutableMap<String, Header>? = null,
    var securitySchemes: MutableMap<String, SecurityScheme>? = null,
    var links: MutableMap<String, Link>? = null,
    var callbacks: MutableMap<String, Callback>? = null,
    var extensions: MutableMap<String, JsonObject>? = null,
    @OpenAPI31
    var pathItems: MutableMap<String, PathItem>? = null
) {


    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val components = o as Components
        return this.schemas == components.schemas &&
                this.responses == components.responses &&
                this.parameters == components.parameters &&
                this.examples == components.examples &&
                this.requestBodies == components.requestBodies &&
                this.headers == components.headers &&
                this.securitySchemes == components.securitySchemes &&
                this.links == components.links &&
                this.callbacks == components.callbacks &&
                this.extensions == components.extensions &&
                this.pathItems == components.pathItems
    }

    override fun hashCode(): Int {
        return hashAll(
            schemas,
            responses,
            parameters,
            examples,
            requestBodies,
            headers,
            securitySchemes,
            links,
            callbacks,
            extensions,
            pathItems
        )
    }

    override fun toString(): String {
        val sb = """class Components {
    schemas: ${toIndentedString(schemas)}
    responses: ${toIndentedString(responses)}
    parameters: ${toIndentedString(parameters)}
    examples: ${toIndentedString(examples)}
    requestBodies: ${toIndentedString(requestBodies)}
    headers: ${toIndentedString(headers)}
    securitySchemes: ${toIndentedString(securitySchemes)}
    links: ${toIndentedString(links)}
    callbacks: ${toIndentedString(callbacks)}
    pathItems: ${toIndentedString(pathItems)}
}"""
        return sb
    }

    companion object {
        const val COMPONENTS_SCHEMAS_REF: String = "#/components/schemas/"
    }
}

