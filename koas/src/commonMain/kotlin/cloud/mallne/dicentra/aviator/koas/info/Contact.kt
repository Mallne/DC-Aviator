package cloud.mallne.dicentra.aviator.koas.info

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Contact information for the exposed API. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Contact.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Contact(
    /** The identifying name of the contact person/organization. */
    val name: String? = null,
    /** The URL pointing to the contact information. MUST be in the format of a URL. */
    val url: String? = null,
    /**
     * The email address of the contact person/organization. MUST be in the format of an email
     * address.
     */
    val email: String? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    override var extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Contact>(
                generatedSerializer(),
                Contact::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}