package cloud.mallne.dicentra.aviator

import android.util.Log
import cloud.mallne.dicentra.aviator.core.IAviatorService
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.RequestType
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.model.Parameter
import cloud.mallne.dicentra.aviator.model.Server
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class AviatorService<Request : JsonElement, Response : JsonElement?>(
    override val serviceLocator: ServiceLocator,
    override val options: ServiceOptions,
    override val requestType: RequestType,
    override val server: Server,
    override val path: String,
    override val pathParams: List<Parameter>,
    val method: Int,
) : IAviatorService {
    override val serviceURL: String = server.url + path

    init {
        AviatorServiceUtils.validate(this)
    }

    inline fun <reified T : InflatedServiceOptions> optionBundle(): T = AviatorServiceUtils.optionBundle(options)

    fun request(
        requestBody: Request? = null,
        options: RequestOptions<Response> = RequestOptions()
    ): JsonRequest<Response> {
        val address = options.addressInterceptor(AviatorServiceUtils.getAddress(options, serviceURL, pathParams))
        Log.d("AviatorService", "Requesting $address")
        val request = when (requestType) {
            RequestType.OBJECT -> requestO(address, requestBody as JsonObject?, options)
            RequestType.ARRAY -> requestA(address, requestBody as JsonArray?, options)
        }
        return request
    }

    private fun requestO(
        address: String,
        requestBody: JsonObject?,
        options: RequestOptions<Response>
    ): JsonObjectRequest {
        val request = AviatorJsonObjectRequest(
            method,
            address,
            requestBody,
            { options.doThat(it) },
            options.onNetworkError,
            responseInterceptor = options.responseInterceptor,
        )
        request.setShouldCache(options.shouldCache)
        request.retryPolicy = options.retryPolicy
        return request
    }

    private fun requestA(
        address: String,
        requestBody: JsonArray?,
        options: RequestOptions<Response>
    ): JsonArrayRequest {
        val request = AviatorJsonArrayRequest(
            method,
            address,
            requestBody,
            { options.doThat(it) },
            options.onNetworkError,
            responseInterceptor = options.responseInterceptor
        )
        request.setShouldCache(options.shouldCache)
        request.retryPolicy = options.retryPolicy
        return request
    }
}

typealias ServiceArguments = Map<String, String>