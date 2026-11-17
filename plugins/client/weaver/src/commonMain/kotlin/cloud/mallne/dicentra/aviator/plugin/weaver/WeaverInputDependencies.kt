package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.weaver.core.model.WeaverObjectNotationInvocation
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import cloud.mallne.dicentra.weaver.core.specification.compute.ArrayComputationDeclaration
import cloud.mallne.dicentra.weaver.core.specification.compute.ComputeDeclaration
import cloud.mallne.dicentra.weaver.core.specification.compute.MapComputationDeclaration
import cloud.mallne.dicentra.weaver.core.specification.compute.ObjectComputationDeclaration
import cloud.mallne.dicentra.weaver.core.specification.compute.expression.resolve
import cloud.mallne.dicentra.weaver.language.ast.expressions.BinaryExpression
import cloud.mallne.dicentra.weaver.language.ast.expressions.BooleanLiteral
import cloud.mallne.dicentra.weaver.language.ast.expressions.CurrentAccessor
import cloud.mallne.dicentra.weaver.language.ast.expressions.ExpressionInterpolation
import cloud.mallne.dicentra.weaver.language.ast.expressions.NestedExpression
import cloud.mallne.dicentra.weaver.language.ast.expressions.NumberLiteral
import cloud.mallne.dicentra.weaver.language.ast.expressions.ParameterAccessContent
import cloud.mallne.dicentra.weaver.language.ast.expressions.StringLiteral
import cloud.mallne.dicentra.weaver.language.ast.expressions.TernaryExpression
import cloud.mallne.dicentra.weaver.language.ast.expressions.TopLevelTypeCoercion
import cloud.mallne.dicentra.weaver.language.ast.expressions.UnaryExpression
import cloud.mallne.dicentra.weaver.language.ast.expressions.WeaverContent
import cloud.mallne.dicentra.weaver.language.ast.expressions.WeaverExpression

/** Conservative read set. Dynamic access and extension functions may consume the whole input. */
internal class WeaverInputDependencies(
    val accessors: Set<String> = emptySet(),
    val allInputs: Boolean = false,
) {
    operator fun plus(other: WeaverInputDependencies) = WeaverInputDependencies(
        accessors + other.accessors, allInputs || other.allInputs,
    )

    fun consumes(name: String): Boolean = allInputs || accessors.any {
        it.substringBefore('.').substringBefore('[') == name
    }
}

internal fun WeaverObjectNotationInvocation.inputDependencies(
    visiting: Set<Pair<WeaverSchema, String>> = emptySet(),
): WeaverInputDependencies {
    val identity = schemaRoot to limboObject.key
    if (metaAccess == WeaverObjectNotationInvocation.MetaAccess.Accessor) return accessorDependencies()
    if (metaAccess == WeaverObjectNotationInvocation.MetaAccess.Parameter) {
        return definedParameters[parameter]?.inputDependencies(visiting) ?: WeaverInputDependencies(allInputs = true)
    }
    if (identity in visiting) return WeaverInputDependencies(allInputs = true)
    val next = visiting + identity
    val arguments = definedParameters.values.fold(WeaverInputDependencies()) { result, argument ->
        result + argument.inputDependencies(next)
    }
    val reads = when (val computation = limboObject.compute) {
        is ComputeDeclaration -> expressionDependencies(computation.computeAst, next)
        is ObjectComputationDeclaration -> computation.nested?.fold(WeaverInputDependencies()) { result, property ->
            result + property.content.resolveBound(this).inputDependencies(next)
        } ?: accessorDependencies()
        is ArrayComputationDeclaration -> expressionDependencies(computation.loopAst, next) +
            expressionDependencies(computation.repeatAst, next)
        is MapComputationDeclaration -> expressionDependencies(computation.loopAst, next) +
            expressionDependencies(computation.keyRepeatAst, next) + expressionDependencies(computation.valueRepeatAst, next)
    }
    return arguments + reads
}

