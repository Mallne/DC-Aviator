@file:OptIn(ExperimentalJsExport::class)

package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import io.ktor.openapi.OpenApiDoc
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JavaScript-friendly entry point for the Fetch client.
 *
 * The Kotlin API uses typed OpenAPI models and reified response serializers. This small facade
 * accepts an OpenAPI JSON document and string-valued path/query/header parameters, and returns
 * JSON text so JavaScript and TypeScript callers can use the package without Kotlin reflection.
 */
@JsExport
class FetchAviatorJsClient internal constructor(
    private val services: Map<String, FetchAviatorService>,
) {
    /** Operation IDs available in the supplied Aviator OpenAPI document. */
    val operationIds: Array<String>
        get() = services.keys.toTypedArray()

    /**
     * Execute an operation and return a JSON envelope containing HTTP status, content type, and
     * response text. Response deserialization is intentionally left to optional Aviator adapters.
     *
     * `parametersJson` is a JSON object mapping OpenAPI parameter names to string values.
     */
    @OptIn(InternalAviatorAPI::class)
    suspend fun request(operationId: String, parametersJson: String = "{}"): String {
        val service = services[operationId]
            ?: throw IllegalArgumentException("Unknown Aviator operationId: $operationId")
        val parameters = Json.decodeFromString<Map<String, String>>(parametersJson)
        val requestParameters = RequestParameters(
            value = parameters.mapValuesTo(mutableMapOf()) { (_, value) -> RequestParameter.Single(value) },
        )
        val context = service.requestContextful<JsonElement, NoBody>(requestParams = requestParameters)
        val response = context.networkChain.lastOrNull()?.response
        return Json.encodeToString(
            buildJsonObject {
                put("status", response?.status?.value ?: 0)
                put("contentType", response?.contentType?.toString().orEmpty())
                put("body", response?.content?.decodeToString().orEmpty())
            },
        )
    }
}

/** Create a JavaScript-callable client from serialized Aviator OpenAPI and a Fetch-compatible function. */
@JsExport
fun createFetchAviatorJsClient(openApiJson: String, fetcher: dynamic): FetchAviatorJsClient {
    val openApi = Json.decodeFromString<OpenApiDoc>(openApiJson)
    val services = FetchAviatorServiceConverter(fetcher = fetcher)
        .build(openApi) {}
        .filterIsInstance<FetchAviatorService>()
        .mapNotNull { service -> service.route.operationId?.let { it to service } }
        .toMap()
    return FetchAviatorJsClient(services)
}
