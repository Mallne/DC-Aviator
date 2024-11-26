package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.model.IInfo
import kotlinx.serialization.Serializable

@Serializable
data class Info(
    override val title: String,
    override val description: String? = null,
    override val license: License? = null,
    override val version: String
) : IInfo