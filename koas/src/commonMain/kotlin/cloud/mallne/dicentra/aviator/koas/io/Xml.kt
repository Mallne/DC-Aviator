package cloud.mallne.dicentra.aviator.koas.io

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@Serializable(Xml.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Xml(
    /**
     * Replaces the name of the element/attribute used for the described schema property. When defined
     * within the @'OpenApiItems'@ (items), it will affect the name of the individual XML elements
     * within the list. When defined alongside type being array (outside the items), it will affect
     * the wrapping element and only if wrapped is true. If wrapped is false, it will be ignored.
     */
    val name: String,
    /** The URL of the namespace definition. Value SHOULD be in the form of a URL. */
    val namespace: String? = null,
    /** The prefix to be used for the name. */
    val prefix: String? = null,
    /**
     * Declares whether the property definition translates to an attribute instead of an element.
     * Default value is @False@.
     */
    val attribute: Boolean = false,
    /**
     * MAY be used only for an array definition. Signifies whether the array is wrapped (for
     * example, @\<books\>\<book/\>\<book/\>\</books\>@) or unwrapped (@\<book/\>\<book/\>@). Default
     * value is
     *
     * @False@. The definition takes effect only when defined alongside type being array (outside the
     *   items).
     */
    val wrapped: Boolean = false,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    override var extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Xml>(
                generatedSerializer(),
                Xml::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}