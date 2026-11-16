package cloud.mallne.dicentra.aviator.core.plugins

enum class PluginActivation { PASSIVE, DEFINITION }

data class UniformPluginRegistration(
    val instance: UniformAviatorPluginInstance,
    val activation: PluginActivation = PluginActivation.DEFINITION,
)

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
    val activation: PluginActivation = PluginActivation.DEFINITION,
) {
    init {
        require(instance !is AviatorAdapterPluginInstance || activation == PluginActivation.PASSIVE) {
            "Body adapters are passive and are selected by request/response representation"
        }
    }
}
