package cloud.mallne.dicentra.aviator.core.plugins

interface AviatorPluginActivationScope {
    fun <ConfigScope : AviatorPluginConfigScope> install(
        plugin: AviatorPlugin<ConfigScope>,
        config: ConfigScope.() -> Unit = {}
    ): AviatorPluginInstance
}