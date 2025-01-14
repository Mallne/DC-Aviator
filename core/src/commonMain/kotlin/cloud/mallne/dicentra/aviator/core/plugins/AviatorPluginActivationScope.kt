package cloud.mallne.dicentra.aviator.core.plugins

interface AviatorPluginActivationScope {
    fun <ConfigScope : AviatorPluginConfigScope> install(
        plugin: AviatorPlugin<out ConfigScope>,
        config: ConfigScope.() -> Unit = {}
    ): AviatorPluginInstance
}