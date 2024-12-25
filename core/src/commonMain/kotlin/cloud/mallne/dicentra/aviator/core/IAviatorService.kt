package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import cloud.mallne.dicentra.aviator.koas.servers.Server

interface IAviatorService {
    val serviceLocator: IServiceLocator
    val options: ServiceOptions
    val requestType: RequestType
    val server: Server
    val path: String
    val pathParams: List<Parameter>
    val serviceURL: String
        get() = server.url + path
}