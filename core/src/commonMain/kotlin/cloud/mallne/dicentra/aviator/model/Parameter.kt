package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.model.IParameter
import cloud.mallne.dicentra.aviator.core.model.Insides
import kotlinx.serialization.Serializable

/**
 * Describes a single operation parameter.
 */
@Serializable
data class Parameter(
    override val name: String,
    override val `in`: Insides,
    override val description: String? = null,
) : IParameter
