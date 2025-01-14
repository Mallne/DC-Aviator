package cloud.mallne.dicentra.aviator.koas.info

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** License information for the exposed API. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(License.Companion.Serializer::class)
@KeepGeneratedSerializer
data class License(
    /** The license name used for the API. */
    val name: String,
    /** A URL to the license used for the API. MUST be in the format of a URL. */
    val url: String? = null,
    val identifier: String? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    override var extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<License>(
                generatedSerializer(),
                License::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}