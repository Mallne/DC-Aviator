package cloud.mallne.dicentra.aviator.koas

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.io.Callback
import cloud.mallne.dicentra.aviator.koas.io.Example
import cloud.mallne.dicentra.aviator.koas.io.Header
import cloud.mallne.dicentra.aviator.koas.io.MediaType
import cloud.mallne.dicentra.aviator.koas.io.Schema
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import cloud.mallne.dicentra.aviator.koas.parameters.RequestBody
import cloud.mallne.dicentra.aviator.koas.responses.Link
import cloud.mallne.dicentra.aviator.koas.responses.Response
import cloud.mallne.dicentra.aviator.koas.security.SecurityScheme
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Holds a set of reusable objects for different aspects of the OAS. All objects defined within the
 * components object will have no effect on the API unless they are explicitly referenced from
 * properties outside the components object.
 */
@Serializable(Components.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
data class Components(
    val schemas: Map<String, ReferenceOr<Schema>> = emptyMap(),
    val responses: Map<String, ReferenceOr<Response>> = emptyMap(),
    val parameters: Map<String, ReferenceOr<Parameter>> = emptyMap(),
    val examples: Map<String, ReferenceOr<Example>> = emptyMap(),
    val requestBodies: Map<String, ReferenceOr<RequestBody>> = emptyMap(),
    val headers: Map<String, ReferenceOr<Header>> = emptyMap(),
    val securitySchemes: Map<String, ReferenceOr<SecurityScheme>> = emptyMap(),
    val links: Map<String, ReferenceOr<Link>> = emptyMap(),
    val callbacks: Map<String, ReferenceOr<Callback>> = emptyMap(),
    val pathItems: Map<String, ReferenceOr<PathItem>> = emptyMap(),
    val mediaTypes: Map<String, ReferenceOr<MediaType>> = emptyMap(),
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    override var extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Components>(
                generatedSerializer(),
                Components::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}
