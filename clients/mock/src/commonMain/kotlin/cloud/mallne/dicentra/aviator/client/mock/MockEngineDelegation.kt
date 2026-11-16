package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.core.engine.EngineTemplate
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-pluginMaterialization`
import kotlinx.serialization.json.JsonElement
import cloud.mallne.dicentra.aviator.core.plugins.EngineInvocationContext
import cloud.mallne.dicentra.aviator.core.engine.OpenApiClientInput
import cloud.mallne.dicentra.aviator.core.engine.EngineDefinitionConverter
import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.core.engine.OpenApiOperationDefinitionRequest
import cloud.mallne.dicentra.aviator.model.ServiceLocator

/** An already materialized OpenAPI operation, executed without an orchestration runtime. */
class MockClientDefinition(val service: MockedAviatorService) : ExecutableDefinition {
    override val locator: ServiceLocator get() = service.serviceLocator
    override val delegation: String get() = MockEngineTemplate.IDENTITY
    override val outboundTargets: List<String> get() = service.oas.servers.orEmpty().map { it.url }
    override val pluginConfiguration: Map<String, JsonElement> =
        service.route.`x-dicentra-aviator-pluginMaterialization`.orEmpty().toMap()
}

class MockEngineDefinitionConverter(
    private val converter: MockConverter,
) : EngineDefinitionConverter<OpenApiOperationDefinitionRequest, MockClientDefinition> {
    override fun convert(source: OpenApiOperationDefinitionRequest): MockClientDefinition =
        MockClientDefinition(
            converter.buildOperation(source.api, source.route, source.locator, source.activatePlugins),
        )
}

object MockEngineTemplate : EngineTemplate<OpenApiClientInput<NoBody>, MockExecutionContext> {
    override val identity: String = IDENTITY

    override suspend fun execute(
        definition: ExecutableDefinition,
        input: OpenApiClientInput<NoBody>,
        context: EngineInvocationContext,
    ): Result<MockExecutionContext> {
        require(definition is MockClientDefinition) { "Expected a mock client definition" }
        val plugins = context.clientPluginsOver(definition.service.plugins)
        val service = definition.service.copy(
            plugins = plugins.plugins,
            adapters = plugins.adapters,
            deserializers = plugins.deserializers,
        )
        return Result.success(service.requestContextful(input.options, input.parameters))
    }

    const val IDENTITY = "mock-client"
}
