package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.RequestType
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.model.IOperation
import kotlinx.serialization.Serializable


/**
 * Describes a single API operation on a path.
 */
@Serializable
data class Operation(
    override val `x-serviceDelegateCall`: ServiceLocator? = null,
    override val `x-serviceOptions`: ServiceOptions? = null,
    override val `x-requestType`: RequestType? = null,
    override val summary: String? = null,
    override val description: String? = null,
    override val parameters: List<Parameter>? = null,
    override val deprecated: Boolean = false,
) : IOperation
