package cloud.mallne.dicentra.aviator.koas.info

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The object provides metadata about the API. The metadata MAY be used by the clients if needed,
 * and MAY be presented in editing or documentation generation tools for convenience.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Info.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Info(
    /** The title of the API. */
    val title: String,
    /** A short summary of the API. */
    val summary: String? = null,
    /**
     * A short description of the API. [CommonMark syntax](https://spec.commonmark.org/) MAY be used
     * for rich text representation.
     */
    val description: String? = null,
    /** A URL to the Terms of Service for the API. MUST be in the format of a URL. */
    val termsOfService: String? = null,
    /** The contact information for the exposed API. */
    val contact: Contact? = Contact(),
    /** The license information for the exposed API. */
    val license: License? = null,
    /**
     * The version of the OpenAPI document (which is distinct from the OpenAPI Specification version
     * or the API implementation version).
     */
    val version: String,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    override val extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {


    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Info>(
                generatedSerializer(),
                Info::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}