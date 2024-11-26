package cloud.mallne.dicentra.aviator.model

import android.util.Log
import cloud.mallne.xi.areaassist.aviator.service.model.*
import cloud.mallne.xi.areaassist.exceptions.ServiceException
import cloud.mallne.xi.areaassist.helpers.functional.Either
import cloud.mallne.xi.areaassist.helpers.functional.left
import cloud.mallne.xi.areaassist.helpers.functional.right
import com.android.volley.DefaultRetryPolicy
import com.android.volley.RetryPolicy
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import org.json.JSONArray
import org.json.JSONObject

class AviatorService(
    val serviceLocator: ServiceLocator,
    val options: ServiceOptions,
    val requestType: RequestType,
    private val server: Server,
    private val path: String,
    val pathParams: List<Parameter>,
    private val method: Int,
) {
    val serviceURL: String = server.url + path

    init {
        validate()
    }

    inline fun <reified T : InflatedServiceOptions> optionBundle(): T =
        InflatedServiceOptions.inflate<T>(options)

    fun request(
        requestBody: Either<JSONObject, JSONArray>? = null,
        options: Options = Options()
    ): Either<JsonRequest<JSONObject>, JsonRequest<JSONArray>> {
        val address = options.addressInterceptor(getAddress(options))
        Log.d("AviatorService", "Requesting $address")
        val request: Either<JsonRequest<JSONObject>, JsonRequest<JSONArray>> = when (requestType) {
            RequestType.OBJECT -> requestO(address, requestBody?.leftOrNull(), options).left()
            RequestType.ARRAY -> requestA(address, requestBody?.getOrNull(), options).right()
        }
        return request
    }

    private fun requestO(
        address: String,
        requestBody: JSONObject?,
        options: Options
    ): JsonObjectRequest {
        val responseInterceptor: ResponseJsonObjectInterceptor =
            if (options.responseInterceptor?.isLeft() == true) {
                options.responseInterceptor.leftOrNull()!!
            } else {
                interceptorObject
            }
        val request = AviatorJsonObjectRequest(
            method,
            address,
            requestBody.left(),
            { options.doThat(it.left()) },
            options.onNetworkError,
            responseInterceptor = responseInterceptor,
        )
        request.setShouldCache(options.shouldCache)
        request.setRetryPolicy(options.retryPolicy)
        return request
    }

    private fun requestA(
        address: String,
        requestBody: JSONArray?,
        options: Options
    ): JsonArrayRequest {
        val responseInterceptor: ResponseJsonArrayInterceptor =
            if (options.responseInterceptor?.isRight() == true) {
                options.responseInterceptor.getOrNull()!!
            } else {
                interceptorArray
            }
        val request = AviatorJsonArrayRequest(
            method,
            address,
            requestBody,
            { options.doThat(it.right()) },
            options.onNetworkError,
            responseInterceptor = responseInterceptor
        )
        request.setShouldCache(options.shouldCache)
        request.setRetryPolicy(options.retryPolicy)
        return request
    }


    private fun getAddress(options: Options): String {
        val pathItems = serviceURL.split("/")
        val pis = pathItems.map { pi ->
            val template = template(pi)
            if (template != null) {
                val param = pathParams.find { it.name == template }
                if (param != null) {
                    val vl = options.parameters[param.name]
                    if (vl != null) {
                        pi.replace("{$template}", vl)
                    }
                }
            }
            pi
        }.toMutableList()

        //fabricate the Query String
        val params = pathParams.filter { it.`in` == Parameter.Companion.Insides.query }
        val optionsMap = params.mapNotNull {
            val vl = options.parameters[it.name]
            if (vl != null) {
                it.name to vl
            } else {
                null
            }
        }.toMap()
        val optionsString = optionsMap.map { "${it.key}=${it.value}" }.joinToString("&")

        return if (optionsString.isNotEmpty()) {
            "${pis.joinToString("/")}?${optionsString}"
        } else {
            pis.joinToString("/")
        }
    }

    private fun validate() {
        validatePathParams()
    }

    private fun validatePathParams() {
        if (!pathParams.all { it.`in` == Parameter.Companion.Insides.path || it.`in` == Parameter.Companion.Insides.query }) throw ServiceException(
            "Not all path params are specified as such"
        )

        val pathParms = pathParams.filter { it.`in` == Parameter.Companion.Insides.path }
        val paths = path.split("/")
        val params = paths.mapNotNull { template(it) }
        if (params.size > pathParms.size) throw ServiceException("Not all path params are specified")
        params.forEach { param ->
            if (!pathParms.map { it.name }
                    .contains(param)) throw ServiceException("Path param $param is not specified")
        }
    }

    private fun template(string: String): String? {
        return if (string.contains("{") && string.contains("}")) {
            // get the string between the braces
            string.substring(string.indexOf("{") + 1, string.indexOf("}"))
        } else {
            null
        }
    }

    data class Options(
        val addressInterceptor: (String) -> String = { it },
        val doThat: (Either<JSONObject, JSONArray>) -> Unit = {},
        val onNetworkError: (VolleyError) -> Unit = {},
        val shouldCache: Boolean = true,
        val parameters: ServiceArguments = emptyMap(),
        val retryPolicy: RetryPolicy = DefaultRetryPolicy(5000, 3, 1f),
        val responseInterceptor: Either<ResponseJsonObjectInterceptor, ResponseJsonArrayInterceptor>? = null
    )

    companion object {
        val interceptorObject: ResponseJsonObjectInterceptor = { it }
        val interceptorArray: ResponseJsonArrayInterceptor = { it }
    }
}

typealias ServiceArguments = Map<String, String>