package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoCriterion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Evaluates the initial profile's simple comparison conditions; no host-language evaluation. */
internal fun evaluateArazzoCriterion(
    criterion: ArazzoCriterion,
    context: ArazzoExpressionContext,
): Boolean {
    require(criterion.type == null || criterion.type == "simple") { "Only simple criteria are supported" }
    require(criterion.context == null) { "Simple criteria do not accept context" }
    val parts = splitComparison(criterion.condition) ?: error("Unsupported simple criterion")
    val left = criterionValue(parts.first, context)
    val right = criterionValue(parts.third, context)
    return compare(left, parts.second, right)
}

internal fun isSupportedArazzoCriterionSyntax(condition: String): Boolean {
    val parts = splitComparison(condition) ?: return false
    return isSupportedOperand(parts.first) && isSupportedOperand(parts.third)
}

private fun isSupportedOperand(value: String): Boolean {
    if (value.startsWith("\$")) return isSupportedArazzoExpressionSyntax(value)
    if (value.startsWith('\'') && value.endsWith('\'') && value.length >= 2) return true
    return try {
        Json.parseToJsonElement(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}

private fun splitComparison(condition: String): Triple<String, String, String>? {
    val operators = listOf("==", "!=", ">=", "<=", ">", "<")
    var quote: Char? = null
    var escaped = false
    condition.forEachIndexed { index, char ->
        if (escaped) escaped = false
        else if (char == '\\' && quote != null) escaped = true
        else if (quote != null && char == quote) quote = null
        else if (quote == null && (char == '\'' || char == '"')) quote = char
        else if (quote == null) {
            val operator = operators.firstOrNull { condition.startsWith(it, index) } ?: return@forEachIndexed
            val left = condition.substring(0, index).trim()
            val right = condition.substring(index + operator.length).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) return Triple(left, operator, right)
        }
    }
    return null
}

private fun criterionValue(value: String, context: ArazzoExpressionContext): JsonElement =
    if (value.startsWith("\$")) evaluateArazzoExpression(value, context)
    else if (value.startsWith('\'') && value.endsWith('\'') && value.length >= 2) {
        JsonPrimitive(value.substring(1, value.lastIndex))
    } else Json.parseToJsonElement(value)

private fun compare(left: JsonElement, operator: String, right: JsonElement): Boolean {
    if (operator == "==") return left == right
    if (operator == "!=") return left != right
    val leftPrimitive = left as? JsonPrimitive ?: error("Relational comparison requires primitive values")
    val rightPrimitive = right as? JsonPrimitive ?: error("Relational comparison requires primitive values")
    val leftNumber = if (leftPrimitive.isString) null else leftPrimitive.content.toDoubleOrNull()
    val rightNumber = if (rightPrimitive.isString) null else rightPrimitive.content.toDoubleOrNull()
    val comparison = when {
        leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
        leftPrimitive.isString && rightPrimitive.isString -> leftPrimitive.content.compareTo(rightPrimitive.content)
        else -> error("Relational comparison requires two numbers or two strings")
    }
    return when (operator) {
        ">" -> comparison > 0
        ">=" -> comparison >= 0
        "<" -> comparison < 0
        "<=" -> comparison <= 0
        else -> error("Unsupported comparison operator")
    }
}
