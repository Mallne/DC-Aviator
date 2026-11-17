package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Explicit serializers available to reconstruct typed Catalyst request parameters. */
class CatalystParameterCodecs(serializers: Iterable<KSerializer<*>>) {
    private val builtinSerializers = listOf(
        String.serializer(), Boolean.serializer(), Byte.serializer(), Short.serializer(),
        Int.serializer(), Long.serializer(), Float.serializer(), Double.serializer(),
    )
    private val byId = (builtinSerializers + serializers).associateBy { it.descriptor.serialName }

    @Suppress("UNCHECKED_CAST")
    fun decode(codec: String, json: Json, value: JsonElement): Any {
        val serializer = byId[codec]
            ?: throw IllegalArgumentException("Unsupported Catalyst parameter codec '$codec'")
        return try {
            json.decodeFromJsonElement(serializer as KSerializer<Any>, value)
        } catch (cause: SerializationException) {
            // Numeric OpenAPI values are schema-typed by Catalyst. A stale narrow
            // numeric codec must not prevent a decimal from reaching that validator.
            if (codec in numericCodecIds && value is JsonPrimitive && value.isString.not()) {
                value.content.toDoubleOrNull() ?: throw cause
            } else {
                throw cause
            }
        }
    }
}

private val numericCodecIds = setOf(
    Byte.serializer().descriptor.serialName,
    Short.serializer().descriptor.serialName,
    Int.serializer().descriptor.serialName,
    Long.serializer().descriptor.serialName,
    Float.serializer().descriptor.serialName,
    Double.serializer().descriptor.serialName,
)

/** Encodes core Aviator parameters into Catalyst's typed transport representation. */
fun RequestParameters.toCatalystParameters(json: Json): Map<String, CatalystParameterValue> =
    mapValues { (_, parameter) -> parameter.toCatalystValue(json) }

private fun RequestParameter.toCatalystValue(json: Json): CatalystParameterValue = when (this) {
    is RequestParameter.Single<*> -> {
        val serializer = valueSerializer ?: builtinSerializer(value)
        CatalystParameterValue(CatalystParameterValue.Cardinality.SINGLE,
            listOf(encodeValue(json, value, serializer)), serializer?.descriptor?.serialName)
    }
    is RequestParameter.Multi<*> -> {
        val serializer = valueSerializer ?: value.firstOrNull()?.let(::builtinSerializer)
        CatalystParameterValue(CatalystParameterValue.Cardinality.MULTI,
            value.map { encodeValue(json, it, serializer) }, serializer?.descriptor?.serialName)
    }
}

private fun builtinSerializer(value: Any?): KSerializer<*>? = when (value) {
    is String -> String.serializer()
    is Boolean -> Boolean.serializer()
    is Byte -> Byte.serializer()
    is Short -> Short.serializer()
    is Int -> Int.serializer()
    is Long -> Long.serializer()
    is Float -> Float.serializer()
    is Double -> Double.serializer()
    else -> null
}

@Suppress("UNCHECKED_CAST")
private fun encodeValue(json: Json, value: Any?, serializer: KSerializer<*>?): JsonElement {
    if (value == null) return JsonNull
    if (serializer != null) return json.encodeToJsonElement(serializer as KSerializer<Any>, value)
    return when (value) {
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> error("A serializer is required to send ${value::class} through Catalyst")
    }
}

/** Reconstructs Aviator's parameter containers from the versioned typed Catalyst envelope. */
fun CatalystRequest.toRequestParameters(
    json: Json,
    codecs: CatalystParameterCodecs,
): RequestParameters {
    require(version == 1) { "Unsupported Catalyst request version '$version'" }

    return RequestParameters(typedParameters.mapValuesTo(mutableMapOf()) { (name, parameter) ->
        when (parameter.cardinality) {
            CatalystParameterValue.Cardinality.SINGLE -> {
                require(parameter.values.size == 1) { "Catalyst parameter '$name' must have exactly one value" }
                RequestParameter.Single(decodeValue(parameter, json, codecs))
            }
            CatalystParameterValue.Cardinality.MULTI -> RequestParameter.Multi(
                parameter.values.map { decodeValue(parameter, json, codecs, it) }
            )
        }
    })
}

private fun decodeValue(
    parameter: CatalystParameterValue,
    json: Json,
    codecs: CatalystParameterCodecs,
    value: JsonElement = parameter.values.single(),
): Any = parameter.codec?.let { codecs.decode(it, json, value) } ?: value
