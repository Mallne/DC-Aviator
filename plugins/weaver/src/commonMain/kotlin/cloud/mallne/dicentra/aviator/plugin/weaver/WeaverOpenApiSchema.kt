package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.weaver.core.specification.ObjectType
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import cloud.mallne.dicentra.weaver.core.specification.compute.ArrayComputationDeclaration
import cloud.mallne.dicentra.weaver.core.specification.compute.MapComputationDeclaration
import cloud.mallne.dicentra.weaver.core.specification.compute.ObjectComputationDeclaration
import cloud.mallne.dicentra.weaver.core.specification.compute.ComputeDeclaration
import cloud.mallne.dicentra.weaver.core.model.WeaverObjectNotationInvocation
import cloud.mallne.dicentra.weaver.language.ast.expressions.WeaverContent
import cloud.mallne.dicentra.weaver.language.ast.expressions.TopLevelWeaverExpression
import cloud.mallne.dicentra.weaver.language.ast.expressions.NestedExpression
import cloud.mallne.dicentra.weaver.language.ast.expressions.TopLevelTypeCoercion
import cloud.mallne.dicentra.weaver.language.ast.expressions.ParameterAccessContent
import cloud.mallne.dicentra.weaver.language.ast.expressions.CurrentAccessor
import cloud.mallne.dicentra.weaver.language.ast.expressions.TypeCoercion
import cloud.mallne.dicentra.weaver.language.ast.expressions.StringLiteral
import cloud.mallne.dicentra.weaver.language.ast.expressions.NumberLiteral
import cloud.mallne.dicentra.weaver.language.ast.expressions.BooleanLiteral
import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.ReferenceOr

/** Derives the output JSON Schema from the transformation recipe's declared result types. */
fun WeaverSchema.toOpenApiSchema(inputSchema: JsonSchema): JsonSchema {
    return WeaverOpenApiDerivation(inputSchema).schemaFor(getRootLimboObject())
}

private class WeaverOpenApiDerivation(private val inputSchema: JsonSchema) {
    private fun inputSchemaAt(accessor: String): JsonSchema {
        val path = accessor.split('.')
            .filter(String::isNotBlank)
        var current = inputSchema
        for (segment in path) {
            val name = segment.substringBefore('[')
            if (current.type == null && current.properties == null && current.items == null && current.additionalProperties == null) return JsonSchema()
            if (name.isNotEmpty()) {
                val property = current.properties?.get(name)?.valueOrNull()
                current = property ?: when (val additional = current.additionalProperties) {
                    is AdditionalProperties.Allowed -> {
                        require(additional.value) { "Weaver accessor '$accessor' is not declared by the OpenAPI input schema" }
                        JsonSchema()
                    }
                    is AdditionalProperties.PSchema -> additional.value.valueOrNull()
                        ?: throw IllegalArgumentException("Weaver accessor '$accessor' uses an unresolved OpenAPI input schema")
                    null -> throw IllegalArgumentException("Weaver accessor '$accessor' is not declared by the OpenAPI input schema")
                }
            }
            repeat(segment.count { it == '[' }) {
                if (current.type == null && current.items == null) return JsonSchema()
                current = current.items?.valueOrNull()
                    ?: throw IllegalArgumentException("Weaver accessor '$accessor' requires an array item schema")
            }
        }
        return current
    }

