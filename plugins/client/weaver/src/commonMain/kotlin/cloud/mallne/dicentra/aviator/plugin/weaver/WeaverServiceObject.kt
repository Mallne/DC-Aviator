package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder.Companion.json
import cloud.mallne.dicentra.aviator.koas.typed.Model
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.weaver.core.Weaver
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import cloud.mallne.dicentra.weaver.core.specification.compute.ObjectComputationDeclaration
import cloud.mallne.dicentra.weaver.exceptions.WeaverMissingPropertyException
import cloud.mallne.dicentra.aviator.core.io.RawRequestBody
import io.ktor.openapi.GenericElementWrapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import io.ktor.openapi.ParameterType
import io.ktor.openapi.Parameter
import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data class WeaverServiceObject(
    @SerialName("x-dicentra-weaver-schema")
    val schema: WeaverSchema? = null,
    @SerialName("x-dicentra-weaver-request-body-schema")
    val requestBodySchema: WeaverSchema? = null,
    @SerialName("x-dicentra-weaver-request-parameters-schema")
    val requestParametersSchema: WeaverSchema? = null,
) : InflatedServiceOptions {
    override fun usable(): ServiceOptions = GenericElementWrapper(this, serializer())

    @OptIn(InternalAPI::class)
    fun adaptRequestParameters(parameters: RequestParameters, route: Route, weaver: Weaver, json: Json): Route {
        val schema = requestParametersSchema ?: return route
        route.input.forEach { input ->
            if (input.input != ParameterType.query) return@forEach
            val default = input.type.defaultValue(json) ?: return@forEach
            if (input.name !in parameters) parameters[input.name] = RequestParameter.Single(default)
        }
        val root = schema.getRootLimboObject()
        val computation = root.limboObject.compute as? ObjectComputationDeclaration
        val projections = computation?.nested?.map { property ->
            val declaration = root.limboObject.copy(compute = computation.copy(nested = listOf(property)))
            val projection = root.copy(limboObject = declaration, definedParameters = emptyMap())
            val usedBindings = projection.bindingReads()
            projection.copy(definedParameters = root.definedParameters.filterKeys { it in usedBindings })
        }
        // Analyze exactly the entry points we execute, after pruning unused root arguments.
        val dependencies = projections?.fold(WeaverInputDependencies()) { reads, projection ->
            reads + projection.inputDependencies()
        } ?: root.inputDependencies()
        val input = buildJsonObject {
            parameters.forEach { (name, value) ->
                if (dependencies.consumes(name)) put(name, value.toJson(json))
            }
        }
        val optionalInputs = route.input.filter { !it.isRequired }.map { it.name }.toSet()
        val declaredOutputs = computation?.nested?.map { it.name }?.toSet().orEmpty()
        val callerInputs = route.input.filter {
            it.input != ParameterType.query || it.name !in declaredOutputs || dependencies.consumes(it.name)
        }
        require(callerInputs.filter { it.isRequired }.all { it.name in parameters }) {
            "Weaver request is missing a required caller input"
        }
        val output = if (projections != null) {
            // Evaluate projections independently so an absent optional source omits only its
            // output field. Never manufacture empty strings (which coerce to zero/false).
            buildJsonObject {
                projections.forEach { entryPoint ->
                    try {
                        val projected = weaver.engine(schema).executeElement(input, entryPoint) as JsonObject
                        projected.forEach { (name, value) -> put(name, value) }
                    } catch (missing: WeaverMissingPropertyException) {
                        if (!missing.atRoot || missing.property !in optionalInputs || missing.property in input) throw missing
                    }
                }
            }
        } else weaver.engine(schema).executeElement(input) as? JsonObject
            ?: error("Weaver request-parameters schema must produce an object")
        val outputNames = declaredOutputs + output.keys
        fun isRequiredCallerAccessor(accessor: String): Boolean {
            val segments = accessor.substringBefore('[').split('.')
            val input = callerInputs.firstOrNull { it.name == segments.first() } ?: return false
            if (!input.isRequired) return false
            var model = input.type
            for (segment in segments.drop(1)) {
                val objectModel = model as? Model.Object ?: return false
                val property = objectModel.properties.firstOrNull { it.baseName == segment } ?: return false
                if (!property.isRequired) return false
                model = property.model
            }
            return true
        }
        val requiredOutputs = computation?.nested.orEmpty().filter { property ->
            val reads = property.content.resolveBound(root).inputDependencies()
            !reads.allInputs && reads.accessors.isNotEmpty() && reads.accessors.all(::isRequiredCallerAccessor)
        }.map { it.name }.toSet()
        val providerParameters = outputNames.map { name ->
            val declared = route.parameter.firstOrNull { it.`in` == ParameterType.query && it.name == name }
            declared?.copy(required = declared.required || name in requiredOutputs)
                ?: Parameter(name = name, `in` = ParameterType.query, required = name in requiredOutputs)
        }
        require(providerParameters.filter { it.required }.all { it.name in output }) {
            "Weaver projection is missing a required provider input"
        }
        route.input.filter { it.input == ParameterType.query }.forEach { parameters.remove(it.name) }
        output.forEach { (name, value) ->
            parameters[name] = if (value is JsonArray) {
                RequestParameter.Multi(value.map { it.parameterText(json) })
            } else RequestParameter.Single(value.parameterText(json))
        }
        return route.copy(
            parameter = route.parameter.filter { it.`in` != ParameterType.query } + providerParameters,
            input = route.input.filter { it.input != ParameterType.query || it.name in outputNames },
        )
    }

    private fun JsonElement.parameterText(json: Json): String =
        if (this is JsonPrimitive) content else json.encodeToString(JsonElement.serializer(), this)

    @Suppress("UNCHECKED_CAST")
    private fun parameterJson(value: Any?, serializer: KSerializer<*>?, json: Json): JsonElement = when {
        value == null -> JsonNull
        serializer != null -> json.encodeToJsonElement(serializer as SerializationStrategy<Any>, value)
        value is JsonElement -> value
        value is Boolean -> JsonPrimitive(value)
        value is Number -> JsonPrimitive(value)
        value is String -> JsonPrimitive(value)
        else -> error("A serializer is required to transform a structured Weaver parameter")
    }

    private fun RequestParameter.toJson(json: Json): JsonElement = when (this) {
        is RequestParameter.Single<*> -> parameterJson(value, valueSerializer, json)
        is RequestParameter.Multi<*> -> JsonArray(value.map { parameterJson(it, valueSerializer, json) })
    }

    fun <O : @Serializable Any, B : @Serializable Any> adaptRequestBody(
        context: AviatorExecutionContext<O, B>,
        weaver: Weaver,
    ) {
        val schema = requestBodySchema ?: return
        val body = context.body ?: return
        val bodyType = context.bodyClazz ?: return
        require(body !is RawRequestBody) { "Weaver body recipes require a JSON-compatible body, not serialized bytes" }
        @Suppress("UNCHECKED_CAST")
        val serializer = bodyType.third as KSerializer<Any>
        val input = context.dataHolder.json.encodeToJsonElement(serializer, body)
        val transformed = weaver.engine(schema).executeElement(input)
        // The output need not have the caller's input DTO shape. Use its own serializer
        // instead of decoding the provider projection back into the input type.
        @Suppress("UNCHECKED_CAST")
        val mutable = context as AviatorExecutionContext<O, Any>
        mutable.body = transformed
        @Suppress("UNCHECKED_CAST")
        val outputType = Triple(JsonElement::class, typeOf<JsonElement>(), JsonElement.serializer()) as
            Triple<KClass<Any>, KType, KSerializer<Any>>
        mutable.bodyClazz = outputType
    }

    private fun Model.defaultValue(json: Json): Any? = when (this) {
        is Model.Primitive.String -> default
        is Model.Primitive.Int -> default
        is Model.Primitive.Double -> default
        is Model.Primitive.Boolean -> default
        is Model.Enum.Closed -> default?.let(json::parseToJsonElement)
        is Model.Enum.Open -> default?.let(json::parseToJsonElement)
        is Model.Collection.List -> default?.let(json::parseToJsonElement)?.takeUnless { it === JsonNull }
        else -> null
    }
}
