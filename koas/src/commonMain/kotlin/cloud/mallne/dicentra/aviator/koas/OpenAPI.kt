package cloud.mallne.dicentra.aviator.koas

import cloud.mallne.dicentra.aviator.koas.exceptions.ExplicitTypeException
import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.info.ExternalDocs
import cloud.mallne.dicentra.aviator.koas.info.Info
import cloud.mallne.dicentra.aviator.koas.info.Tag
import cloud.mallne.dicentra.aviator.koas.security.SecurityRequirement
import cloud.mallne.dicentra.aviator.koas.servers.Server
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.jvm.JvmStatic

/** This is the root document object for the API specification. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(OpenAPI.Companion.Serializer::class)
@KeepGeneratedSerializer
data class OpenAPI(
    @EncodeDefault(ALWAYS) val openapi: String = "3.1.1",
    /** Provides metadata about the API. The metadata can be used by the clients if needed. */
    val info: Info,
    /** The default value for the $schema keyword within Schema Objects contained within this OAS document. */
    val jsonSchemaDialect: String? = null,
    /** An array of Server Objects, which provide connectivity information to a target server. */
    val servers: List<Server> = listOf(Server(url = "/")),
    /** The available paths and operations for the API. */
    val paths: Map<String, PathItem> = emptyMap(),
    /**
     * The incoming webhooks that MAY be received as part of this API and that the API consumer MAY
     * choose to implement. Closely related to the callbacks feature, this section describes requests
     * initiated other than by an API call, for example by an out of band registration. The key name
     * is a unique string to refer to each webhook, while the (optionally referenced) Path Item Object
     * describes a request that may be initiated by the API provider and the expected responses.
     */
    val webhooks: Map<String, ReferenceOr<PathItem>> = emptyMap(),
    /** An element to hold various schemas for the specification. */
    val components: Components = Components(),
    /**
     * A declaration of which security mechanisms can be used across the API. The list of values
     * includes alternative security requirement objects that can be used. Only one of the security
     * requirement objects need to be satisfied to authorize a request. Individual operations can
     * override this definition. To make security optional, an empty security requirement can be
     * included in the array.
     */
    val security: List<SecurityRequirement> = emptyList(),
    /**
     * A list of tags used by the specification with additional metadata. The order of the tags can be
     * used to reflect on their order by the parsing tools. Not all tags that are used by the
     * 'Operation' Object must be declared. The tags that are not declared MAY be organized randomly
     * or based on the tools' logic. Each tag name in the list MUST be unique.
     */
    val tags: Set<Tag> = emptySet(),
    /** Additional external documentation. */
    val externalDocs: ExternalDocs? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    override var extensions: Map<String, JsonElement> = mutableMapOf()
) : Extendable {

    fun operationsByTag(): Map<String, List<Operation>> = TODO()
    //    tags.associateBy(Tag::name) { tag ->
    //      operations().filter { it.tags.contains(tag.name) }
    //    }

    fun withComponents(components: Components): OpenAPI = copy(components = components)

    fun withPathItem(path: String, pathItem: PathItem): OpenAPI {
        val newPathItem =
            when (val existing = paths[path]) {
                null -> pathItem
                else -> existing + pathItem
            }

        return copy(paths = paths + Pair(path, newPathItem))
    }

    fun withServers(servers: List<Server>): OpenAPI = copy(servers = this.servers + servers)

    fun withServers(vararg servers: Server): OpenAPI =
        copy(servers = this.servers + servers.toList())

    fun withServer(server: Server): OpenAPI = copy(servers = this.servers + listOf(server))

    fun withTags(tags: Set<Tag>): OpenAPI = copy(tags = this.tags + tags)

    fun withTag(tag: Tag): OpenAPI = copy(tags = this.tags + setOf(tag))

    fun withExternalDocs(externalDocs: ExternalDocs): OpenAPI =
        copy(externalDocs = externalDocs)

    fun withExtensions(extensions: Map<String, JsonElement>): OpenAPI =
        copy(extensions = this.extensions + extensions)

    companion object {
        internal object Serializer :
            KSerializerWithExtensions<OpenAPI>(
                generatedSerializer(),
                OpenAPI::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )

        @JvmStatic
        val Json: Json = Json {
            encodeDefaults = false
            prettyPrint = true
            // TODO: Should this somehow be configurable?
            //   This allows incorrect OpenAPI to be parsed,
            //   such as OpenAPI Generator skips validation.
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        is List<*> -> JsonArray(map { it.toJsonElement() })
        is Map<*, *> ->
            @Suppress("UNCHECKED_CAST")
            JsonObject((this as Map<String, Any?>).mapValues { (_, v) -> v.toJsonElement() })

        null -> JsonNull
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> throw ExplicitTypeException("Unsupported type: ${this::class.simpleName}")
    }
