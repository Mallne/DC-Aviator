package cloud.mallne.dicentra.aviator.koas.typed

import cloud.mallne.dicentra.aviator.koas.exceptions.IngestArgumentViolation
import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPIConstraintViolation
import cloud.mallne.dicentra.aviator.koas.extensions.SchemaExtensions.stringEnums
import cloud.mallne.dicentra.aviator.koas.typed.Model.Object.Property
import cloud.mallne.dicentra.aviator.koas.typed.NamingContext.Named
import cloud.mallne.dicentra.polyfill.ensure
import cloud.mallne.dicentra.polyfill.ensureNotNull
import io.ktor.http.*
import io.ktor.openapi.*
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmInline

fun OpenApiDoc.routes(): List<Route> = OpenAPITransformer(this).routes()

/**
 * This class implements the traverser, it goes through the [OpenApiDoc] file, and gathers all the
 * information.
 *
 * It does the heavy lifting of figuring out what a `Schema` is, a `String`, `enum=[alive, dead]`,
 * object, etc.
 */
private class OpenAPITransformer(private val openAPI: OpenApiDoc) {

    data class OperationsHolder(
        val path: String,
        val method: HttpMethod,
        val operation: Operation,
        val pathItem: PathItem,
    )

    fun operations(): List<OperationsHolder> = openAPI.paths.entries.flatMap { (path, pref) ->
        val p = pref.get()
        listOfNotNull(
            p.get?.let { OperationsHolder(path, HttpMethod.Get, it, p) },
            p.put?.let { OperationsHolder(path, HttpMethod.Put, it, p) },
            p.post?.let { OperationsHolder(path, HttpMethod.Post, it, p) },
            p.delete?.let { OperationsHolder(path, HttpMethod.Delete, it, p) },
            p.head?.let { OperationsHolder(path, HttpMethod.Head, it, p) },
            p.options?.let { OperationsHolder(path, HttpMethod.Options, it, p) },
            p.trace?.let { OperationsHolder(path, HttpMethod.parse("Trace"), it, p) },
            p.patch?.let { OperationsHolder(path, HttpMethod.Patch, it, p) },
        ) /* TODO-low OAS 3.2.0 + p.additionalOperations.map { (method, op) ->
            OperationsHolder(path, HttpMethod.parse(method), op, p)
        } */
    }

    fun routes(): List<Route> = operations().map { (path, method, operation, pathItem) ->
        val parts = path.segments()

        fun context(context: NamingContext): NamingContext = when (parts.size) {
            0 -> context
            1 -> NamingContext.Nested(context, Named(parts[0]))
            else -> NamingContext.Nested(
                context, parts.drop(1).fold<String, NamingContext>(Named(parts[0])) { acc, part ->
                    NamingContext.Nested(Named(part), acc)
                })
        }

        val inputs = operation.input(::context)
        val nestedInput = inputs.mapNotNull { (it as? Resolved.Value)?.value }
        val nestedResponses = operation.responses?.responses?.mapNotNull { (_, refOrResponse) ->
            when (refOrResponse) {
                is ReferenceOr.Reference -> null
                is ReferenceOr.Value -> {
                    val resolved =
                        refOrResponse.value.content?.getOrElse(ContentType.Application.Json) { null }?.schema?.resolve()
                            ?: return@mapNotNull null
                    val context = resolved.namedOr {
                        val operationId = ensureNotNull(operation.operationId) {
                            OpenAPIConstraintViolation("OperationId is required for request body inline schemas. Otherwise we cannot generate OperationIdRequest class name")
                        }
                        context(NamingContext.RouteBody(operationId, "Response"))
                    }
                    (resolved.toModel(context) as? Resolved.Value)?.value
                }
            }
        }

        val json =
            operation.requestBody?.valueOrNull()?.content?.getOrElse(ContentType.Application.Json) { null }?.schema?.resolve()

        val nestedBody = json?.namedOr {
            val name = ensureNotNull(operation.operationId?.let { Named("${it}Request") }) {
                OpenAPIConstraintViolation("OperationId is required for request body inline schemas. Otherwise we cannot generate OperationIdRequest class name")
            }
            context(name)
        }?.let { (json.toModel(it) as? Resolved.Value)?.value }.let(::listOfNotNull)

        val securityComponents = openAPI.components?.securitySchemes
        val globalSecurity = openAPI.security
        val operationSecurity = operation.security
        val securityRequirements = (globalSecurity ?: listOf()) + (operationSecurity ?: listOf())
        var anonymousAllowed = false
        val securities = mutableListOf<Route.Security>()

        for (securityRequirement in securityRequirements) {
            if (securityRequirement.isEmpty()) {
                anonymousAllowed = true
            }
            securities.addAll(
                securityRequirement.mapNotNull { (name, scopes) ->
                    securityComponents?.get(name)?.let { referenceOr ->
                        referenceOr.get().let {
                            Route.Security(
                                name,
                                scopes,
                                it
                            )
                        }
                    }
                }
            )
        }

        if (securities.isEmpty()) {
            anonymousAllowed = true
        }

        Route(
            operationId = operation.operationId,
            summary = operation.summary ?: pathItem.summary,
            path = path,
            method = method,
            body = toRequestBody(operation, operation.requestBody?.get(), ::context),
            input = inputs.zip(operation.parameters ?: listOf()) { model, p ->
                val param = p.get()
                Route.Input(param.name, model.value, param.required, param.`in`!!, param.description)
            },
            returnType = toResponses(operation, ::context),
            extensions = operation.extensions ?: mapOf(),
            nested = nestedInput + (nestedResponses ?: listOf()) + nestedBody,
            parameter = pathItem.condenseParameters { operation },
            securities = Route.Securities(securities, anonymousAllowed)
        )
    }

