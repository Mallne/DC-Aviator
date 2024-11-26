package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.IAviatorApi
import kotlinx.serialization.Serializable

/**
 * This is the root document object of the OpenAPI document.
 */
@Serializable
data class AviatorAPI(
    override val `x-dicentra-aviator`: String = "1.0.0", // Required: Aviator version (e.g., "1.0.1")
    override val info: Info, // Required: Metadata about the API
    override val server: Server, // server where the API is hosted
    override val paths: Map<String, PathItem>, // Available paths and operations
) : IAviatorApi
