package cloud.mallne.dicentra.aviator

import cloud.mallne.dicentra.aviator.core.IAviatorService
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.RequestType
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import cloud.mallne.dicentra.aviator.koas.servers.Server
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class AviatorService<Request : @Serializable Any>(
    override val serviceLocator: ServiceLocator,
    override val options: ServiceOptions,
    override val requestType: RequestType,
    override val server: Server,
    override val path: String,
    override val pathParams: List<Parameter>,
    val method: HttpMethod,
    val client: HttpClient
) : IAviatorService {
    override val serviceURL: String = server.url + path

    init {
        AviatorServiceUtils.validate(this)
    }

    inline fun <reified T : InflatedServiceOptions> optionBundle(): T = AviatorServiceUtils.optionBundle(options)

    suspend fun request(
        requestBody: Request? = null,
        options: RequestOptions = RequestOptions()
    ): HttpResponse {
        val address =
            options.addressInterceptor(AviatorServiceUtils.getAddress(options.parameters, serviceURL, pathParams))

        val response = client.request(address) {
            method = this@AviatorService.method
            if (requestBody != null) {
                setBody<Any>(requestBody)
            }

        }
        return response
    }
}

typealias ServiceArguments = Map<String, String>