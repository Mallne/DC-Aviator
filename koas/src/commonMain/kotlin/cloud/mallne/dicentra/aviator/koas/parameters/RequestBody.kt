package cloud.mallne.dicentra.aviator.koas.parameters

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.io.MediaType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@Serializable(RequestBody.Companion.Serializer::class)
@KeepGeneratedSerializer
data class RequestBody(
    /**
     * A brief description of the request body. This could contain examples of use. CommonMark syntax
     * MAY be used for rich text representation.
     */
    val description: String? = null,
    /**
     * The content of the request body. The key is a media type or media type range and the value
     * describes it. For requests that match multiple keys, only the most specific key is applicable.
     * e.g. text/plain overrides text
     */
    val content: Map<String, MediaType>,
    /** Determines if the request body is required in the request. Defaults to false. */
    val required: Boolean = false,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    override var extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<RequestBody>(
                generatedSerializer(),
                RequestBody::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}