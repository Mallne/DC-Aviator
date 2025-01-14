package cloud.mallne.dicentra.aviator.koas.io

import cloud.mallne.dicentra.aviator.koas.Style
import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A single encoding definition applied to a single schema property. */
@Serializable(Encoding.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
data class Encoding(
    /**
     * The Content-Type for encoding a specific property. Default value depends on the property type:
     * - for string with format being binary – application/octet-stream;
     * - for other primitive types – text/plain
     * - for object - application/json
     * - for array – the default is defined based on the inner type. The value can be a specific media
     *   type (e.g. application/json), a wildcard media type (e.g. image&#47;&#42;), or a
     *   comma-separated list of the two types.
     */
    val contentType: String, // Could be arrow.endpoint.model.MediaType
    /**
     * A map allowing additional information to be provided as headers, for example
     * Content-Disposition. Content-Type is described separately and SHALL be ignored in this section.
     * This property SHALL be ignored if the request body media type is not a multipart
     */
    val headers: Map<String, ReferenceOr<Header>> = emptyMap(),
    /**
     * Describes how a specific property value will be serialized depending on its type. See [cloud.mallne.dicentra.aviator.koas.Style]
     * for details on the style property. The behavior follows the same values as query parameters,
     * including default values. This property SHALL be ignored if the request body media type is not
     * application/x-www-form-urlencoded.
     */
    val style: String? = null,
    /**
     * When this is true, property values of type array or object generate separate parameters for
     * each value of the array, or key-value-pair of the map. For other types of properties this
     * property has no effect. When style is form, the default value is true. For all other styles,
     * the default value is false. This property SHALL be ignored if the request body media type is
     * not application/x-www-form-urlencoded.
     */
    val explode: Boolean = style?.let { it == Style.form.name } == true,
    /**
     * Determines whether the parameter value SHOULD allow reserved characters, as defined by RFC3986
     * :/?#[]@!$&'()*+,;= to be included without percent-encoding. The default value is false. This
     * property SHALL be ignored if the request body media type is not
     * application/x-www-form-urlencoded.
     */
    val allowReserved: Boolean,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    override var extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Encoding>(
                generatedSerializer(),
                Encoding::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}