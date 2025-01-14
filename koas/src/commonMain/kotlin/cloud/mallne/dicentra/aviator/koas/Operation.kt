package cloud.mallne.dicentra.aviator.koas

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.info.ExternalDocs
import cloud.mallne.dicentra.aviator.koas.io.Callback
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import cloud.mallne.dicentra.aviator.koas.parameters.RequestBody
import cloud.mallne.dicentra.aviator.koas.responses.Responses
import cloud.mallne.dicentra.aviator.koas.security.SecurityRequirement
import cloud.mallne.dicentra.aviator.koas.servers.Server
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable(Operation.Companion.Serializer::class)
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
data class Operation(
    /**
     * A list of tags for API documentation control. Tags can be used for logical grouping of
     * operations by resources or any other qualifier.
     */
    val tags: List<String> = emptyList(),
    /** A short summary of what the operation does. */
    val summary: String? = null,
    /**
     * A verbose explanation of the operation behavior. CommonMark syntax MAY be used for rich text
     * representation.
     */
    val description: String? = null,
    /** Additional external documentation for this operation. */
    val externalDocs: ExternalDocs? = null,
    /**
     * Unique string used to identify the operation. The id MUST be unique among all operations
     * described in the API. The operationId value is case-sensitive. Tools and libraries MAY use the
     * operationId to uniquely identify an operation, therefore, it is RECOMMENDED to follow common
     * programming naming conventions.
     */
    val operationId: String? = null,
    /**
     * A list of parameters that are applicable for this operation. If a parameter is already defined
     * at the Path Item, the new definition will override it but can never remove it. The list MUST
     * NOT include duplicated parameters. A unique parameter is defined by a combination of a name and
     * location. The list can use the Reference Object to link to parameters that are defined at the
     * OpenAPI Object's components/parameters.
     */
    val parameters: List<ReferenceOr<Parameter>> = emptyList(),
    /**
     * The request body applicable for this operation. The requestBody is only supported in HTTP
     * methods where the HTTP 1.1 specification RFC7231 has explicitly defined semantics for request
     * bodies. In other cases where the HTTP spec is vague, requestBody SHALL be ignored by consumers.
     */
    val requestBody: ReferenceOr<RequestBody>? = null,
    /** The list of possible responses as they are returned from executing this operation. */
    val responses: Responses = Responses(),
    /**
     * A map of possible out-of band callbacks related to the parent operation. The key is a unique
     * identifier for the Callback Object. Each value in the map is a Callback Object that describes a
     * request that may be initiated by the API provider and the expected responses.
     */
    val callbacks: Map<String, ReferenceOr<Callback>> = emptyMap(),
    /**
     * Declares this operation to be deprecated. Consumers SHOULD refrain from usage of the declared
     * operation. Default value is false.
     */
    val deprecated: Boolean = false,
    /**
     * A declaration of which security mechanisms can be used for this operation. The list of values
     * includes alternative security requirement objects that can be used. Only one of the security
     * requirement objects need to be satisfied to authorize a request. To make security optional, an
     * empty security requirement ({}) can be included in the array. This definition overrides any
     * declared top-level security. To remove a top-level security declaration, an empty array can be
     * used.
     */
    val security: List<SecurityRequirement> = emptyList(),
    /**
     * An alternative server array to service this operation. If an alternative server object is
     * specified at the Path Item Object or Root level, it will be overridden by this value.
     */
    val servers: List<Server> = emptyList(),
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    override var extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Operation>(
                generatedSerializer(),
                Operation::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}