    fun PathItem.condenseParameters(
        pathItem: PathItem = this,
        operation: () -> Operation? = this::get,
    ): List<Parameter> {
        val paramsOfPath = pathItem.parameters?.map { it.get() }
        val paramsOfOperation = operation()?.parameters?.map { it.get() } ?: emptyList()

        val filteredParams = paramsOfPath?.filter { p ->
            paramsOfOperation.find { it.name == p.name } == null
        } ?: listOf()

        return filteredParams + paramsOfOperation
    }

    fun Operation.input(create: (NamingContext) -> NamingContext): List<Resolved<Model>> =
        parameters?.map { p ->
            val param = p.get()
            val resolved = param.schema?.resolve()
                ?: throw OpenAPIConstraintViolation("No Schema for Parameter ${param.name} of operation $operationId/$description.")
            val context = resolved.namedOr {
                val operationId = ensureNotNull(operationId) {
                    OpenAPIConstraintViolation("operationId currently required to generate inline schemas for operation parameters.")
                }
                create(NamingContext.RouteParam(param.name, operationId, "Request"))
            }
            resolved.toModel(context)
        } ?: listOf()

    fun Resolved<JsonSchema>.toModel(context: NamingContext): Resolved<Model> {
        val schema: JsonSchema = value
        val model = when {
            schema.isOpenEnumeration() -> schema.toOpenEnum(
                context,
                schema.anyOf?.firstNotNullOf { it.resolve().value.stringEnums?.filterNotNull() } ?: listOf())

            /*
             * We're modifying the schema here...
             * This is to flatten the following to just `String`
             * "model": {
             *   "description": "ID of the model to use. You can use the [List models](/docs/api-reference/models/list) API to see all of your available models, or see our [Model overview](/docs/models/overview) for descriptions of them.\n",
             *   "anyOf": [
             *     {
             *       "type": "string"
             *     }
             *   ]
             * }
             */
            value.anyOf?.size == 1 -> value.anyOf!![0].resolve().toModel(context).value

            value.oneOf?.size == 1 -> value.oneOf!![0].resolve().toModel(context).value

            schema.anyOf?.isNotEmpty() == true -> schema.toUnion(context, schema.anyOf!!)
            // oneOf + properties => oneOf requirements: 'propA OR propB is required'.
            schema.oneOf?.isNotEmpty() == true && schema.properties?.isNotEmpty() == true -> schema.toObject(context)
            schema.oneOf?.isNotEmpty() == true -> schema.toUnion(context, schema.oneOf!!)
            schema.allOf?.isNotEmpty() == true -> allOf(schema, context)
            schema.enum?.isNotEmpty() == true -> schema.toEnum(context, schema.stringEnums?.filterNotNull() ?: listOf())
            else -> schema.type(context)
        }

        return when (this) {
            is Resolved.Ref -> Resolved.Ref(name, model)
            is Resolved.Value -> Resolved.Value(model)
        }
    }

