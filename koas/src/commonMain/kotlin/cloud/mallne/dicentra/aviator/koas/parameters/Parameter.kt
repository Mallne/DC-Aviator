package cloud.mallne.dicentra.aviator.koas.parameters

import cloud.mallne.dicentra.aviator.koas.Style
import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPIConstraintViolation
import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.io.Example
import cloud.mallne.dicentra.aviator.koas.io.ExampleValue
import cloud.mallne.dicentra.aviator.koas.io.MediaType
import cloud.mallne.dicentra.aviator.koas.io.Schema
import cloud.mallne.dicentra.polyfill.ensure
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Describes a single operation parameter.
 *
 * A unique parameter is defined by a combination of a [name] and [input].
 *
 * Parameter Locations There are four possible parameter locations specified by the in field:
 *
 * path - Used together with Path Templating, where the parameter value is actually part of the
 * operation's URL. This does not include the host or base path of the API. For example, in
 * /items/{itemId}, the path parameter is itemId. query - Parameters that are appended to the URL.
 * For example, in /items?id=###, the query parameter is id. header - Custom headers that are
 * expected as part of the request. Note that RFC7230 states header names are case insensitive.
 * cookie - Used to pass a specific cookie value to the API.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Parameter.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Parameter(
    /**
     * The name of the parameter. Parameter names are case sensitive. If in is "path", the name field
     * MUST correspond to a template expression occurring within the path field in the Paths Object.
     * See [Path Templating](https://swagger.io/specification/#path-templating) for further
     * information. If in is "header" and the name field is "Accept", "Content-Type" or
     * "Authorization", the parameter definition SHALL be ignored. For all other cases, the name
     * corresponds to the parameter name used by the in property.
     */
    val name: String,
    @SerialName("in")
    /** The input of the parameter. */
    val input: Input,
    /**
     * A brief description of the parameter. This could contain examples of use. CommonMark syntax MAY
     * be used for rich text representation.
     */
    val description: String? = null,
    /**
     * Determines whether this parameter is mandatory. If the parameter location is "path", this
     * property is REQUIRED and its value MUST be true. Otherwise, the property MAY be included and
     * its default value is false.
     */
    val required: Boolean = input == Input.Path,
    /**
     * Specifies that a parameter is deprecated and SHOULD be transitioned out of usage. Default value
     * is false.
     */
    val deprecated: Boolean = false,
    /**
     * Sets the ability to pass empty-valued parameters. This is valid only for query parameters and
     * allows sending a parameter with an empty value. Default value is false. If style is used, and
     * if behavior is n/a (cannot be serialized), the value of allowEmptyValue SHALL be ignored. Use
     * of this property is NOT RECOMMENDED, as it is likely to be removed in a later revision.
     */
    val allowEmptyValue: Boolean = false,
    /**
     * Determines whether the parameter value SHOULD allow reserved characters, as defined by RFC3986
     * :/?#[]@!$&'()*+,;= to be included without percent-encoding. This property only applies to
     * parameters with an in value of query. The default value is false.
     */
    val allowReserved: Boolean = false,
    /** The schema defining the type used for the parameter. */
    val schema: ReferenceOr<Schema>? = null,
    val content: Map<String, ReferenceOr<MediaType>> = emptyMap(),
    /**
     * Describes how the parameter value will be serialized depending on the type of the parameter
     * value. Default values (based on value of _paramIn): for ParamQuery - StyleForm; for ParamPath -
     * StyleSimple; for ParamHeader - StyleSimple; for ParamCookie - StyleForm.
     */
    val style: Style? = null,
    val explode: Boolean? = null,
    /**
     * Example of the parameter's potential value. The example SHOULD match the specified schema and
     * encoding properties if present. The example field is mutually exclusive of the examples field.
     * Furthermore, if referencing a schema that contains an example, the example value SHALL override
     * the example provided by the schema. To represent examples of media types that cannot naturally
     * be represented in JSON or YAML, a string value can contain the example with escaping where
     * necessary.
     */
    val example: ExampleValue? = null,
    /**
     * Examples of the parameter's potential value. Each example SHOULD contain a value in the correct
     * format as specified in the parameter encoding. The _paramExamples field is mutually exclusive
     * of the _paramExample field. Furthermore, if referencing a schema that contains an example, the
     * examples value SHALL override the example provided by the schema.
     */
    val examples: Map<String, ReferenceOr<Example>>? = emptyMap(),
    override var extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {
    init {
        ensure(content.isNotEmpty() || schema != null) {
            OpenAPIConstraintViolation("Either content or schema must be provided for parameter $name")
        }
        if (input == Input.Path)
            ensure(required) {
                OpenAPIConstraintViolation("${required}Determines whether this parameter is mandatory. If the parameter location is \"path\", this property is REQUIRED and its value MUST be true. Otherwise, the property MAY be included and its default value is false.")
            }
    }

    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Parameter>(
                generatedSerializer(),
                Parameter::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }

    @Serializable
    enum class Input(val value: String) {
        @SerialName("query")
        Query("query"),

        @SerialName("header")
        Header("header"),

        @SerialName("path")
        Path("path"),

        @SerialName("cookie")
        Cookie("cookie"),

        @SerialName("querystring")
        Querystring("querystring")
    }
}