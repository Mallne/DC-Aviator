package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.core.model.IParameter
import cloud.mallne.dicentra.aviator.core.model.IServer

interface IAviatorService {
    val serviceLocator: IServiceLocator
    val options: ServiceOptions
    val requestType: RequestType
    val server: IServer
    val path: String
    val pathParams: List<IParameter>
    val serviceURL: String
        get() = server.url + path
}