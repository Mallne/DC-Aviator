package cloud.mallne.dicentra.aviator.koas.responses

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.io.Header
import cloud.mallne.dicentra.aviator.koas.io.MediaType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@Serializable(Response.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Response(
    /**
     * A short description of the response. CommonMark's syntax MAY be used for rich text
     * representation.
     */
    val description: String,
    /** Maps a header name to its definition. RFC7230 states header names are case-insensitive. */
    val headers: Map<String, ReferenceOr<Header>> = emptyMap(),
    /**
     * A map containing descriptions of potential response payloads. The key is a media type or media
     * type range and the value describes it. For responses that match multiple keys, only the most
     * specific key is applicable. i.e. text/plain overrides text
     */
    val content: Map<String, MediaType> = emptyMap(),
    /**
     * A map of operations links that can be followed from the response. The key of the map is a short
     * name for the link, following the naming constraints of the names for Component Objects.
     */
    val links: Map<String, ReferenceOr<Link>> = emptyMap(),
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    override val extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    operator fun plus(other: Response): Response =
        Response(
            description,
            headers + other.headers,
            content + other.content,
            links + other.links,
            extensions + other.extensions
        )

    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Response>(
                generatedSerializer(),
                Response::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}