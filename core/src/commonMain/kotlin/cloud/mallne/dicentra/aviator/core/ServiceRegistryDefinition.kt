package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.model.ServiceLocator

interface ServiceRegistryDefinition {
    val serviceLocator: String
    fun locator(flavour: ServiceMethods): ServiceLocator {
        return ServiceLocator(
            this.serviceLocator,
            flavour
        )
    }
}