private fun WeaverObjectNotationInvocation.accessorDependencies(): WeaverInputDependencies {
    val dynamic = limboObject.accessorAst.any { element -> element.segment.any { it is ExpressionInterpolation } }
    // Computed indices can themselves read arbitrary inputs; don't discard potential dependencies.
    val computedIndex = limboObject.accessor.contains("[{")
    return WeaverInputDependencies(setOf(limboObject.accessor), allInputs = dynamic || computedIndex)
}

private fun WeaverObjectNotationInvocation.expressionDependencies(
    expression: WeaverExpression,
    visiting: Set<Pair<WeaverSchema, String>>,
): WeaverInputDependencies = when (expression) {
    is CurrentAccessor -> accessorDependencies()
    is ParameterAccessContent -> if (
        (limboObject.compute is ArrayComputationDeclaration || limboObject.compute is MapComputationDeclaration) &&
        expression.paramName in setOf("value", "key", "index")
    ) accessorDependencies() else definedParameters[expression.paramName]?.inputDependencies(visiting)
        ?: WeaverInputDependencies(allInputs = true)
    is WeaverContent -> expression.resolveBound(this).inputDependencies(visiting)
    is TopLevelTypeCoercion -> expressionDependencies(expression.content, visiting)
    is NestedExpression -> expressionDependencies(expression.expr, visiting)
    is UnaryExpression -> expressionDependencies(expression.expression, visiting)
    is BinaryExpression -> expressionDependencies(expression.left, visiting) + expressionDependencies(expression.right, visiting)
    is TernaryExpression -> expressionDependencies(expression.condition, visiting) +
        expressionDependencies(expression.trueBranch, visiting) + expressionDependencies(expression.falseBranch, visiting)
    is StringLiteral, is NumberLiteral, is BooleanLiteral -> WeaverInputDependencies()
    else -> WeaverInputDependencies(allInputs = true)
}

/** Parameter names used by this projection, excluding its own eagerly evaluated bindings. */
internal fun WeaverObjectNotationInvocation.bindingReads(
    visiting: Set<Pair<WeaverSchema, String>> = emptySet(),
): Set<String> {
    if (metaAccess == WeaverObjectNotationInvocation.MetaAccess.Parameter) return setOfNotNull(parameter)
    val identity = schemaRoot to limboObject.key
    if (identity in visiting) return limboObject.parameters.keys
    val next = visiting + identity
    fun reads(expression: WeaverExpression): Set<String> = when (expression) {
        is ParameterAccessContent -> setOf(expression.paramName)
        is CurrentAccessor -> if (limboObject.accessor.contains('#')) limboObject.parameters.keys else emptySet()
        is WeaverContent -> expression.resolve(schemaRoot, limboObject).bindingReads(next)
        is TopLevelTypeCoercion -> reads(expression.content)
        is NestedExpression -> reads(expression.expr)
        is UnaryExpression -> reads(expression.expression)
        is BinaryExpression -> reads(expression.left) + reads(expression.right)
        is TernaryExpression -> reads(expression.condition) + reads(expression.trueBranch) + reads(expression.falseBranch)
        is StringLiteral, is NumberLiteral, is BooleanLiteral -> emptySet()
        else -> limboObject.parameters.keys
    }
    val arguments = definedParameters.values.flatMap { it.bindingReads(next) }.toSet()
    if (metaAccess == WeaverObjectNotationInvocation.MetaAccess.Accessor) {
        return arguments + if (limboObject.accessor.contains('#')) limboObject.parameters.keys else emptySet()
    }
    val result = when (val computation = limboObject.compute) {
        is ComputeDeclaration -> reads(computation.computeAst)
        is ObjectComputationDeclaration -> computation.nested?.flatMap {
            it.content.resolve(schemaRoot, limboObject).bindingReads(next)
        }?.toSet() ?: limboObject.parameters.keys
        is ArrayComputationDeclaration -> reads(computation.loopAst) + reads(computation.repeatAst)
        is MapComputationDeclaration -> reads(computation.loopAst) + reads(computation.keyRepeatAst) + reads(computation.valueRepeatAst)
    }
    return arguments + result
}
