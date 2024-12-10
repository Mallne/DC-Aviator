package cloud.mallne.dicentra.aviator.core.model

import cloud.mallne.dicentra.aviator.core.IServiceLocator
import cloud.mallne.dicentra.aviator.core.RequestType
import cloud.mallne.dicentra.aviator.core.ServiceOptions

/**
 * Describes a single API operation on a path.
 */
interface IOperation {
    val `x-serviceDelegateCall`: IServiceLocator?
    val `x-serviceOptions`: ServiceOptions?
    val `x-requestType`: RequestType?
    val summary: String?
    val description: String?
    val parameters: List<IParameter>?
    val deprecated: Boolean
}
