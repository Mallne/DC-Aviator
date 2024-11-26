package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.model.ILicense
import kotlinx.serialization.Serializable

@Serializable
data class License(
    override val name: String,
    override val url: String? = null
) : ILicense