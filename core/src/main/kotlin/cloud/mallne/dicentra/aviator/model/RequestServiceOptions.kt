package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.ServiceArguments

open class RequestServiceOptions<ResponseType>(
    val addressInterceptor: (String) -> String = { it },
    val doThat: (ResponseType) -> Unit = {},
    val onNetworkError: (Exception) -> Unit = {},
    val shouldCache: Boolean = true,
    val parameters: ServiceArguments = emptyMap(),
    val responseInterceptor: (ResponseType) -> ResponseType = { it },
)