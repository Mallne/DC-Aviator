package cloud.mallne.dicentra.aviator.core.plugins

enum class PluginActivation { PASSIVE, DEFINITION }

internal fun defaultClientActivation(instance: AviatorPluginInstance): PluginActivation =
    if (instance is AviatorAdapterPluginInstance) PluginActivation.PASSIVE else PluginActivation.DEFINITION

private fun requirePassiveAdapter(instance: AviatorPluginInstance, activation: PluginActivation) {
    require(instance !is AviatorAdapterPluginInstance || activation == PluginActivation.PASSIVE) {
        "Body adapters are passive and are selected by request/response representation"
    }
}

data class UniformPluginRegistration(
    val instance: UniformAviatorPluginInstance,
    val activation: PluginActivation = defaultClientActivation(instance),
) {
    init {
        requirePassiveAdapter(instance, activation)
    }
}

data class EnginePluginRegistration(
    val instance: EngineAviatorPluginInstance,
    val activation: PluginActivation = PluginActivation.DEFINITION,
)

data class DelegationPluginRegistration(
    val instance: DelegationAviatorPluginInstance,
    val activation: PluginActivation = PluginActivation.DEFINITION,
)

data class ClientPluginRegistration(
    val instance: AviatorPluginInstance,
    val activation: PluginActivation = defaultClientActivation(instance),
) {
    init {
        requirePassiveAdapter(instance, activation)
    }
}
