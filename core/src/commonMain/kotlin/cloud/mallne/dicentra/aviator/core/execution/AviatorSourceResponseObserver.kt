package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** A response as received before response-stage plugins can modify the network chain. */
class AviatorSourceResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val contentType: String,
    val body: ByteArray?,
)

/** Optional per-invocation observer of source responses, isolated from response-stage mutations. */
abstract class AviatorSourceResponseObserver : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AviatorSourceResponseObserver>

    abstract suspend fun onResponses(responses: List<AviatorSourceResponse>)
}

internal suspend fun observeSourceResponses(responses: List<NetworkResponse<out NetworkHeader>>) {
    val observer = currentCoroutineContext()[AviatorSourceResponseObserver] ?: return
    observer.onResponses(responses.map { response ->
        AviatorSourceResponse(
            response.status.value,
            response.headers.values.toStringList().mapValues { it.value.toList() },
            response.contentType.toString(),
            response.content?.copyOf(),
        )
    })
}
