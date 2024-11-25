package cloud.mallne.dicentra.api.settings.modules.vn

import kotlinx.serialization.Serializable

@Serializable
data class Instance(
    val superAdmin: Boolean = false,
    val instanceId: String,
    val admin: Boolean = false,
)