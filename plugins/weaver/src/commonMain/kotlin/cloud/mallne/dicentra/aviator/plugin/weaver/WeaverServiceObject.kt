package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class WeaverServiceObject(
    @SerialName("x-dicentra-weaver-schema")
    val schema: WeaverSchema
) : InflatedServiceOptions {
    override fun usable(): ServiceOptions = Json.encodeToJsonElement(this)
}
