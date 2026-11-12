package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoExpressionContext
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.evaluateArazzoExpression
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.HttpResponseMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.ResponseSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArazzoExpressionTest {
    private val inputs = Json.parseToJsonElement("""{"enabled":true,"data":[1,null]}""")

    @Test
    fun preservesInputAndStepOutputJsonTypes() {
        val context = ArazzoExpressionContext(
            inputs = inputs,
            stepOutputs = mapOf("first" to JsonObject(mapOf("value" to JsonPrimitive(42)))),
        )
        assertEquals(JsonPrimitive(true), evaluateArazzoExpression("\$inputs#/enabled", context))
        assertEquals(JsonPrimitive(42), evaluateArazzoExpression("\$steps.first.outputs.value", context))
    }

    @Test
    fun resolvesJsonResponseStatusHeadersAndBodyPointer() {
        val body = BodyValue.JsonValue(Json.parseToJsonElement("""{"result":{"ok":true}}"""))
        val context = ArazzoExpressionContext(
            inputs = inputs,
            response = HttpResponseMetadata(202, mapOf("X-Trace" to listOf("one", "two"))),
            responseBody = body,
            stepResponses = mapOf("first" to ResponseSnapshot(body)),
        )
        assertEquals(JsonPrimitive(202), evaluateArazzoExpression("\$response.statusCode", context))
        assertEquals(Json.parseToJsonElement("""["one","two"]"""), evaluateArazzoExpression("\$response.header.x-trace", context))
        assertEquals(JsonPrimitive(true), evaluateArazzoExpression("\$steps.first.response.body#/result/ok", context))
    }

    @Test
    fun rejectsUnsupportedAndUnavailableExpressions() {
        val context = ArazzoExpressionContext(inputs)
        assertFailsWith<IllegalArgumentException> { evaluateArazzoExpression("\$response.statusCode", context) }
        assertFailsWith<IllegalStateException> { evaluateArazzoExpression("\$env.HOME", context) }
        assertFailsWith<IllegalArgumentException> { evaluateArazzoExpression("\$steps.unknown.outputs.value", context) }
        assertFailsWith<IllegalArgumentException> { evaluateArazzoExpression("\$steps.unknown.response.body", context) }
    }
}
