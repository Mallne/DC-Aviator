package cloud.mallne.dicentra.aviator.koas

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import cloud.mallne.dicentra.aviator.koas.servers.Server
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable(PathItem.Companion.Serializer::class)
@KeepGeneratedSerializer
data class PathItem(
    /**
     * Allows for an external definition of this path item. The referenced structure MUST be in the
     * format of a [PathItem]. In case a [PathItem] field appears both in the defined object and the
     * referenced object, the behavior is undefined.
     */
    val ref: String? = null,
    /** An optional, string summary, intended to apply to all operations in this path. */
    val summary: String? = null,
    /**
     * An optional, string description, intended to apply to all operations in this path. CommonMark
     * syntax MAY be used for rich text representation.
     */
    val description: String? = null,
    /** A definition of a GET operation on this path. */
    val get: Operation = Operation(),
    /** A definition of a PUT operation on this path. */
    val put: Operation = Operation(),
    /** A definition of a POST operation on this path. */
    val post: Operation = Operation(),
    /** A definition of a DELETE operation on this path. */
    val delete: Operation = Operation(),
    /** A definition of a OPTIONS operation on this path. */
    val options: Operation = Operation(),
    /** A definition of a HEAD operation on this path. */
    val head: Operation = Operation(),
    /** A definition of a PATCH operation on this path. */
    val patch: Operation = Operation(),
    /** A definition of a TRACE operation on this path. */
    val trace: Operation = Operation(),
    /** An alternative server array to service all operations in this path. */
    val servers: List<Server> = emptyList(),
    /**
     * A list of parameters that are applicable for all the operations described under this path.
     * These parameters can be overridden at the operation level, but cannot be removed there. The
     * list MUST NOT include duplicated parameters. A unique parameter is defined by a combination of
     * a name and location. The list can use the Reference Object to link to parameters that are
     * defined at the OpenAPI Object's components/parameters.
     */
    val parameters: List<ReferenceOr<Parameter>> = emptyList(),
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    override val extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {

    operator fun plus(other: PathItem): PathItem =
        PathItem(
            null,
            null,
            get = get ?: other.get,
            put = put ?: other.put,
            post = post ?: other.post,
            delete = delete ?: other.delete,
            options = options ?: other.options,
            head = head ?: other.head,
            patch = patch ?: other.patch,
            trace = trace ?: other.trace,
            servers = emptyList(),
            parameters = emptyList()
        )

    companion object {
        internal object Serializer :
            KSerializerWithExtensions<PathItem>(
                generatedSerializer(),
                PathItem::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}
