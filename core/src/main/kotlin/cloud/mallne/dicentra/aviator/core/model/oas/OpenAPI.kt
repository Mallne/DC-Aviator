package cloud.mallne.dicentra.aviator.core.model.oas

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.info.Info
import cloud.mallne.dicentra.aviator.core.model.oas.security.SecurityRequirement
import cloud.mallne.dicentra.aviator.core.model.oas.servers.Server
import cloud.mallne.dicentra.aviator.core.model.oas.tags.Tag
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
class OpenAPI(
    var openapi: String = "3.1.0",
    var info: Info,
    var externalDocs: ExternalDocumentation? = null,
    var servers: MutableList<Server>? = null,
    var security: MutableList<SecurityRequirement>? = null,
    var tags: MutableList<Tag>? = null,
    var paths: Paths? = null,
    var components: Components? = null,
    override var extensions: MutableMap<String, JsonElement> = mutableMapOf(),
    var jsonSchemaDialect: String? = null,
    @Transient
    var specVersion: SpecVersion? = SpecVersion.V30,
    var webhooks: MutableMap<String, PathItem>? = null
): Extendable {


    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val openAPI = o as OpenAPI
        return this.openapi == openAPI.openapi &&
                this.info == openAPI.info &&
                this.externalDocs == openAPI.externalDocs &&
                this.servers == openAPI.servers &&
                this.security == openAPI.security &&
                this.tags == openAPI.tags &&
                this.paths == openAPI.paths &&
                this.components == openAPI.components &&
                this.webhooks == openAPI.webhooks &&
                this.extensions == openAPI.extensions &&
                this.jsonSchemaDialect == openAPI.jsonSchemaDialect
    }

    override fun hashCode(): Int {
        return hashAll(
            openapi,
            info,
            externalDocs,
            servers,
            security,
            tags,
            paths,
            components,
            webhooks,
            extensions,
            jsonSchemaDialect
        )
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("class OpenAPI {\n")

        sb.append("    openapi: ").append(toIndentedString(openapi)).append("\n")
        sb.append("    info: ").append(toIndentedString(info)).append("\n")
        sb.append("    externalDocs: ").append(toIndentedString(externalDocs)).append("\n")
        sb.append("    servers: ").append(toIndentedString(servers)).append("\n")
        sb.append("    security: ").append(toIndentedString(security)).append("\n")
        sb.append("    tags: ").append(toIndentedString(tags)).append("\n")
        sb.append("    paths: ").append(toIndentedString(paths)).append("\n")
        sb.append("    components: ").append(toIndentedString(components)).append("\n")
        if (specVersion == SpecVersion.V31) sb.append("    webhooks: ").append(toIndentedString(webhooks)).append("\n")
        if (specVersion == SpecVersion.V31) sb.append("    jsonSchemaDialect: ")
            .append(toIndentedString(jsonSchemaDialect)).append("\n")
        sb.append("}")
        return sb.toString()
    }
}

