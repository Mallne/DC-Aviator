package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeaverServiceObject(
    @SerialName("x-dicentra-weaver-schema")
    val schema: WeaverSchema
) : InflatedServiceOptions
