package cloud.mallne.dicentra.aviator.koas.responses

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * A container for the expected responses of an operation. The container maps a HTTP response code
 * to the expected response. It is not expected from the documentation to necessarily cover all
 * possible HTTP response codes, since they may not be known in advance. However, it is expected
 * from the documentation to cover a successful operation response and any known errors.
 */
@Serializable(with = Responses.Companion.Serializer::class)
data class Responses(
    /**
     * The documentation of responses other than the ones declared for specific HTTP response codes.
     * It can be used to cover undeclared responses.
     */
    val default: ReferenceOr<Response>? = null,
    /**
     * Any HTTP status code can be used as the property name (one property per HTTP status code).
     * Describes the expected response for those HTTP status codes.
     */
    val responses: Map<Int, ReferenceOr<Response>> = emptyMap(),
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [kotlinx.serialization.json.JsonNull],
     * [kotlinx.serialization.json.JsonPrimitive], [kotlinx.serialization.json.JsonArray] or [kotlinx.serialization.json.JsonObject].
     */
    override val extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {

    constructor(
        statusCode: Int,
        response: Response
    ) : this(null, mapOf(statusCode to ReferenceOr.Value(response)))

    constructor(
        head: Pair<Int, ReferenceOr<Response>>,
        vararg responses: Pair<Int, ReferenceOr<Response>>
    ) : this(null, mapOf(head) + responses)

    operator fun plus(other: Responses): Responses =
        Responses(other.default ?: default, responses + other.responses)

    companion object {
        internal object Serializer : KSerializer<Responses> {
            override val descriptor: SerialDescriptor = ResponsesDescriptor
            private val responseSerializer = ReferenceOr.Companion.serializer(Response.serializer())
            private val responsesSerializer = MapSerializer(Int.Companion.serializer(), responseSerializer)

            override fun deserialize(decoder: Decoder): Responses {
                decoder as JsonDecoder
                val json = decoder.decodeSerializableValue(JsonElement.Companion.serializer()).jsonObject
                val default =
                    if (json.contains("default"))
                        decoder.json.decodeFromJsonElement(responseSerializer, json.getValue("default"))
                    else null
                val responsesJs = json.filterNot { it.key.startsWith("x-") || it.key == "default" }
                val responses =
                    if (responsesJs.isNotEmpty())
                        decoder.json.decodeFromJsonElement(responsesSerializer, JsonObject(responsesJs))
                    else emptyMap()
                val extensions = json.filter { it.key.startsWith("x-") }
                return Responses(default, responses, extensions)
            }

            override fun serialize(encoder: Encoder, value: Responses) {
                encoder as JsonEncoder
                val default =
                    value.default?.let {
                        encoder.json
                            .encodeToJsonElement(ReferenceOr.Companion.serializer(Response.serializer()), it)
                            .jsonObject
                    }
                val responses =
                    encoder.json.encodeToJsonElement(responsesSerializer, value.responses).jsonObject
                val json = JsonObject((default ?: emptyMap()) + responses + value.extensions)
                encoder.encodeSerializableValue(JsonElement.Companion.serializer(), json)
            }
        }
    }
}