package cloud.mallne.dicentra.aviator.koas.io

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable(Example.Companion.Serializer::class)
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
data class Example(
    /** Short description for the example. */
    val summary: String? = null,
    /**
     * Long description for the example. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,
    /**
     * Embedded literal example. The value field and externalValue field are mutually exclusive. To
     * represent examples of media types that cannot naturally represented in JSON or YAML, use a
     * string value to contain the example, escaping where necessary.
     */
    val value: ExampleValue? = null,
    /**
     * A URL that points to the literal example. This provides the capability to reference examples
     * that cannot easily be included in JSON or YAML documents. The value field and externalValue
     * field are mutually exclusive.
     */
    val externalValue: String? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    override val extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Example>(
                generatedSerializer(),
                Example::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}