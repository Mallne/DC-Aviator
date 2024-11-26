package cloud.mallne.dicentra.aviator

import cloud.mallne.dicentra.aviator.core.ServiceArguments
import cloud.mallne.dicentra.aviator.model.RequestServiceOptions
import com.android.volley.DefaultRetryPolicy
import com.android.volley.RetryPolicy

class RequestOptions<ResponseType>(
    addressInterceptor: (String) -> String = { it },
    doThat: (ResponseType) -> Unit = {},
    onNetworkError: (Exception) -> Unit = {},
    shouldCache: Boolean = true,
    parameters: ServiceArguments = emptyMap(),
    responseInterceptor: (ResponseType) -> ResponseType = { it },
    val retryPolicy: RetryPolicy = DefaultRetryPolicy(5000, 3, 1f),
) : RequestServiceOptions<ResponseType>(
    addressInterceptor,
    doThat,
    onNetworkError,
    shouldCache,
    parameters,
    responseInterceptor,
)