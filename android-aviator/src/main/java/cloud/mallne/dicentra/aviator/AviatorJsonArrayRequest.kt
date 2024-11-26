package cloud.mallne.dicentra.aviator

import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.toolbox.JsonArrayRequest
import org.json.JSONArray

class AviatorJsonArrayRequest(
    method: Int,
    url: String,
    jsonRequest: JSONArray? = null,
    listener: Listener<JSONArray>,
    errorListener: ErrorListener? = null,
    private val responseInterceptor: ResponseJsonArrayInterceptor = { it },
) : JsonArrayRequest(
    method,
    url,
    jsonRequest,
    listener,
    errorListener,
) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONArray> {
        val resp: Response<JSONArray> = super.parseNetworkResponse(response)
        return responseInterceptor.invoke(resp)
    }
}

typealias ResponseJsonArrayInterceptor = (Response<JSONArray>) -> Response<JSONArray>