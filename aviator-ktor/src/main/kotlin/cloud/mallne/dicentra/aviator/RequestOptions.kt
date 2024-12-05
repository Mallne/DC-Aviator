package cloud.mallne.dicentra.aviator

import cloud.mallne.dicentra.aviator.core.ServiceArguments

class RequestOptions(
    val addressInterceptor: (String) -> String = { it },
    val parameters: ServiceArguments = emptyMap(),
)