    private tailrec fun JsonSchema.type(context: NamingContext): Model = when (type) {
        JsonType.STRING -> if (format == "binary") Model.OctetStream(description)
        else Model.Primitive.String(
            default(),
            description,
            Constraints.Text(this)
        )

        JsonType.ARRAY -> collection(context)
        JsonType.BOOLEAN -> Model.Primitive.Boolean(
            default(),
            description
        )

        JsonType.INTEGER -> Model.Primitive.Int(
            default(), description, Constraints.Number(this)
        )

        JsonType.NUMBER -> Model.Primitive.Double(
            default(), description, Constraints.Number(this)
        )

        JsonType.OBJECT -> toObject(context)
        JsonType.NULL -> TODO("JsonType.Null is not defined Behaviour")
        is JsonSchema.SchemaType.AnyOf -> TODO("JsonType.AnyOf is not defined Behaviour")
        null -> when {
            // If no type is defined, but we find properties, or additionalProperties, we assume it's
            // an object.
            properties?.isNotEmpty() == true || additionalProperties != null -> toObject(context)
            // If 'items' is defined, we assume it's an array.
            items != null -> collection(context)
            else -> TODO("Schema: $this not yet supported. Please report to issue tracker.")
        }
    }

    private inline fun <reified A> JsonSchema.default(on: (GenericElement) -> A? = { it.deserialize(kotlinx.serialization.serializer<A>()) }): A? =
        when (val def = default) {
            null -> null
            else -> on(def)
        }

    fun JsonSchema.isOpenEnumeration(): Boolean {
        val anyOf = anyOf
        return anyOf?.size == 2 && anyOf.count { it.resolve().value.enum?.isNotEmpty() == true } == 1 && anyOf.count { it.resolve().value.type == JsonType.STRING } == 2
    }

    fun ReferenceOr<JsonSchema>.resolve(): Resolved<JsonSchema> = when (this) {
        is ReferenceOr.Value -> Resolved.Value(value)
        is ReferenceOr.Reference -> {
            val name = ref.drop("#/components/schemas/".length)
            val schema = ensureNotNull(openAPI.components?.schemas?.get(name)) {
                OpenAPIConstraintViolation("Schema $name could not be found in ${openAPI.components?.schemas}. Is it missing?")
            }
            Resolved.Ref(name, schema)
        }
    }

