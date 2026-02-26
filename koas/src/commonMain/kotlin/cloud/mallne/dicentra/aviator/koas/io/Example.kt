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
     * An example of the data structure that MUST be valid according to the relevant Schema Object.
     * If this field is present, value MUST be absent.
     */
    val dataValue: JsonElement? = null,
    /**
     * An example of the serialized form of the value, including encoding and escaping as described under
     * Validating Examples. If dataValue is present, then this field SHOULD contain the serialization of
     * the given data. Otherwise, it SHOULD be the valid serialization of a data value that itself MUST be
     * valid as described for dataValue. This field SHOULD NOT be used if the serialization format is JSON,
     * as the data form is easier to work with. If this field is present, value, and externalValue MUST be absent.
     */
    val serializedValue: String? = null,
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
    override var extensions: Map<String, JsonElement> = mutableMapOf(),
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