    fun schemaFor(
        invocation: WeaverObjectNotationInvocation,
        visiting: Set<Pair<WeaverSchema, String>> = emptySet(),
    ): JsonSchema {
        invocation.coercion?.let { return coercionSchema(it) }
        val transform = invocation.limboObject
        val identity = invocation.schemaRoot to transform.key
        if (invocation.metaAccess == WeaverObjectNotationInvocation.MetaAccess.Accessor) {
            return inputSchemaAt(transform.accessor)
        }
        if (invocation.metaAccess == WeaverObjectNotationInvocation.MetaAccess.Parameter) {
            invocation.definedParameters[invocation.parameter]?.let { return schemaFor(it, visiting) }
            return scalarSchema(transform.parameters[invocation.parameter] ?: ObjectType.Undefined)
        }
        require(identity !in visiting) { "Recursive Weaver output schema reference '${transform.key}'" }
        val nextVisiting = visiting + identity
        return when (val compute = transform.compute) {
            is ObjectComputationDeclaration -> {
                if (compute.nested == null) return inputSchemaAt(transform.accessor)
                val required = mutableListOf<String>()
                val properties = compute.nested.orEmpty().associate { property ->
                    val child = property.content.resolveBound(invocation)
                    val childSchema = schemaFor(
                        child, nextVisiting,
                    )
                    val dependencies = child.inputDependencies()
                    if (!dependencies.allInputs && dependencies.accessors.all(::accessorGuaranteed)) required += property.name
                    property.name to ReferenceOr.Value(childSchema)
                }
                JsonSchema(type = JsonType.OBJECT, properties = properties, required = required.takeIf { it.isNotEmpty() })
            }
            is ArrayComputationDeclaration -> {
                val item = expressionSchema(compute.repeatAst, invocation, nextVisiting) {
                    inputSchemaAt(transform.accessor).items?.valueOrNull() ?: JsonSchema()
                }
                JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(item))
            }
            is MapComputationDeclaration -> {
                val valueSchema = expressionSchema(compute.valueRepeatAst, invocation, nextVisiting) {
                    val source = inputSchemaAt(transform.accessor)
                    (source.additionalProperties as? AdditionalProperties.PSchema)?.value?.valueOrNull() ?: JsonSchema()
                }
                JsonSchema(type = JsonType.OBJECT, additionalProperties = AdditionalProperties.PSchema(ReferenceOr.Value(valueSchema)))
            }
            is ComputeDeclaration -> {
                expressionSchema(compute.computeAst, invocation, nextVisiting,
                    fallback = scalarSchema(transform.type))
            }
        }
    }

    /** Only claim presence when every field/index on the source path is guaranteed. */
    private fun accessorGuaranteed(accessor: String): Boolean {
        var current = inputSchema
        for (segment in accessor.split('.')) {
            val name = segment.substringBefore('[')
            if (name.isNotEmpty()) {
                if (name !in current.required.orEmpty()) return false
                current = current.properties?.get(name)?.valueOrNull() ?: return false
            }
            if ('[' in segment) {
                val index = segment.substringAfter('[').substringBefore(']').toIntOrNull() ?: return false
                if (index < 0 || (current.minItems ?: 0) <= index) return false
                current = current.items?.valueOrNull() ?: return false
            }
        }
        return true
    }

    private fun expressionSchema(
        expression: TopLevelWeaverExpression,
        invocation: WeaverObjectNotationInvocation,
        visiting: Set<Pair<WeaverSchema, String>>,
        fallback: JsonSchema = JsonSchema(),
        itemSchema: (() -> JsonSchema)? = null,
    ): JsonSchema = when (expression) {
        is ParameterAccessContent -> if (expression.paramName == "value" && itemSchema != null) itemSchema()
            else invocation.definedParameters[expression.paramName]?.let { schemaFor(it, visiting) }
                ?: schemaFor(expression.resolveBound(invocation), visiting)
        is CurrentAccessor -> inputSchemaAt(invocation.limboObject.accessor)
        is WeaverContent -> schemaFor(expression.resolveBound(invocation), visiting)
        is NestedExpression -> expressionSchema(expression.expr, invocation, visiting, fallback, itemSchema)
        is TopLevelTypeCoercion -> coercionSchema(expression.type)
        is StringLiteral -> JsonSchema(type = JsonType.STRING)
        is NumberLiteral -> JsonSchema(type = JsonType.NUMBER)
        is BooleanLiteral -> JsonSchema(type = JsonType.BOOLEAN)
        else -> fallback
    }
}

private fun scalarSchema(type: ObjectType): JsonSchema = when (type) {
    ObjectType.Boolean -> JsonSchema(type = JsonType.BOOLEAN)
    ObjectType.String -> JsonSchema(type = JsonType.STRING)
    ObjectType.Number -> JsonSchema(type = JsonType.NUMBER)
    ObjectType.Object -> JsonSchema(type = JsonType.OBJECT, additionalProperties = AdditionalProperties.Allowed(true))
    ObjectType.List -> JsonSchema(type = JsonType.ARRAY)
    ObjectType.Map -> JsonSchema(type = JsonType.OBJECT, additionalProperties = AdditionalProperties.Allowed(true))
    ObjectType.Undefined -> JsonSchema()
}

private fun coercionSchema(type: TypeCoercion.Type): JsonSchema = JsonSchema(type = when (type) {
    TypeCoercion.Type.Integer, TypeCoercion.Type.AvailabilityInteger -> JsonType.INTEGER
    TypeCoercion.Type.Float -> JsonType.NUMBER
    TypeCoercion.Type.Boolean, TypeCoercion.Type.AvailabilityBoolean -> JsonType.BOOLEAN
    TypeCoercion.Type.String -> JsonType.STRING
})