    tailrec fun ReferenceOr<Response>.get(): Response = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/responses/".length)
            ensureNotNull(openAPI.components?.responses?.get(typeName)) {
                OpenAPIConstraintViolation("Response $typeName could not be found in ${openAPI.components?.responses}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Parameter>.get(): Parameter = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/parameters/".length)
            ensureNotNull(openAPI.components?.parameters?.get(typeName)) {
                OpenAPIConstraintViolation("Parameter $typeName could not be found in ${openAPI.components?.parameters}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<ExampleObject>.get(): ExampleObject = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/examples/".length)
            ensureNotNull(openAPI.components?.examples?.get(typeName)) {
                OpenAPIConstraintViolation("Example $typeName could not be found in ${openAPI.components?.examples}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<RequestBody>.get(): RequestBody = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/requestBodies/".length)
            ensureNotNull(openAPI.components?.requestBodies?.get(typeName)) {
                OpenAPIConstraintViolation("RequestBody $typeName could not be found in ${openAPI.components?.requestBodies}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Header>.get(): Header = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/header/".length)
            ensureNotNull(openAPI.components?.headers?.get(typeName)) {
                OpenAPIConstraintViolation("Header $typeName could not be found in ${openAPI.components?.headers}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<SecurityScheme>.get(): SecurityScheme = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/securitySchemes/".length)
            ensureNotNull(openAPI.components?.securitySchemes?.get(typeName)) {
                OpenAPIConstraintViolation("SecurityScheme $typeName could not be found in ${openAPI.components?.securitySchemes}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Link>.get(): Link = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/links/".length)
            ensureNotNull(openAPI.components?.links?.get(typeName)) {
                OpenAPIConstraintViolation("Link $typeName could not be found in ${openAPI.components?.links}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Callback>.get(): Callback = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/callbacks/".length)
            ensureNotNull(openAPI.components?.callbacks?.get(typeName)) {
                OpenAPIConstraintViolation("Callback $typeName could not be found in ${openAPI.components?.callbacks}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<PathItem>.get(): PathItem = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/pathItems/".length)
            ensureNotNull(openAPI.components?.pathItems?.get(typeName)) {
                OpenAPIConstraintViolation("PathItem $typeName could not be found in ${openAPI.components?.pathItems}. Is it missing?")
            }.get()
        }
    }

    // TODO-low prep for OpenAPI 3.2.0
    //tailrec fun ReferenceOr<MediaType>.get(): MediaType = when (this) {
    //    is ReferenceOr.Value -> value
    //    is ReferenceOr.Reference -> {
    //        val typeName = ref.drop("#/components/mediaTypes/".length)
    //        ensureNotNull(openAPI.components.mediaTypes[typeName]) {
    //            OpenAPIConstraintViolation("MediaType $typeName could not be found in ${openAPI.components.mediaTypes}. Is it missing?")
    //        }.get()
    //    }
    //}

    private fun JsonSchema.toObject(context: NamingContext): Model = when {
        properties?.isNotEmpty() == true -> toObject(context, properties ?: mapOf())
        additionalProperties != null -> when (additionalProperties) {
            // TODO: implement Schema validation
            is AdditionalProperties.PSchema -> Model.FreeFormJson(
                description,
                Constraints.Object(this)
            )

            is AdditionalProperties.Allowed -> if ((additionalProperties as AdditionalProperties.Allowed).value) Model.FreeFormJson(
                description,
                Constraints.Object(this)
            )
            else throw OpenAPIConstraintViolation(
                "No additional properties allowed on object without properties. $this"
            )


            else -> throw IngestArgumentViolation(
                "the additional Properties violate the defined logic: $additionalProperties $this"
            )
        }

        else -> Model.FreeFormJson(description, Constraints.Object(this))
    }

    /**
     * allOf defines an object that is a combination of all the defined allOf schemas. For example: an
     * object with age, and name + an object with id == an object with age, name and id.
     *
     * This is still a WIP. We need to implement a more fine-grained approach to combining schemas,
     * such that we can generate the most idiomatic Kotlin code in all cases. Different results are
     * likely desired, depending on what kind of schemas need to be comibined. Simple products, or
     * more complex combinations including oneOf, anyOf, etc.
     */
    private fun allOf(schema: JsonSchema, context: NamingContext): Model {
        val allOf = schema.allOf?.map { it.resolve() } ?: listOf()
        val ref = allOf.singleOrNull { it is Resolved.Ref && it.value.type == JsonType.OBJECT }
        val obj = allOf.singleOrNull { it is Resolved.Value && it.value.type == JsonType.OBJECT }
        return when {
            ref != null && obj != null -> {
                val properties = (ref.value.properties ?: mapOf()) + (obj.value.properties ?: mapOf())
                JsonSchema(
                    type = JsonType.OBJECT,
                    properties = properties,
                    additionalProperties = ref.value.additionalProperties
                        ?: obj.value.additionalProperties,
                    description = ref.value.description,
                    required = ref.value.required.orEmpty() + obj.value.required.orEmpty(),
                    discriminator = ref.value.discriminator ?: obj.value.discriminator,
                    minProperties = ref.value.minProperties ?: obj.value.minProperties,
                    maxProperties = ref.value.maxProperties ?: obj.value.maxProperties,
                    readOnly = ref.value.readOnly ?: obj.value.readOnly,
                    writeOnly = ref.value.writeOnly ?: obj.value.writeOnly,
                    externalDocs = ref.value.externalDocs,
                    examples = ref.value.examples,
                    default = ref.value.default,
                    id = ref.value.id,
                    anchor = ref.value.anchor,
                    deprecated = ref.value.deprecated,
                ).toObject(context, properties)
            }

            (schema.additionalProperties as? AdditionalProperties.Allowed)?.value == true -> Model.FreeFormJson(
                schema.description,
                Constraints.Object(schema)
            )

            else -> schema.toUnion(context, schema.allOf ?: listOf())
        }
    }

    fun JsonSchema.toObject(
        context: NamingContext,
        properties: Map<String, ReferenceOr<JsonSchema>>,
    ): Model {
        ensure((additionalProperties as? AdditionalProperties.Allowed)?.value != true) {
            OpenAPIConstraintViolation("Additional properties, on a schema with properties, are not yet supported.")
        }
        return Model.Object(context, description, properties.map { (name, ref) ->
            val resolved = ref.resolve()
            val pContext = when (resolved) {
                is Resolved.Ref -> Named(resolved.name)
                is Resolved.Value -> NamingContext.Nested(Named(name), context)
            }
            val model = resolved.toModel(pContext)
            // TODO implement oneOf required properties properly
            //   This cannot be done with @Required, but needs to be part of validation
            //        val oneOfRequired = oneOf?.any {
            // it.resolve().value.required.orEmpty().contains(name) }
            Property(
                name,
                model.value,
                required?.contains(name) == true,
                type == JsonType.NULL || (type is JsonSchema.SchemaType.AnyOf && (type as JsonSchema.SchemaType.AnyOf).types.contains(
                    JsonType.NULL
                )) || required?.contains(name)?.not() != false,
                resolved.value.description
            )
        }, properties.mapNotNull { (name, ref) ->
            val resolved = ref.resolve()
            val pContext = when (resolved) {
                is Resolved.Ref -> Named(resolved.name)
                is Resolved.Value -> NamingContext.Nested(Named(name), context)
            }
            nestedModel(resolved, pContext)
        })
    }

    private fun JsonSchema.singleDefaultOrNull(): String? = (default)?.toString()

    fun JsonSchema.toOpenEnum(context: NamingContext, values: List<String>): Model.Enum.Open {
        ensure(values.isNotEmpty()) { OpenAPIConstraintViolation("OpenEnum requires at least 1 possible value") }
        val default = singleDefaultOrNull()
        return Model.Enum.Open(context, values, default, description)
    }

    private fun JsonSchema.collection(context: NamingContext): Model.Collection {
        val items =
            ensureNotNull(items?.resolve()) { OpenAPIConstraintViolation("Array type requires items to be defined.") }
        val inner = items.toModel(items.namedOr { context })
        val default = Json.encodeToString(default)
        return if (uniqueItems == true) Model.Collection.List(
            inner.value,
            default,
            description,
            Constraints.Collection(this)
        )
        else Model.Collection.List(inner.value, default, description, Constraints.Collection(this))
    }

    fun JsonSchema.toEnum(context: NamingContext, enums: List<String>): Model.Enum.Closed {
        ensure(enums.isNotEmpty()) { OpenAPIConstraintViolation("Enum requires at least 1 possible value") }/* To resolve the inner type, we erase the enum values.
         * Since the schema is still on the same level - we keep the topLevelName */
        val inner = Resolved.Value(copy(enum = emptyList())).toModel(context)
        val default = singleDefaultOrNull()
        return Model.Enum.Closed(context, inner.value, enums, default, description)
    }

    /**
     * This Comparator will sort union cases by their most complex schema first Such that if we have {
     * "text" : String } & { "text" : String, "id" : Int } That we don't accidentally result in the
     * first case, when we receive the second case. Primitive.String always comes last.
     */
    private val unionSchemaComparator: Comparator<Model.Union.Case> = Comparator { o1, o2 ->
        val m1 = o1.model
        val m2 = o2.model
        val m1Complexity = when (m1) {
            is Model.Object -> m1.properties.size
            is Model.Enum -> m1.values.size
            is Model.Primitive.String -> -1
            else -> 0
        }
        val m2Complexity = when (m2) {
            is Model.Object -> m2.properties.size
            is Model.Enum -> m2.values.size
            is Model.Primitive.String -> -1
            else -> 0
        }
        m2Complexity - m1Complexity
    }

    private fun JsonSchema.toUnion(
        context: NamingContext, subtypes: List<ReferenceOr<JsonSchema>>,
    ): Model.Union {
        val caseToContext = subtypes.associate { ref ->
            val resolved = ref.resolve()
            Pair(resolved, toUnionCaseContext(context, resolved))
        }
        val cases = caseToContext.map { (resolved, caseContext) ->
            Model.Union.Case(caseContext, resolved.toModel(caseContext).value)
        }.sortedWith(unionSchemaComparator)
        val inline = caseToContext.mapNotNull { (resolved, caseContext) ->
            nestedModel(
                resolved,
                caseContext
            )
        }
        return Model.Union(
            context,
            cases,
            singleDefaultOrNull()
                ?: subtypes.firstNotNullOfOrNull { it.resolve().value.singleDefaultOrNull() },
            description,
            inline
        )
    }

    fun toUnionCaseContext(context: NamingContext, case: Resolved<JsonSchema>): NamingContext =
        when (case) {
            is Resolved.Ref -> Named(case.name)
            is Resolved.Value -> when {
                context is Named && case.value.type == JsonType.STRING && case.value.enum?.isNotEmpty() == true -> NamingContext.Nested(
                    Named(
                        case.value.stringEnums?.filterNotNull()?.joinToString(prefix = "", separator = "Or") {
                            it.replaceFirstChar(Char::uppercaseChar)
                        } ?: throw IngestArgumentViolation("Invalid enum ${case.value.enum}")
                    ),
                    context)

                case.value.type == JsonType.OBJECT -> NamingContext.Nested(
                    case.value.properties?.firstNotNullOfOrNull { (key, value) ->
                        if (key == "event" || key == "type") value.resolve().value.stringEnums else null
                    }?.singleOrNull()?.let(::Named)
                        ?: TODO("Name Generated for inline objects of unions not yet supported."),
                    context
                )

                case.value.type == JsonType.ARRAY -> case.value.items?.resolve()
                    ?.namedOr { if (case.value.uniqueItems == true) Named("Set") else Named("List") }
                    ?.let { NamingContext.Nested(it, context) } ?: context

                else -> context
            }
        }

    private fun nestedModel(resolved: Resolved<JsonSchema>, caseContext: NamingContext): Model? =
        when (val model = resolved.toModel(caseContext)) {
            is Resolved.Ref -> null
            is Resolved.Value -> when (model.value) {
                is Model.Collection -> when (val inner = resolved.value.items?.resolve()) {
                    is Resolved.Value -> nestedModel(Resolved.Value(inner.value), caseContext)
                    is Resolved.Ref -> null
                    null -> throw IngestArgumentViolation("Impossible: List without inner type")
                }

                else -> model.value
            }
        }

    // TODO interceptor
    fun toRequestBody(
        operation: Operation,
        body: RequestBody?,
        create: (NamingContext) -> NamingContext,
    ): Route.Bodies =
        Route.Bodies(
            body?.required == true,
            body?.content?.entries?.associate { (contentType, mediaType) ->
                contentType to generateRequestModel(
                    mediaType.schema?.resolve(),
                    operation,
                    create,
                    body.description
                )
            }.orEmpty(),
            body?.extensions.orEmpty()
        )

    private tailrec fun generateRequestModel(
        schema: Resolved<JsonSchema>?,
        operation: Operation,
        create: (NamingContext) -> NamingContext,
        description: String?,
    ): Route.Bodies.Body = schema?.let { json ->
        val context = json.namedOr {
            val name =
                ensureNotNull(operation.operationId?.let { Named("${it}Request") }) {
                    OpenAPIConstraintViolation("OperationId is required for request body inline schemas. Otherwise we cannot generate OperationIdRequest class name")
                }
            create(name)
        }
        Route.Bodies.Body(json.toModel(context).value, json.value, json.value.properties?.map { (name, ref) ->
            val p = ref.resolve()
            name to generateRequestModel(p, operation, create, p.value.description)
        }?.toMap() ?: mapOf())
    } ?: (Route.Bodies.Body(Model.Primitive.Unit(description), null, mapOf()))

    fun toResponses(operation: Operation, create: (NamingContext) -> NamingContext): Route.Returns =
        Route.Returns(
            operation.responses?.responses?.entries?.associate { (code, refOrResponse) ->
                val statusCode = HttpStatusCode.fromValue(code)
                val response = refOrResponse.get()

                val m = response.content?.map { (contentType, mediaType) ->
                    contentType to generateRequestModel(
                        mediaType.schema?.resolve(),
                        operation,
                        create,
                        response.description
                    )
                }?.toMap()

                statusCode to Route.ReturnType(
                    m ?: mapOf(), response.extensions ?: mapOf()
                )
            } ?: mapOf(), operation.responses?.extensions ?: mapOf()
        )

    /**
     * Allows tracking whether data was referenced by name, or defined inline. This is important to be
     * able to maintain the structure of the specification.
     */
    // TODO this can be removed.
    //   Move 'nested' logic to OpenAPITransformer
    //   Inline `namedOr` logic where used
    //   Rely on `ReferenceOr<Schema>` everywhere within `OpenAPITransformer`?
    sealed interface Resolved<A> {
        val value: A

        data class Ref<A>(val name: String, override val value: A) : Resolved<A>

        @JvmInline
        value class Value<A>(override val value: A) : Resolved<A>

        fun namedOr(orElse: () -> NamingContext): NamingContext = when (this) {
            is Ref -> Named(name)
            is Value -> orElse()
        }

        fun valueOrNull(): A? = when (this) {
            is Ref -> null
            is Value -> value
        }
    }
}
