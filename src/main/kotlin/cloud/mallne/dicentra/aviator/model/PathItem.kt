package cloud.mallne.dicentra.aviator.model

import com.android.volley.Request
import com.google.gson.annotations.Expose

/**
 * Describes the operations available on a single path.
 */
data class PathItem(
    @Expose
    val info: Info? = null,
    @Expose
    val get: Operation? = null,
    @Expose
    val put: Operation? = null,
    @Expose
    val post: Operation? = null,
    @Expose
    val delete: Operation? = null,
    @Expose
    val parameters: List<Parameter>? = null,
) {
    fun getOperations(method: Int): Operation? {
        return when (method) {
            Request.Method.GET -> get
            Request.Method.PUT -> put
            Request.Method.POST -> post
            Request.Method.DELETE -> delete
            else -> {
                throw IllegalArgumentException("Unknown method: $method")
            }
        }
    }

    fun getOperations(): Map<Int, Operation?> {
        return mapOf(
            Request.Method.GET to get,
            Request.Method.PUT to put,
            Request.Method.POST to post,
            Request.Method.DELETE to delete,
        )
    }
}
