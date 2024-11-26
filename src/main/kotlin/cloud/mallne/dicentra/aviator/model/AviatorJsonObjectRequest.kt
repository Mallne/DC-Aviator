package cloud.mallne.dicentra.aviator.model

import cloud.mallne.xi.areaassist.helpers.LittleHelpers.nullable
import cloud.mallne.xi.areaassist.helpers.functional.Either
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class AviatorJsonObjectRequest(
    method: Int,
    url: String,
    private val request: Either<JSONObject?, Map<String, String>?>? = null,
    listener: Listener<JSONObject>,
    errorListener: ErrorListener? = null,
    private val responseInterceptor: ResponseJsonObjectInterceptor = { it },
    private val contentType: String = "application/json; charset=utf-8",
) : JsonObjectRequest(
    method,
    url,
    request?.leftOrNull(),
    listener,
    errorListener,
) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
        val resp: Response<JSONObject> = super.parseNetworkResponse(response)
        return responseInterceptor.invoke(resp)
    }

    override fun getBodyContentType(): String {
        return contentType
    }

    override fun getBody(): ByteArray? {
        return if (request?.isRight() == true) {
            val params: Map<String, String>? = request.getOrNull()
            val paramsEncoding = paramsEncoding
            if (!params.isNullOrEmpty()) {
                encodeParameters(
                    params.map { it.key.nullable() to it.value.nullable() }.toMap(),
                    paramsEncoding
                )
            } else null
        } else super.getBody()
    }

    private fun encodeParameters(params: Map<String?, String?>, paramsEncoding: String): ByteArray {
        val encodedParams = StringBuilder()
        try {
            for ((key, value) in params) {
                require(!(key == null || value == null)) {
                    String.format(
                        "Request#getParams() or Request#getPostParams() returned a map "
                                + "containing a null key or value: (%s, %s). All keys "
                                + "and values must be non-null.",
                        key, value
                    )
                }
                encodedParams.append(URLEncoder.encode(key, paramsEncoding))
                encodedParams.append('=')
                encodedParams.append(URLEncoder.encode(value, paramsEncoding))
                encodedParams.append('&')
            }
            return encodedParams.toString().toByteArray(charset(paramsEncoding))
        } catch (uee: UnsupportedEncodingException) {
            throw RuntimeException("Encoding not supported: $paramsEncoding", uee)
        }
    }
}

typealias ResponseJsonObjectInterceptor = (Response<JSONObject>) -> Response<JSONObject>