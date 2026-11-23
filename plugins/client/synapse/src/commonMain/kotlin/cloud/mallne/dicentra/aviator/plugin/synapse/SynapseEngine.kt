package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.client.ktor.invoke
import cloud.mallne.dicentra.aviator.core.engine.AviatorEngine
import cloud.mallne.dicentra.aviator.core.engine.OpenApiClientInput
import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.model.ServiceLocator

suspend fun AviatorEngine.account(locator: ServiceLocator): SynapseUser =
    checkNotNull(invoke<SynapseUser>(locator).single().result.getOrThrow().result) {
        "Synapse account response was empty"
    }

suspend fun AviatorEngine.discover(
    locator: ServiceLocator,
    transformationType: ServiceDefinitionTransformationType,
): SynapseDiscoveryResponse? =
    invoke<SynapseDiscoveryResponse>(
        locator,
        OpenApiClientInput(parameters = discoveryRequestParameters(transformationType)),
    ).single().result.getOrThrow().result

fun discoveryRequestParameters(transformationType: ServiceDefinitionTransformationType): RequestParameters =
    RequestParameters(mutableMapOf("transformationType" to RequestParameter.Single(transformationType.name)))
