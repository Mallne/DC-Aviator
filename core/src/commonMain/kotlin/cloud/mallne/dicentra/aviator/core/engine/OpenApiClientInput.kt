package cloud.mallne.dicentra.aviator.core.engine

import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.ktor.openapi.OpenApiDoc
import kotlinx.serialization.Serializable

/** Typed operation input shared by the Mock, Ktor and Fetch engine delegations. */
data class OpenApiClientInput<B : @Serializable Any>(
    val body: B? = null,
    val options: RequestOptions = emptyMap(),
    val parameters: RequestParameters = RequestParameters(),
)

/** Converts an Aviator OpenAPI document into client definitions keyed by declared locators. */
interface EngineDefinitionConverter<out Definition : ExecutableDefinition> {
    fun convert(
        api: OpenApiDoc,
        plugins: AviatorPluginActivationScope.() -> Unit = {},
    ): List<Definition>
}

/** Shared OpenAPI operation source passed to client definition converters. */
data class OpenApiOperationDefinitionRequest(
    val api: OpenApiDoc,
    val route: Route,
    val locator: ServiceLocator,
    val activatePlugins: AviatorPluginActivationScope.() -> Unit = {},
)
