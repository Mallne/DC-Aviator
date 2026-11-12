package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.HttpResponseMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.ResponseSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Values visible to the supported Arazzo runtime-expression profile. */
internal data class ArazzoExpressionContext(
    val inputs: JsonElement,
    val stepOutputs: Map<String, JsonObject> = emptyMap(),
    val responseBody: BodyValue = BodyValue.Unavailable,
    val response: HttpResponseMetadata? = null,
    val stepResponses: Map<String, ResponseSnapshot> = emptyMap(),
)

/** Evaluates only the documented subset; unsupported syntax and unavailable values fail closed. */
internal fun evaluateArazzoExpression(expression: String, context: ArazzoExpressionContext): JsonElement {
    val (reference, pointer) = expression.split("#", limit = 2).let { parts ->
        require(parts.size <= 2) { "Malformed Arazzo expression" }
        parts[0] to parts.getOrNull(1)?.let { "#$it" }
    }

    val root = when {
        reference == "\$inputs" -> context.inputs
        reference.startsWith("\$inputs.") -> {
            val path = reference.removePrefix("\$inputs.").split('.').joinToString("/") { token ->
                token.replace("~", "~0").replace("/", "~1")
            }
            requireNotNull(context.inputs.atJsonPointer("/$path")) { "Arazzo input is unavailable" }
        }
        reference == "\$statusCode" || reference == "\$response.statusCode" ->
            JsonPrimitive(requireNotNull(context.response) { "Response metadata is unavailable" }.status)
        reference == "\$response.body" -> responseBody(context.responseBody)
        reference.startsWith("\$response.header.") -> {
            val name = reference.removePrefix("\$response.header.")
            require(name.isNotBlank() && '.' !in name) { "Unsupported response header expression" }
            val values = requireNotNull(context.response) { "Response metadata is unavailable" }.headers
                .entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
                ?: error("Response header '$name' is unavailable")
            if (values.size == 1) JsonPrimitive(values.single()) else kotlinx.serialization.json.JsonArray(values.map(::JsonPrimitive))
        }
        reference.startsWith("\$steps.") -> stepValue(reference, context)
        else -> error("Unsupported Arazzo expression")
    }

    if (pointer == null) return root
    val path = pointer.removePrefix("#")
    return requireNotNull(root.atJsonPointer(path)) { "Arazzo expression target is missing" }
}

/** Syntax-only profile check for preflight; it never evaluates values or executes host code. */
internal fun isSupportedArazzoExpressionSyntax(expression: String): Boolean {
    val parts = expression.split("#", limit = 2)
    if (parts.size > 2) return false
    val reference = parts[0]
    val pointer = parts.getOrNull(1)
    val rootSupported = reference == "\$inputs" ||
        Regex("^\\${'$'}inputs(?:\\.[A-Za-z0-9_-]+)+$").matches(reference) ||
        Regex("^\\${'$'}steps\\.[A-Za-z0-9_-]+\\.outputs\\.[A-Za-z0-9_-]+$").matches(reference) ||
        Regex("^\\${'$'}steps\\.[A-Za-z0-9_-]+\\.response\\.body$").matches(reference) ||
        reference in setOf("\$statusCode", "\$response.statusCode", "\$response.body") ||
        Regex("^\\${'$'}response\\.header\\.[A-Za-z0-9-]+$").matches(reference)
    if (!rootSupported) return false
    if (pointer != null && (reference.startsWith("\$response.header.") || reference == "\$statusCode" || reference == "\$response.statusCode")) return false
    return pointer == null || isValidJsonPointerSyntax(pointer)
}

private fun stepValue(reference: String, context: ArazzoExpressionContext): JsonElement {
    val output = Regex("^\\${'$'}steps\\.([A-Za-z0-9_-]+)\\.outputs\\.([A-Za-z0-9_-]+)$").matchEntire(reference)
    if (output != null) {
        return requireNotNull(context.stepOutputs[output.groupValues[1]]?.get(output.groupValues[2])) {
            "Step output is unavailable"
        }
    }
    val body = Regex("^\\${'$'}steps\\.([A-Za-z0-9_-]+)\\.response\\.body$").matchEntire(reference)
    if (body != null) {
        val snapshot = requireNotNull(context.stepResponses[body.groupValues[1]]) { "Step response is unavailable" }
        return responseBody(snapshot.body)
    }
    error("Unsupported step expression")
}

private fun responseBody(body: BodyValue): JsonElement = when (body) {
    is BodyValue.JsonValue -> body.value
    BodyValue.Empty -> error("Response body is empty")
    BodyValue.Unavailable -> error("Response body is unavailable")
    is BodyValue.DecodeFailure -> error("Response body could not be decoded")
    is BodyValue.BytesValue -> error("Response body is not JSON")
}
