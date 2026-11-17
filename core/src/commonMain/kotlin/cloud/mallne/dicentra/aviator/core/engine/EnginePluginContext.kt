package cloud.mallne.dicentra.aviator.core.engine

import cloud.mallne.dicentra.aviator.model.ServiceLocator

/** Definition-scoped execution data exposed to engine plugins. */
data class EnginePluginContext<out I : Any>(
    val locator: ServiceLocator,
    val definition: ExecutableDefinition,
    val input: I,
)
