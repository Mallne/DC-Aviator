package cloud.mallne.dicentra.aviator.core.engine

import cloud.mallne.dicentra.aviator.model.ServiceLocator

/** Resolution order is significant and must be stable for first-match modes. */
fun interface DefinitionCoordinator {
    suspend fun resolve(locator: ServiceLocator): List<ExecutableDefinition>
}

class InMemoryDefinitionCoordinator(definitions: List<ExecutableDefinition>) : DefinitionCoordinator {
    private val definitions = definitions.toList()

    override suspend fun resolve(locator: ServiceLocator): List<ExecutableDefinition> =
        definitions.filter { it.locator == locator }
}
