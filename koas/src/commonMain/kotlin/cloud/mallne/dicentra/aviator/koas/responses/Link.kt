package cloud.mallne.dicentra.aviator.koas.responses

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.servers.Server
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The Link object represents a possible design-time link for a response. The presence of a link
 * does not guarantee the caller's ability to successfully invoke it, rather it provides a known
 * relationship and traversal mechanism between responses and other operations.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Link.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Link(
    /**
     * A relative or absolute URI reference to an OAS operation. This field is mutually exclusive of
     * the '_linkOperationId' field, and MUST point to an 'Operation' Object. Relative
     * '_linkOperationRef' values MAY be used to locate an existing 'Operation' Object in the OpenAPI
     * definition.
     */
    val operationRef: String? = null,
    /**
     * The name of an /existing/, resolvable OAS operation, as defined with a unique
     * '_operationOperationId'. This field is mutually exclusive of the '_linkOperationRef' field.
     */
    val operationId: String? = null,
    /**
     * A map representing parameters to pass to an operation as specified with '_linkOperationId' or
     * identified via '_linkOperationRef'. The key is the parameter name to be used, whereas the value
     * can be a constant or an expression to be evaluated and passed to the linked operation. The
     * parameter name can be qualified using the parameter location @[{in}.]{name}@ for operations
     * that use the same parameter name in different locations (e.g. path.id).
     */
    val parameters: Map<String, ExpressionOrValue> = emptyMap(),
    /**
     * A literal value or @{expression}@ to use as a request body when calling the target operation.
     */
    val requestBody: ExpressionOrValue? = null,
    /** A description of the link. */
    val description: String? = null,
    /** A server object to be used by the target operation. */
    val server: Server? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    override var extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Link>(
                generatedSerializer(),
                Link::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}