package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoExpressionContext
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.evaluateArazzoCriterion
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoCriterion
import cloud.mallne.dicentra.aviator.orchestration.model.HttpResponseMetadata
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArazzoCriteriaTest {
    private val context = ArazzoExpressionContext(
        inputs = Json.parseToJsonElement("{}"),
        response = HttpResponseMetadata(202),
    )

    @Test
    fun comparesStatusAndLiteralsUsingSupportedOperators() {
        assertTrue(evaluateArazzoCriterion(ArazzoCriterion("\$statusCode >= 200"), context))
        assertTrue(evaluateArazzoCriterion(ArazzoCriterion("\$statusCode == 202"), context))
        assertFalse(evaluateArazzoCriterion(ArazzoCriterion("\$statusCode < 200"), context))
        assertTrue(evaluateArazzoCriterion(ArazzoCriterion("'a == b' != 'a'"), context))
    }

    @Test
    fun rejectsUnsupportedCriterionKindsAndExpressionSyntax() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            evaluateArazzoCriterion(ArazzoCriterion("\$statusCode == 200", type = "jsonpath"), context)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            evaluateArazzoCriterion(ArazzoCriterion("1 + 2 == 3"), context)
        }
    }
}
