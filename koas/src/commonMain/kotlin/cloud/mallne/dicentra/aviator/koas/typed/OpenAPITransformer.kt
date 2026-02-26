package cloud.mallne.dicentra.aviator.koas.typed

import cloud.mallne.dicentra.aviator.koas.AdditionalProperties
import cloud.mallne.dicentra.aviator.koas.AdditionalProperties.Allowed
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.Operation
import cloud.mallne.dicentra.aviator.koas.PathItem
import cloud.mallne.dicentra.aviator.koas.exceptions.IngestArgumentViolation
import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPIConstraintViolation
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import cloud.mallne.dicentra.aviator.koas.io.Callback
import cloud.mallne.dicentra.aviator.koas.io.Example
import cloud.mallne.dicentra.aviator.koas.io.ExampleValue
import cloud.mallne.dicentra.aviator.koas.io.Header
import cloud.mallne.dicentra.aviator.koas.io.MediaType
import cloud.mallne.dicentra.aviator.koas.io.Schema
import cloud.mallne.dicentra.aviator.koas.io.Schema.Type
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import cloud.mallne.dicentra.aviator.koas.parameters.RequestBody
import cloud.mallne.dicentra.aviator.koas.responses.Link
import cloud.mallne.dicentra.aviator.koas.responses.Response
import cloud.mallne.dicentra.aviator.koas.security.SecurityScheme
import cloud.mallne.dicentra.aviator.koas.typed.Model.Object.Property
import cloud.mallne.dicentra.aviator.koas.typed.NamingContext.Named
import cloud.mallne.dicentra.polyfill.ensure
import cloud.mallne.dicentra.polyfill.ensureNotNull
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.jvm.JvmInline

fun OpenAPI.routes(): List<Route> = OpenAPITransformer(this).routes()

fun OpenAPI.models(): Set<Model> = with(OpenAPITransformer(this)) { schemas() }.toSet()

/**
 * This class implements the traverser, it goes through the [OpenAPI] file, and gathers all the
 * information.
 *
 * It does the heavy lifting of figuring out what a `Schema` is, a `String`, `enum=[alive, dead]`,
 * object, etc.
 */
private class OpenAPITransformer(private val openAPI: OpenAPI) {

    data class OperationsHolder(
        val path: String,
        val method: HttpMethod,
        val operation: Operation,
        val pathItem: PathItem,
    )

