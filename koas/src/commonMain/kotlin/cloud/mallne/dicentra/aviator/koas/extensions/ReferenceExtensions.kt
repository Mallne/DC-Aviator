package cloud.mallne.dicentra.aviator.koas.extensions

import io.ktor.openapi.ReferenceOr.*


object ReferenceExtensions {
    private const val schemas: String = "#/components/schemas/"
    private const val responses: String = "#/components/responses/"
    private const val parameters: String = "#/components/parameters/"
    private const val examples: String = "#/components/examples/"
    private const val requestBodies: String = "#/components/requestBodies/"
    private const val headers: String = "#/components/headers/"
    private const val securitySchemes: String = "#/components/securitySchemes/"
    private const val links: String = "#/components/links/"
    private const val callbacks: String = "#/components/callbacks/"
    private const val pathItems: String = "#/components/pathItems/"
    private const val mediaTypes: String = "#/components/mediaTypes/"

    fun Companion.schema(name: String): Reference = Reference("$schemas$name")
    fun Companion.response(name: String): Reference = Reference("$responses$name")
    fun Companion.parameter(name: String): Reference = Reference("$parameters$name")
    fun Companion.example(name: String): Reference = Reference("$examples$name")
    fun Companion.requestBody(name: String): Reference = Reference("$requestBodies$name")
    fun Companion.header(name: String): Reference = Reference("$headers$name")
    fun Companion.securityScheme(name: String): Reference =
        Reference("$securitySchemes$name")

    fun Companion.link(name: String): Reference = Reference("$links$name")
    fun Companion.callback(name: String): Reference = Reference("$callbacks$name")
    fun Companion.pathItem(name: String): Reference = Reference("$pathItems$name")
    fun Companion.mediaType(name: String): Reference = Reference("$mediaTypes$name")
}