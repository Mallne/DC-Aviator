package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.execution.BasicExecutionContext
import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.koas.typed.Model
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.koas.typed.Model.Enum
import cloud.mallne.dicentra.aviator.koas.typed.NamingContext
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.koas.typed.TemplateParser.parsePath
import cloud.mallne.dicentra.weaver.core.Weaver
import cloud.mallne.dicentra.weaver.core.specification.LimboObjectDeclaration
import cloud.mallne.dicentra.weaver.core.specification.ObjectType
import cloud.mallne.dicentra.weaver.core.specification.PropertyDeclaration
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import cloud.mallne.dicentra.weaver.core.specification.Wol
import cloud.mallne.dicentra.weaver.core.specification.weaverSchema
import cloud.mallne.dicentra.weaver.core.specification.compute.ComputeDeclaration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.openapi.Parameter
import io.ktor.openapi.PathItem
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.GenericElementWrapper
import io.ktor.openapi.ReferenceOr.Value
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import io.ktor.http.HttpMethod
import io.ktor.openapi.ParameterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.reflect.typeOf

class WeaverServiceObjectTest {
    @Test
    fun unusedRootBindingsDoNotAddSerializationDependencies() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("unusedBinding") {
            root("parameters", "#out#(value=#source#,ignored=#headerSource#)")
            transform("source", "name") { value("{}", ObjectType.String) }
            transform("headerSource", "authorization") { value("{}", ObjectType.String) }
            transform("out", "unused", parameters = mapOf("value" to ObjectType.String, "ignored" to ObjectType.String)) {
                objectValue { property("native", "##value##") }
            }
        })
        val header = RequestParameter.Single(WireOnly("token"), stringify = { it.text })
        val route = routeWithInputs(listOf(
            Route.Input("name", Model.Primitive.String(null, null, null), true, ParameterType.query, null),
            Route.Input("authorization", Model.Primitive.String(null, null, null), false, ParameterType.header, null),
        ))
        val parameters = RequestParameters(mutableMapOf("name" to RequestParameter.Single("test"), "authorization" to header))
        options.adaptRequestParameters(parameters, route, Weaver(), Json)
        assertEquals("test", parameters["native"]?.toString())
        assertEquals(header, parameters["authorization"])
    }

    @Test
    fun boundObjectPropertiesDoNotConsumeUnrelatedCustomHeaders() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("bound") {
            root("parameters", "#out#(value=#source#)")
            transform("source", "name") { value("{}", ObjectType.String) }
            transform("out", "unused", parameters = mapOf("value" to ObjectType.String)) {
                objectValue { property("native", "##value##") }
            }
        })
        val header = RequestParameter.Single(WireOnly("token"), stringify = { it.text })
        val route = routeWithInputs(listOf(
            Route.Input("name", Model.Primitive.String(null, null, null), true, ParameterType.query, null),
            Route.Input("authorization", Model.Primitive.String(null, null, null), false, ParameterType.header, null),
        ))
        val parameters = RequestParameters(mutableMapOf("name" to RequestParameter.Single("test"),
            "authorization" to header))
        val dispatch = options.adaptRequestParameters(parameters, route, Weaver(), Json)
        assertEquals("test", parameters["native"]?.toString())
        assertEquals(header, parameters["authorization"])
        assertEquals(true, dispatch.parameter.single { it.name == "native" }.required)
    }

    @Test
    fun renamedRequiredInputsUseASeparateProviderRoute() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("rename") {
            root("parameters", Wol.reference("out"))
            transform("out", "unused") { objectValue { source("native", Wol.path("logical"), ObjectType.String) } }
        })
        val route = routeWithInputs(listOf(Route.Input("logical", Model.Primitive.String(null, null, null),
            true, ParameterType.query, null))).copy(parameter = listOf(
            Parameter(name = "logical", `in` = ParameterType.query, required = true,
                schema = Value(JsonSchema(type = JsonType.STRING))),
        ))
        val parameters = RequestParameters(mutableMapOf("logical" to RequestParameter.Single("test")))
        val providerRoute = options.adaptRequestParameters(parameters, route, Weaver(), Json)
        assertEquals("/forecast?native=test", providerRoute.parsePath(parameters.toStringList()))
        assertEquals(listOf("logical"), route.parameter.map { it.name })
        assertFailsWith<IllegalArgumentException> {
            options.adaptRequestParameters(RequestParameters(), route, Weaver(), Json)
        }
        val optionalCaller = route.copy(
            input = route.input.map { it.copy(isRequired = false) },
            parameter = route.parameter + Parameter(name = "native", `in` = ParameterType.query, required = true),
        )
        assertFailsWith<IllegalArgumentException> {
            options.adaptRequestParameters(RequestParameters(), optionalCaller, Weaver(), Json)
        }
    }

    @Test
    fun nestedAccessorRequirednessUsesRootAndSchemaPropertyRequirements() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("nestedRequired") {
            root("parameters", Wol.reference("out"))
            transform("out", "unused") { objectValue { source("native", Wol.path("address").field("city"), ObjectType.String) } }
        })
        fun route(requiredAddress: Boolean, requiredCity: Boolean): Route {
            val city = Model.Object.Property("city", Model.Primitive.String(null, null, null), requiredCity,
                !requiredCity, null)
            val address = Model.Object(NamingContext.Named("address"), null, listOf(city), emptyList())
            return routeWithInputs(listOf(Route.Input("address", address, requiredAddress,
                ParameterType.query, null)))
        }
        val addressValue = buildJsonObject { put("city", "X") }
        val params = RequestParameters(mutableMapOf("address" to RequestParameter.Single(addressValue)))
        val required = options.adaptRequestParameters(params, route(requiredAddress = true, requiredCity = true), Weaver(), Json)
        assertEquals(true, required.parameter.single { it.name == "native" }.required)

        val optionalNested = options.adaptRequestParameters(RequestParameters(mutableMapOf(
            "address" to RequestParameter.Single(addressValue))),
            route(requiredAddress = true, requiredCity = false), Weaver(), Json)
        assertEquals(false, optionalNested.parameter.single { it.name == "native" }.required)

        val optionalRoot = options.adaptRequestParameters(RequestParameters(mutableMapOf(
            "address" to RequestParameter.Single(addressValue))),
            route(requiredAddress = false, requiredCity = true), Weaver(), Json)
        assertEquals(false, optionalRoot.parameter.single { it.name == "native" }.required)
    }

    @Test
    fun missingOptionalRootBindingDoesNotDiscardAConstantField() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("bindings") {
            root("parameters", "#out#(value=#optionalSource#)")
            transform("optionalSource", "optional") { value("{}", ObjectType.String) }
            transform("constant", "unused") { value(Wol.stringLiteral("kept"), ObjectType.String) }
            transform("out", "unused", parameters = mapOf("value" to ObjectType.String)) {
                objectValue { use("constant", "constant"); property("optional", "##value##") }
            }
        })
        val route = routeWithInputs(listOf(Route.Input("optional", Model.Primitive.String(null, null, null),
            false, ParameterType.query, null)))
        val parameters = RequestParameters()
        options.adaptRequestParameters(parameters, route, Weaver(), Json)
        assertEquals(setOf("constant"), parameters.keys)
        assertEquals("kept", parameters["constant"]?.toString())
    }

    @Test
    fun arrayDefaultsPreserveTypesOverridesAndAbsence() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("arrayDefaults") {
            root("parameters", Wol.reference("out"))
            transform("out", "unused") {
                objectValue { use("ids", "ids"); use("optional", "optional"); use("empty", "empty") }
            }
            listOf("ids", "optional", "empty").forEach { name ->
                transform(name, name, parameters = mapOf("value" to ObjectType.Number)) { array() }
            }
        })
        val item = Value(JsonSchema(type = JsonType.INTEGER))
        val definition = OpenApiDoc.build {
            info = OpenApiInfo(title = "Array defaults", version = "1")
        }.copy(paths = mapOf("/arrays" to Value(PathItem(get = Operation.build {
            operationId = "ArrayDefaults"
        }.copy(parameters = listOf(
            Value(Parameter(name = "ids", `in` = ParameterType.query, required = true,
                schema = Value(JsonSchema(type = JsonType.ARRAY, items = item,
                    default = GenericElementWrapper(listOf(1, 2), ListSerializer(Int.serializer())))))),
            Value(Parameter(name = "optional", `in` = ParameterType.query,
                schema = Value(JsonSchema(type = JsonType.ARRAY, items = item)))),
            Value(Parameter(name = "empty", `in` = ParameterType.query,
                schema = Value(JsonSchema(type = JsonType.ARRAY, items = item,
                    default = GenericElementWrapper(emptyList<Int>(), ListSerializer(Int.serializer())))))),
        ))))))
        listOf(definition, Json.decodeFromString(OpenApiDoc.serializer(),
            Json.encodeToString(OpenApiDoc.serializer(), definition))).forEach { doc ->
            val route = doc.routes().single()
            val parameters = RequestParameters()
            options.adaptRequestParameters(parameters, route, Weaver(), Json)
            assertEquals(listOf("1", "2"), (parameters["ids"] as RequestParameter.Multi<*>).toStringList())
            assertEquals(emptyList(), (parameters["empty"] as RequestParameter.Multi<*>).toStringList())
            assertNull(parameters["optional"])

            parameters["ids"] = RequestParameter.Multi(listOf(9))
            options.adaptRequestParameters(parameters, route, Weaver(), Json)
            assertEquals(listOf("9"), (parameters["ids"] as RequestParameter.Multi<*>).toStringList())
            parameters["ids"] = RequestParameter.Multi(emptyList<Int>())
            options.adaptRequestParameters(parameters, route, Weaver(), Json)
            assertEquals(emptyList(), (parameters["ids"] as RequestParameter.Multi<*>).toStringList())
        }
    }

    @Test
    fun nestedNonDefaultRootKeepsItsSelectionAndOuterArgumentBindings() {
        val child = weaverSchema("child") {
            root("default", Wol.reference("default"))
            transform("default", "unrelated") {
                objectValue { source("wrong", Wol.path("unrelated"), ObjectType.String) }
            }
            transform("alternate", "unused", parameters = mapOf("value" to ObjectType.String)) {
                objectValue {
                    property("native", "##value##")
                    source("kept", Wol.path("keep"), ObjectType.String)
                }
            }
        }
        val recipe = weaverSchema("outer") {
            root("parameters", "child.#alternate#(value=#source#)")
            transform("source", "logical") { value("{}", ObjectType.String) }
            nested(child)
        }
        val options = WeaverServiceObject(requestParametersSchema = recipe)
        val route = routeWithInputs(listOf(
            Route.Input("logical", Model.Primitive.String(null, null, null), true, ParameterType.query, null),
            Route.Input("keep", Model.Primitive.String(null, null, null), true, ParameterType.query, null),
        ))
        val parameters = RequestParameters(mutableMapOf("logical" to RequestParameter.Single("bound"),
            "keep" to RequestParameter.Single("retained")))
        options.adaptRequestParameters(parameters, route, Weaver(), Json)
        assertEquals(setOf("native", "kept"), parameters.keys)
        assertEquals("bound", parameters["native"]?.toString())
        assertEquals("retained", parameters["kept"]?.toString())
    }

    private class WireOnly(val text: String)

    @Test
    fun discardedCustomInputsAndUnrelatedHeadersDoNotRequireJsonSerializers() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("projection") {
            root("parameters", Wol.reference("out"))
            transform("out", "unused") { objectValue { source("name", Wol.path("name"), ObjectType.String) } }
        })
        val discarded = RequestParameter.Single(WireOnly("filter"), stringify = { it.text })
        val header = RequestParameter.Single(WireOnly("token"), stringify = { it.text })
        val route = routeWithInputs(listOf(
            Route.Input("name", Model.Primitive.String(null, null, null), true, ParameterType.query, null),
            Route.Input("filter", Model.Primitive.String(null, null, null), false, ParameterType.query, null),
            Route.Input("authorization", Model.Primitive.String(null, null, null), false, ParameterType.header, null),
        ))
        val parameters = RequestParameters(mutableMapOf("name" to RequestParameter.Single("test"),
            "filter" to discarded, "authorization" to header))
        options.adaptRequestParameters(parameters, route, Weaver(), Json)
        assertEquals("test", parameters["name"]?.toString())
        assertNull(parameters["filter"])
        assertEquals(header, parameters["authorization"])
    }

    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Serializable
    data class CallerBody(val logical: String)

    @Test
    fun enumDefaultsFromOpenApiAreProjectedAsValuesNotJsonText() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("enumDefaults") {
            root("parameters", Wol.reference("out"))
            transform("out", "unused") {
                objectValue {
                    source("mode", Wol.path("mode"), ObjectType.String)
                    source("count", Wol.path("count"), ObjectType.Number, coercion = "i")
                    source("open", Wol.path("open"), ObjectType.String)
                }
            }
        })
        val definition = OpenApiDoc.build {
            info = OpenApiInfo(title = "Enum defaults", version = "1")
        }.copy(paths = mapOf("/enum" to Value(PathItem(get = Operation.build { operationId = "EnumDefaults" }.copy(
            parameters = listOf(
                Value(Parameter(name = "mode", `in` = ParameterType.query, required = true,
                    schema = Value(JsonSchema(type = JsonType.STRING,
                        enum = listOf(GenericElementWrapper("auto", String.serializer())),
                        default = GenericElementWrapper("auto", String.serializer()))))),
            ),
        )))))
        // Test both in-memory specs (GenericElementWrapper) and persisted JSON specs.
        val definitions = listOf(definition, Json.decodeFromString(OpenApiDoc.serializer(),
            Json.encodeToString(OpenApiDoc.serializer(), definition)))
        definitions.forEach { doc ->
            val original = doc.routes().single()
            val route = original.copy(input = original.input + listOf(Route.Input("open",
                Enum.Open(NamingContext.Named("Open"), listOf("other"), "\"custom\"", null),
                true, ParameterType.query, null), Route.Input("count",
                Enum.Closed(NamingContext.Named("Count"), Model.Primitive.Int(null, null, null), listOf("7"), "7", null),
                true, ParameterType.query, null)))
            val parameters = RequestParameters()
            options.adaptRequestParameters(parameters, route, Weaver(), Json)
            assertEquals("auto", parameters["mode"]?.toString())
            assertEquals("7", parameters["count"]?.toString())
            assertEquals("custom", parameters["open"]?.toString())
            parameters["mode"] = RequestParameter.Single("override")
            options.adaptRequestParameters(parameters, route, Weaver(), Json)
            assertEquals("override", parameters["mode"]?.toString())
        }
    }

    @Test
    fun bodyProjectionUsesTheOutputSerializerInsteadOfTheInputDto() {
        val options = WeaverServiceObject(requestBodySchema = weaverSchema("body") {
            root("body", Wol.reference("provider"))
            transform("provider", "logical") {
                objectValue { source("native", Wol.path("logical"), ObjectType.String) }
            }
        })
        val holder = object : AviatorServiceDataHolder {
            override val serviceLocator = ServiceLocator("test")
            override val plugins = emptyList<AviatorPluginInstance>()
            override val options: ServiceOptions = options.usable()
            override val route = routeWithInputs(emptyList())
            override val oas = OpenApiDoc.build { }
            override val serializers: List<SerialFormat> = listOf(Json)
        }
        val context = BasicExecutionContext(
            dataHolder = holder,
            outputClazz = Triple(String::class, typeOf<String>(), String.serializer()),
            body = CallerBody("value"),
            bodyClazz = Triple(CallerBody::class, typeOf<CallerBody>(), CallerBody.serializer()),
        )

        options.adaptRequestBody(context, Weaver())

        // Observe through the pipeline's erased body type, just as request adapters do.
        val body: Any? = (context as BasicExecutionContext<*, *>).body
        assertEquals(buildJsonObject { put("native", "value") }, body)
        assertEquals(JsonElement.serializer().descriptor, context.bodyClazz?.third?.descriptor)
    }

    @Test
    fun missingOptionalNumberIsOmittedButAnExplicitEmptyStringIsPreserved() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("optional") {
            root("parameters", Wol.reference("mapped"))
            transform("mapped", "label") {
                objectValue {
                    source("elevation", Wol.path("elevation"))
                    source("label", Wol.path("label"), ObjectType.String)
                }
            }
        })
        val route = routeWithInputs(listOf(
            Route.Input("elevation", Model.Primitive.Double(null, null, null), false, ParameterType.query, null),
            Route.Input("label", Model.Primitive.String(null, null, null), false, ParameterType.query, null),
        ))
        val parameters = RequestParameters(mutableMapOf("label" to RequestParameter.Single("")))
        options.adaptRequestParameters(parameters, route, Weaver(), Json)
        assertNull(parameters["elevation"])
        assertEquals("", parameters["label"]?.toString())

        assertFailsWith<IllegalArgumentException> {
            options.adaptRequestParameters(RequestParameters(), route.copy(
                input = route.input.map { it.copy(isRequired = true) },
            ), Weaver(), Json)
        }
    }

    @Test
    fun arrayParametersKeepTheirCardinalityAndElementTypes() {
        val options = WeaverServiceObject(requestParametersSchema = weaverSchema("arrays") {
            root("parameters", Wol.reference("mapped"))
            transform("mapped", "ids") { objectValue { use("ids", "ids") } }
            transform("ids", "ids", parameters = mapOf("value" to ObjectType.Number)) { array() }
        })
        val parameters = RequestParameters(mutableMapOf("ids" to RequestParameter.Multi(listOf(1, 2))))
        val route = routeWithInputs(listOf(Route.Input("ids",
            Model.Collection.List(Model.Primitive.Int(null, null, null), null, null, null),
            false, ParameterType.query, null)))
        options.adaptRequestParameters(parameters, route, Weaver(), Json)
        assertEquals(listOf("1", "2"), (parameters["ids"] as RequestParameter.Multi<*>).toStringList())
    }

    @Test
    fun missingSchemaMeansNoTransformationIsConfigured() {
        val options = buildJsonObject {
            put("customExtension", "retained by the service definition")
        }

        val decoded = lenientJson.decodeFromJsonElement<WeaverServiceObject>(options)

        assertNull(decoded.schema)
    }

    @Test
    fun optionsMetadataCanBeIgnoredWhileTheConfiguredSchemaTransformsData() {
        val schema = WeaverSchema(
            key = "temperature-projection",
            root = PropertyDeclaration(name = "result", value = "#temperature#"),
            transforms = listOf(
                LimboObjectDeclaration(
                    key = "temperature",
                    accessor = "current.temperature",
                    compute = ComputeDeclaration(type = ObjectType.Number),
                ),
            ),
        )
        val options = buildJsonObject {
            put("customExtension", "provider configuration remains outside Weaver")
            put("x-dicentra-weaver-schema", lenientJson.encodeToJsonElement(WeaverSchema.serializer(), schema))
        }

        val decoded = lenientJson.decodeFromJsonElement<WeaverServiceObject>(options)
        val input = buildJsonObject {
            put("current", buildJsonObject { put("temperature", 18) })
        }

        val transformed = Weaver().engine(assertNotNull(decoded.schema)).executeElement(input)

        assertEquals(JsonPrimitive(18), transformed)
    }

    @Test
    fun requestParameterRecipeMapsAndDropsInputsAndOpenApiDefaultsAreApplied() {
        val requestParameters = weaverSchema("request-parameters") {
            root("parameters", Wol.reference("mapped"))
            transform("mapped", Wol.path("lat").asExpression()) {
                objectValue {
                    source("latitude", Wol.path("lat"), ObjectType.String)
                    source("longitude", Wol.path("lon"), ObjectType.String)
                    source("timezone", Wol.path("timezone"), ObjectType.String)
                    source("forecast_days", Wol.path("forecast_days"), ObjectType.String)
                }
            }
        }
        val options = WeaverServiceObject(
            requestParametersSchema = requestParameters,
        )
        val route = routeWithInputs(listOf(
            Route.Input("lat", Model.Primitive.String(null, null, null), false, ParameterType.query, null),
            Route.Input("lon", Model.Primitive.String(null, null, null), false, ParameterType.query, null),
            Route.Input("latitude", Model.Primitive.Double(null, null, null), true, ParameterType.query, null),
            Route.Input("longitude", Model.Primitive.Double(null, null, null), true, ParameterType.query, null),
            Route.Input("timezone", Model.Primitive.String("auto", null, null), false, ParameterType.query, null),
            Route.Input("forecast_days", Model.Primitive.String("7", null, null), false, ParameterType.query, null),
            Route.Input("date", Model.Primitive.String(null, null, null), false, ParameterType.query, null),
        ))
        val parameters = RequestParameters(mutableMapOf(
            "lat" to RequestParameter.Single(52.52),
            "lon" to RequestParameter.Single(13.41),
            "timezone" to RequestParameter.Single("Europe/Berlin"),
            "date" to RequestParameter.Single("2026-10-19"),
        ))

        options.adaptRequestParameters(parameters, route, Weaver(), Json)

        assertEquals(RequestParameter.Single("52.52"), parameters["latitude"])
        assertEquals(RequestParameter.Single("13.41"), parameters["longitude"])
        assertEquals(RequestParameter.Single("Europe/Berlin"), parameters["timezone"])
        assertEquals(RequestParameter.Single("7"), parameters["forecast_days"])
        assertEquals(null, parameters["lat"])
        assertEquals(null, parameters["date"])
    }

    private fun routeWithInputs(inputs: List<Route.Input>) = Route(
        operationId = "forecast",
        summary = null,
        path = "/forecast",
        method = HttpMethod.Get,
        parameter = emptyList(),
        body = Route.Bodies(false, emptyMap(), emptyMap()),
        input = inputs,
        returnType = Route.Returns(emptyMap(), emptyMap()),
        securities = Route.Securities(emptyList(), true),
        extensions = emptyMap(),
        nested = emptyList(),
    )
}
