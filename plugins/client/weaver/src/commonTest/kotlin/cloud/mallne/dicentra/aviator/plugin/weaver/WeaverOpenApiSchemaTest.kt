package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.weaver.core.specification.ObjectType
import cloud.mallne.dicentra.weaver.core.specification.Wol
import cloud.mallne.dicentra.weaver.core.specification.weaverSchema
import cloud.mallne.dicentra.weaver.core.Weaver
import cloud.mallne.dicentra.aviator.koas.typed.routes
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.openapi.PathItem
import io.ktor.http.ContentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.ReferenceOr.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WeaverOpenApiSchemaTest {
    @Test
    fun boundObjectPropertiesRetainSourceConstraintsAndRequiredness() {
        val source = JsonSchema(type = JsonType.STRING, minLength = 1, maxLength = 9)
        val recipe = weaverSchema("bound") {
            root("result", "#out#(value=#source#)")
            transform("source", "name") { value("{}", ObjectType.String) }
            transform("out", "unused", parameters = mapOf("value" to ObjectType.String)) {
                objectValue { property("native", "##value##") }
            }
        }
        val input = JsonSchema(type = JsonType.OBJECT, required = listOf("name"),
            properties = mapOf("name" to Value(source)))
        val output = recipe.toOpenApiSchema(input)
        assertEquals(source, output.properties?.get("native")?.valueOrNull())
        assertEquals(listOf("native"), output.required)
        assertEquals(null, recipe.toOpenApiSchema(input.copy(required = null)).required)
    }

    @Test
    fun forwardedObjectArgumentsRetainTheCallersSourceAcrossScopes() {
        val source = JsonSchema(type = JsonType.STRING, minLength = 1, maxLength = 9)
        val child = weaverSchema("child") {
            root("result", Wol.reference("echo"))
            transform("echo", "unused", parameters = mapOf("value" to ObjectType.String)) {
                value("{##value##}", ObjectType.String)
            }
        }
        val recipe = weaverSchema("forwarded") {
            root("result", "#out#(outer=#source#)")
            transform("source", "name") { value("{}", ObjectType.String) }
            transform("out", "unused", parameters = mapOf("outer" to ObjectType.String)) {
                objectValue { property("native", "child.#echo#(value=##outer##)") }
            }
            nested(child)
        }
        val output = recipe.toOpenApiSchema(JsonSchema(type = JsonType.OBJECT, required = listOf("name"),
            properties = mapOf("name" to Value(source))))
        assertEquals(source, output.properties?.get("native")?.valueOrNull())
        assertEquals(listOf("native"), output.required)
        assertEquals(buildJsonObject { put("native", "test") },
            Weaver().engine(recipe).executeElement(buildJsonObject { put("name", "test") }))
    }

    @Test
    fun typelessSchemasRetainDeclaredPropertyAndItemConstraints() {
        val source = JsonSchema(type = JsonType.INTEGER, minimum = 1.0, maximum = 5.0)
        val propertyRecipe = weaverSchema("property") {
            root("result", Wol.reference("value"))
            transform("value", "id") { value("{}", ObjectType.Number) }
        }
        assertEquals(source, propertyRecipe.toOpenApiSchema(JsonSchema(properties = mapOf("id" to Value(source)))))
        val itemRecipe = weaverSchema("item") {
            root("result", Wol.reference("value"))
            transform("value", "[0]") { value("{}", ObjectType.Number) }
        }
        assertEquals(source, itemRecipe.toOpenApiSchema(JsonSchema(items = Value(source))))
    }

    @Test
    fun ordinaryValueParameterUsesItsBoundArgumentSchema() {
        val source = JsonSchema(type = JsonType.STRING, minLength = 1, maxLength = 9)
        val recipe = weaverSchema("parameter") {
            root("result", "#echo#(value=#source#)")
            transform("source", "name") { value("{}", ObjectType.String) }
            transform("echo", "unused", parameters = mapOf("value" to ObjectType.String)) {
                value("{##value##}", ObjectType.String)
            }
        }
        val input = JsonSchema(type = JsonType.OBJECT, properties = mapOf("name" to Value(source)))
        assertEquals(source, recipe.toOpenApiSchema(input))
        assertEquals(JsonPrimitive("test"), Weaver().engine(recipe).executeElement(buildJsonObject { put("name", "test") }))
    }

    @Test
    fun directRootArrayAccessUsesTheItemSchema() {
        val item = JsonSchema(type = JsonType.INTEGER, minimum = 1.0)
        val recipe = weaverSchema("first") {
            root("result", Wol.reference("first"))
            transform("first", "[0]") { value("{}", ObjectType.Number) }
        }
        assertEquals(item, recipe.toOpenApiSchema(JsonSchema(type = JsonType.ARRAY, items = Value(item))))
    }

    @Test
    fun requiredInputsAndConstantsProduceRequiredFieldsButOptionalInputsDoNot() {
        val recipe = weaverSchema("required") {
            root("result", Wol.reference("out"))
            transform("out", "unused") {
                objectValue {
                    source("providerId", Wol.path("id"), ObjectType.String)
                    source("label", Wol.path("label"), ObjectType.String)
                    use("kind", "constant")
                    source("timezone", Wol.path("timezone"), ObjectType.String)
                }
            }
            transform("constant", "unused") { value(Wol.stringLiteral("record"), ObjectType.String) }
        }
        val input = JsonSchema(type = JsonType.OBJECT, required = listOf("id"), properties = mapOf(
            "id" to Value(JsonSchema(type = JsonType.STRING)),
            "label" to Value(JsonSchema(type = JsonType.STRING)),
            "timezone" to Value(JsonSchema(type = JsonType.STRING)),
        ))
        assertEquals(setOf("providerId", "kind"), recipe.toOpenApiSchema(input).required?.toSet())
    }

    @Test
    fun passthroughPrimitiveArrayRetainsItsItemsAndCanBeUsedByAviator() {
        val item = JsonSchema(type = JsonType.INTEGER, minimum = 1.0)
        val input = JsonSchema(type = JsonType.OBJECT, properties = mapOf(
            "ids" to Value(JsonSchema(type = JsonType.ARRAY, items = Value(item))),
        ))
        val recipe = weaverSchema("array") {
            root("result", Wol.reference("ids"))
            transform("ids", "ids", parameters = mapOf("value" to ObjectType.Number)) { array() }
        }
        val output = recipe.toOpenApiSchema(input)
        assertEquals(item, output.items?.valueOrNull())
        val definition = OpenApiDoc.build {
            info = OpenApiInfo(title = "Array output", version = "1")
        }.copy(paths = mapOf("/array" to Value(PathItem(get = Operation.build {
            operationId = "ArrayOutput"
            responses { response(200) { ContentType.Application.Json { schema = output } } }
        }))))
        assertEquals(1, definition.routes().size)
    }

    @Test
    fun nestedSchemasResolveDuplicateTransformNamesInTheirOwnScope() {
        fun child(key: String, field: String) = weaverSchema(key) {
            root("result", Wol.reference("result"))
            transform("result", field) { value("{}", ObjectType.String) }
        }
        val recipe = weaverSchema("root") {
            root("result", Wol.reference("result"))
            transform("result", "unusedAnchor") {
                objectValue {
                    property("first", "first.#result#")
                    property("second", "second")
                }
            }
            nested(child("first", "text"))
            nested(child("second", "number"))
        }
        val input = JsonSchema(type = JsonType.OBJECT, properties = mapOf(
            "text" to Value(JsonSchema(type = JsonType.STRING)),
            "number" to Value(JsonSchema(type = JsonType.NUMBER)),
        ))
        val output = recipe.toOpenApiSchema(input)
        assertEquals(JsonType.STRING, output.properties?.get("first")?.valueOrNull()?.type)
        assertEquals(JsonType.NUMBER, output.properties?.get("second")?.valueOrNull()?.type)
        assertEquals(buildJsonObject { put("first", "hello"); put("second", 2) },
            Weaver().engine(recipe).executeElement(buildJsonObject { put("text", "hello"); put("number", 2) }))
    }

    @Test
    fun explicitObjectConstructionDoesNotReadItsAnchor() {
        val recipe = weaverSchema("object") {
            root("result", Wol.reference("out"))
            transform("out", "notAnInput") {
                objectValue { source("name", Wol.path("name"), ObjectType.String) }
            }
        }
        val input = JsonSchema(type = JsonType.OBJECT, properties = mapOf(
            "name" to Value(JsonSchema(type = JsonType.STRING)),
        ))
        assertEquals(JsonType.STRING, recipe.toOpenApiSchema(input).properties?.get("name")?.valueOrNull()?.type)
    }

    @Test
    fun unusedConstantAnchorsAreIgnoredButRealInputDependenciesAreChecked() {
        val constant = weaverSchema("constant") {
            root("result", Wol.reference("constant"))
            transform("constant", "notAnInput") { value(Wol.stringLiteral("fixed"), ObjectType.String) }
        }
        assertEquals(JsonType.STRING, constant.toOpenApiSchema(JsonSchema(type = JsonType.OBJECT)).type)
        val passthrough = weaverSchema("passthrough") {
            root("result", Wol.reference("source"))
            transform("source", "notAnInput") { value("{}", ObjectType.String) }
        }
        assertFailsWith<IllegalArgumentException> {
            passthrough.toOpenApiSchema(JsonSchema(type = JsonType.OBJECT))
        }
    }

    @Test
    fun scopedReferenceCyclesAreStillRejected() {
        val recipe = weaverSchema("cycle") {
            root("result", Wol.reference("result"))
            transform("result", "unused") { objectValue { use("self", "result") } }
        }
        assertFailsWith<IllegalArgumentException> { recipe.toOpenApiSchema(JsonSchema(type = JsonType.OBJECT)) }
    }

    @Test
    fun indexedAccessTraversesTheInputItemSchemaAndRetainsConstraints() {
        val scalar = JsonSchema(type = JsonType.INTEGER, minimum = 1.0, maximum = 9.0)
        val input = JsonSchema(type = JsonType.OBJECT, properties = mapOf(
            "items" to Value(JsonSchema(type = JsonType.ARRAY, items = Value(
                JsonSchema(type = JsonType.OBJECT, properties = mapOf("id" to Value(scalar))),
            ))),
        ))
        val recipe = weaverSchema("indexed") {
            root("result", Wol.reference("id"))
            transform("id", "items[0].id") { value("{}", ObjectType.Number) }
        }
        assertEquals(scalar, recipe.toOpenApiSchema(input))
    }
}
