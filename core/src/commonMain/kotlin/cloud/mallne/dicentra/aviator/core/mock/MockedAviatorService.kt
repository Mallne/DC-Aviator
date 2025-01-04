package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.IAviatorService
import cloud.mallne.dicentra.aviator.core.RequestType
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import cloud.mallne.dicentra.aviator.koas.servers.Server
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.Serializable

@Serializable
data class MockedAviatorService(
    override val serviceLocator: ServiceLocator,
    override val options: ServiceOptions,
    override val requestType: RequestType,
    override val server: Server,
    override val path: String,
    override val pathParams: List<Parameter>
) : IAviatorService