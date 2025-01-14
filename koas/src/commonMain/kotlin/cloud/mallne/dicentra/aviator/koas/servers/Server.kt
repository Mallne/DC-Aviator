package cloud.mallne.dicentra.aviator.koas.servers

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** An object representing a Server. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Server.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Server(
    /**
     * A URL to the target host. This URL supports Server Variables and MAY be relative, to indicate
     * that the host location is relative to the location where the OpenAPI document is being served.
     * Variable substitutions will be made when a variable is named in {brackets}.
     */
    val url: String,
    /**
     * An optional string describing the host designated by the URL. CommonMark syntax MAY be used for
     * rich text representation.
     */
    val description: String? = null,
    /**
     * A map between a variable name and its value. The value is used for substitution in the server's
     * URL template.
     */
    val variables: Map<String, Variable> = emptyMap(),
    override var extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Server>(
                generatedSerializer(),
                Server::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}