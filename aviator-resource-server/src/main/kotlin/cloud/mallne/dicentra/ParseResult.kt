package cloud.mallne.dicentra

import cloud.mallne.dicentra.aviator.koas.OpenAPI
import kotlinx.serialization.Serializable

@Serializable
data class ParseResult(
    val oas: OpenAPI,

    )