    fun operations(): List<OperationsHolder> = openAPI.paths.entries.flatMap { (path, p) ->
        listOfNotNull(
            OperationsHolder(path, HttpMethod.Get, p.get, p),
            OperationsHolder(path, HttpMethod.Put, p.put, p),
            OperationsHolder(path, HttpMethod.Post, p.post, p),
            OperationsHolder(path, HttpMethod.Delete, p.delete, p),
            OperationsHolder(path, HttpMethod.Head, p.head, p),
            OperationsHolder(path, HttpMethod.Options, p.options, p),
            OperationsHolder(path, HttpMethod.parse("Trace"), p.trace, p),
            OperationsHolder(path, HttpMethod.Patch, p.patch, p),
        ) + p.additionalOperations.map { (method, op) ->
            OperationsHolder(path, HttpMethod.parse(method), op, p)
        }
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
        val nestedResponses = operation.responses.responses.mapNotNull { (_, refOrResponse) ->
            when (refOrResponse) {
                is ReferenceOr.Reference -> null
                is ReferenceOr.Value -> {
                    val resolved =
                        refOrResponse.value.content.getOrElse("application/json") { null }?.get()?.schema?.resolve()
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
            operation.requestBody?.valueOrNull()?.content?.getOrElse("application/json") { null }
                ?.get()?.schema?.resolve()

        val nestedBody = json?.namedOr {
            val name = ensureNotNull(operation.operationId?.let { Named("${it}Request") }) {
                OpenAPIConstraintViolation("OperationId is required for request body inline schemas. Otherwise we cannot generate OperationIdRequest class name")
            }
            context(name)
        }?.let { (json.toModel(it) as? Resolved.Value)?.value }.let(::listOfNotNull)

        val securityComponents = openAPI.components.securitySchemes
        val globalSecurity = openAPI.security
        val operationSecurity = operation.security
        val securityRequirements = globalSecurity + operationSecurity
        var anonymousAllowed = false
        val securities = mutableListOf<Route.Security>()

        for (securityRequirement in securityRequirements) {
            if (securityRequirement.isEmpty()) {
                anonymousAllowed = true
            }
            securities.addAll(
                securityRequirement.mapNotNull { (name, scopes) ->
                    securityComponents[name]?.let {
                        securityComponents[name]?.valueOrNull()?.let {
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
            input = inputs.zip(operation.parameters) { model, p ->
                val param = p.get()
                Route.Input(param.name, model.value, param.required, param.input, param.description)
            },
            returnType = toResponses(operation, ::context),
            extensions = operation.extensions,
            nested = nestedInput + nestedResponses + nestedBody,
            parameter = pathItem.condenseParameters { operation },
            securities = Route.Securities(securities, anonymousAllowed)
        )
    }

    fun PathItem.condenseParameters(
        pathItem: PathItem = this,
        operation: () -> Operation = this::get,
    ): List<Parameter> {
        val paramsOfPath = pathItem.parameters.map { it.get() }
        val paramsOfOperation = operation().parameters.map { it.get() }

        val filteredParams = paramsOfPath.filter { p ->
            paramsOfOperation.find { it.name == p.name } == null
        }

        return filteredParams + paramsOfOperation
    }

    fun Operation.input(create: (NamingContext) -> NamingContext): List<Resolved<Model>> =
        parameters.map { p ->
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
        }

    /** Gathers all "top-level", or components schemas. */
    fun schemas(): List<Model> = openAPI.components.schemas.map { (name, refOrSchema) ->
        when (val resolved = refOrSchema.resolve()) {
            is Resolved.Ref -> throw OpenAPIConstraintViolation("Remote schemas not supported yet.")
            is Resolved.Value -> Resolved.Ref(name, resolved.value).toModel(Named(name)).value
        }
    }

    fun Resolved<Schema>.toModel(context: NamingContext): Resolved<Model> {
        val schema: Schema = value
        val model = when {
            schema.isOpenEnumeration() -> schema.toOpenEnum(
                context,
                schema.anyOf.firstNotNullOf { it.resolve().value.enum })

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
            value.anyOf.size == 1 -> value.anyOf[0].resolve().toModel(context).value

            value.oneOf.size == 1 -> value.oneOf[0].resolve().toModel(context).value

            schema.anyOf.isNotEmpty() -> schema.toUnion(context, schema.anyOf)
            // oneOf + properties => oneOf requirements: 'propA OR propB is required'.
            schema.oneOf.isNotEmpty() && schema.properties.isNotEmpty() -> schema.toObject(context)
            schema.oneOf.isNotEmpty() -> schema.toUnion(context, schema.oneOf)
            schema.allOf.isNotEmpty() -> allOf(schema, context)
            schema.enum.isNotEmpty() -> schema.toEnum(context, schema.enum)
            else -> schema.type(context)
        }

        return when (this) {
            is Resolved.Ref -> Resolved.Ref(name, model)
            is Resolved.Value -> Resolved.Value(model)
        }
    }

    private tailrec fun Schema.type(context: NamingContext): Model = when (val type = type) {
        is Type.Array -> when (val single = type.types.singleOrNull()) {
            null -> {
                ensure(type.types.isNotEmpty()) { OpenAPIConstraintViolation("Array type requires types to be defined. $this") }
                val resolved = type.types.sorted().map { t -> Resolved.Value(Schema(type = t)) }
                Model.Union(
                    context = context,
                    cases = resolved.map { Model.Union.Case(context, it.toModel(context).value) },
                    default = null,
                    description = description,
                    inline = resolved.mapNotNull { nestedModel(it, context) })
            }

            else -> copy(type = single).type(context)
        }

        is Type.Basic -> when (type) {
            Type.Basic.Array -> collection(context)
            Type.Basic.Boolean -> Model.Primitive.Boolean(
                default("Boolean", String::toBooleanStrictOrNull),
                description
            )

            Type.Basic.Integer -> Model.Primitive.Int(
                default("Integer", String::toIntOrNull), description, Constraints.Number(this)
            )

            Type.Basic.Number -> Model.Primitive.Double(
                default("Number", String::toDoubleOrNull), description, Constraints.Number(this)
            )

            Type.Basic.String -> if (format == "binary") Model.OctetStream(description)
            else Model.Primitive.String(
                default("String", String::toString) { it.joinToString() },
                description,
                Constraints.Text(this)
            )

            Type.Basic.Object -> toObject(context)
            Type.Basic.Null -> TODO("Schema.Type.Basic.Null")
        }

        null -> when {
            // If no type is defined, but we find properties, or additionalProperties, we assume it's
            // an object.
            properties.isNotEmpty() || additionalProperties != null -> toObject(context)
            // If 'items' is defined, we assume it's an array.
            items != null -> collection(context)
            else -> TODO("Schema: $this not yet supported. Please report to issue tracker.")
        }
    }

    private fun <A> Schema.default(
        label: String, onSingle: (String) -> A?, onMultiple: (List<String>) -> A?,
    ): A? = when (val default = default) {
        is ExampleValue.Single -> onSingle(default.value)
            ?: throw OpenAPIConstraintViolation("Default value ${default.value} is not a $label.")

        is ExampleValue.Multiple -> onMultiple(default.values)
        null -> null
    }

    private fun <A> Schema.default(label: String, onSingle: (String) -> A?): A? =
        default(label, onSingle) {
            throw OpenAPIConstraintViolation("Multiple default values not supported for $label.")
        }

    fun Schema.isOpenEnumeration(): Boolean {
        val anyOf = anyOf
        return anyOf.size == 2 && anyOf.count { it.resolve().value.enum.isNotEmpty() } == 1 && anyOf.count { it.resolve().value.type == Type.Basic.String } == 2
    }

    fun ReferenceOr<Schema>.resolve(): Resolved<Schema> = when (this) {
        is ReferenceOr.Value -> Resolved.Value(value)
        is ReferenceOr.Reference -> {
            val name = ref.drop("#/components/schemas/".length)
            val schema = ensureNotNull(openAPI.components.schemas[name]) {
                OpenAPIConstraintViolation("Schema $name could not be found in ${openAPI.components.schemas}. Is it missing?")
            }.valueOrNull()
                ?: throw OpenAPIConstraintViolation("Remote schemas are not yet supported.")
            Resolved.Ref(name, schema)
        }
    }

    tailrec fun ReferenceOr<Response>.get(): Response = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/responses/".length)
            ensureNotNull(openAPI.components.responses[typeName]) {
                OpenAPIConstraintViolation("Response $typeName could not be found in ${openAPI.components.responses}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Parameter>.get(): Parameter = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/parameters/".length)
            ensureNotNull(openAPI.components.parameters[typeName]) {
                OpenAPIConstraintViolation("Parameter $typeName could not be found in ${openAPI.components.parameters}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Example>.get(): Example = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/examples/".length)
            ensureNotNull(openAPI.components.examples[typeName]) {
                OpenAPIConstraintViolation("Example $typeName could not be found in ${openAPI.components.examples}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<RequestBody>.get(): RequestBody = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/requestBodies/".length)
            ensureNotNull(openAPI.components.requestBodies[typeName]) {
                OpenAPIConstraintViolation("RequestBody $typeName could not be found in ${openAPI.components.requestBodies}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Header>.get(): Header = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/header/".length)
            ensureNotNull(openAPI.components.headers[typeName]) {
                OpenAPIConstraintViolation("Header $typeName could not be found in ${openAPI.components.headers}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<SecurityScheme>.get(): SecurityScheme = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/securitySchemes/".length)
            ensureNotNull(openAPI.components.securitySchemes[typeName]) {
                OpenAPIConstraintViolation("SecurityScheme $typeName could not be found in ${openAPI.components.securitySchemes}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Link>.get(): Link = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/links/".length)
            ensureNotNull(openAPI.components.links[typeName]) {
                OpenAPIConstraintViolation("Link $typeName could not be found in ${openAPI.components.links}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<Callback>.get(): Callback = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/callbacks/".length)
            ensureNotNull(openAPI.components.callbacks[typeName]) {
                OpenAPIConstraintViolation("Callback $typeName could not be found in ${openAPI.components.callbacks}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<PathItem>.get(): PathItem = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/pathItems/".length)
            ensureNotNull(openAPI.components.pathItems[typeName]) {
                OpenAPIConstraintViolation("PathItem $typeName could not be found in ${openAPI.components.pathItems}. Is it missing?")
            }.get()
        }
    }

    tailrec fun ReferenceOr<MediaType>.get(): MediaType = when (this) {
        is ReferenceOr.Value -> value
        is ReferenceOr.Reference -> {
            val typeName = ref.drop("#/components/mediaTypes/".length)
            ensureNotNull(openAPI.components.mediaTypes[typeName]) {
                OpenAPIConstraintViolation("MediaType $typeName could not be found in ${openAPI.components.mediaTypes}. Is it missing?")
            }.get()
        }
    }

    private fun Schema.toObject(context: NamingContext): Model = when {
        properties.isNotEmpty() -> toObject(context, properties)
        additionalProperties != null -> when (val props: AdditionalProperties =
            additionalProperties) {
            // TODO: implement Schema validation
            is AdditionalProperties.PSchema -> Model.FreeFormJson(
                description,
                Constraints.Object(this)
            )

            is Allowed -> if (props.value) Model.FreeFormJson(description, Constraints.Object(this))
            else throw OpenAPIConstraintViolation(
                "No additional properties allowed on object without properties. $this"
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
    private fun allOf(schema: Schema, context: NamingContext): Model {
        val allOf = schema.allOf.map { it.resolve() }
        val ref = allOf.singleOrNull { it is Resolved.Ref && it.value.type == Type.Basic.Object }
        val obj = allOf.singleOrNull { it is Resolved.Value && it.value.type == Type.Basic.Object }
        return when {
            ref != null && obj != null -> {
                val properties = ref.value.properties + obj.value.properties
                Schema(
                    type = Type.Basic.Object,
                    properties = properties,
                    additionalProperties = ref.value.additionalProperties
                        ?: obj.value.additionalProperties,
                    description = ref.value.description,
                    required = ref.value.required.orEmpty() + obj.value.required.orEmpty(),
                    nullable = ref.value.nullable ?: obj.value.nullable,
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

            (schema.additionalProperties as? Allowed)?.value == true -> Model.FreeFormJson(
                schema.description,
                Constraints.Object(schema)
            )

            else -> schema.toUnion(context, schema.allOf)
        }
    }

    fun Schema.toObject(
        context: NamingContext,
        properties: Map<String, ReferenceOr<Schema>>,
    ): Model {
        ensure((additionalProperties as? Allowed)?.value != true) {
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
                (resolved.value.nullable ?: required?.contains(name)?.not()) != false,
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

    private fun Schema.singleDefaultOrNull(): String? = (default as? ExampleValue.Single)?.value

    fun Schema.toOpenEnum(context: NamingContext, values: List<String>): Model.Enum.Open {
        ensure(values.isNotEmpty()) { OpenAPIConstraintViolation("OpenEnum requires at least 1 possible value") }
        val default = singleDefaultOrNull()
        return Model.Enum.Open(context, values, default, description)
    }

    private fun Schema.collection(context: NamingContext): Model.Collection {
        val items =
            ensureNotNull(items?.resolve()) { OpenAPIConstraintViolation("Array type requires items to be defined.") }
        val inner = items.toModel(items.namedOr { context })
        val default = when (val example = default) {
            is ExampleValue.Multiple -> example.values
            is ExampleValue.Single -> {
                val value = example.value
                when {
                    // Translate empty JS array to empty list
                    value == "[]" -> emptyList()
                    // 'null' for a non-nullable collection becomes an empty list
                    value.equals(
                        "null",
                        ignoreCase = true
                    ) -> if (nullable == true) listOf("null") else emptyList()

                    else -> listOf(value)
                }
            }

            null -> null
        }
        return if (uniqueItems == true) Model.Collection.Set(
            inner.value,
            default,
            description,
            Constraints.Collection(this)
        )
        else Model.Collection.List(inner.value, default, description, Constraints.Collection(this))
    }

    fun Schema.toEnum(context: NamingContext, enums: List<String>): Model.Enum.Closed {
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

    private fun Schema.toUnion(
        context: NamingContext, subtypes: List<ReferenceOr<Schema>>,
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

    fun toUnionCaseContext(context: NamingContext, case: Resolved<Schema>): NamingContext =
        when (case) {
            is Resolved.Ref -> Named(case.name)
            is Resolved.Value -> when {
                context is Named && case.value.type == Type.Basic.String && case.value.enum.isNotEmpty() -> NamingContext.Nested(
                    Named(
                        case.value.enum.joinToString(prefix = "", separator = "Or") {
                            it.replaceFirstChar(Char::uppercaseChar)
                        }
                    ),
                    context)

                case.value.type == Type.Basic.Object -> NamingContext.Nested(
                    case.value.properties.firstNotNullOfOrNull { (key, value) ->
                        if (key == "event" || key == "type") value.resolve().value.enum else null
                    }?.singleOrNull()?.let(::Named)
                        ?: TODO("Name Generated for inline objects of unions not yet supported."),
                    context
                )

                case.value.type == Type.Basic.Array -> case.value.items?.resolve()
                    ?.namedOr { if (case.value.uniqueItems == true) Named("Set") else Named("List") }
                    ?.let { NamingContext.Nested(it, context) } ?: context

                else -> context
            }
        }

    private fun nestedModel(resolved: Resolved<Schema>, caseContext: NamingContext): Model? =
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
        operation: Operation, body: RequestBody?, create: (NamingContext) -> NamingContext,
    ): Route.Bodies =
        Route.Bodies(
            body?.required == true,
            body?.content?.entries?.associate { (contentType, mediaType) ->
                ContentType.parse(contentType) to generateRequestModel(
                    mediaType.get().schema?.resolve(),
                    operation,
                    create,
                    body.description
                )
            }.orEmpty(),
            body?.extensions.orEmpty()
        )

    private tailrec fun generateRequestModel(
        schema: Resolved<Schema>?,
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
        Route.Bodies.Body(json.toModel(context).value, json.value, json.value.properties.map { (name, ref) ->
            val p = ref.resolve()
            name to generateRequestModel(p, operation, create, p.value.description)
        }.toMap())
    } ?: (Route.Bodies.Body(Model.Primitive.Unit(description), null, mapOf()))

    private fun Response.isEmpty(): Boolean =
        headers.isEmpty() && content.isEmpty() && links.isEmpty() && extensions.isEmpty()

    fun toResponses(operation: Operation, create: (NamingContext) -> NamingContext): Route.Returns =
        Route.Returns(
            operation.responses.responses.entries.associate { (code, refOrResponse) ->
                val statusCode = HttpStatusCode.fromValue(code)
                val response = refOrResponse.get()

                val m = response.content.map { (contentType, mediaType) ->
                    ContentType.parse(contentType) to generateRequestModel(
                        mediaType.get().schema?.resolve(),
                        operation,
                        create,
                        response.description
                    )
                }.toMap()

                statusCode to Route.ReturnType(
                    m, response.extensions
                )
            }, operation.responses.extensions
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
