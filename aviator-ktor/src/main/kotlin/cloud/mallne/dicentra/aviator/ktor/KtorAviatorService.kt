package cloud.mallne.dicentra.aviator.ktor

import cloud.mallne.dicentra.aviator.core.IAviatorService
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline
import cloud.mallne.dicentra.aviator.core.execution.BasicExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.*

class KtorAviatorService(
    override val serviceLocator: ServiceLocator,
    override val options: ServiceOptions,
    val client: HttpClient,
    override val plugins: List<AviatorPluginInstance>,
    override val route: Route,
    override val oas: OpenAPI
) : IAviatorService {

    init {
        AviatorServiceUtils.validate(this)
    }

    inline fun <reified T : InflatedServiceOptions> optionBundle(): T = AviatorServiceUtils.optionBundle(options)

    suspend fun request(
        requestBody: Request? = null,
        options: RequestOptions = RequestOptions()
    ): HttpResponse {
        val executor: StagedExecutor = object : StagedExecutor {

        }
        val pipeline = AviatorExecutionPipeline(
            context = BasicExecutionContext(),
            plugins = plugins,
            executor = executor,
        )

        val context = pipeline.run()

        val response = client.request(address) {
            method = this@KtorAviatorService.method
            if (requestBody != null) {
                setBody<Any>(requestBody)
            }

        }
        return response
    }
}