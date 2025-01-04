package cloud.mallne.dicentra.aviator.core

import kotlinx.serialization.Serializable

@Serializable
data class RequestOptions(
    val parameters: ServiceArguments = emptyMap(),
)