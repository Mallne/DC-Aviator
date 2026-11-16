package cloud.mallne.dicentra.aviator.core.plugins

@AviatorJsExport
interface AviatorPluginActivationScope {
    fun <ConfigScope : AviatorPluginConfigScope> install(
        plugin: AviatorPlugin<out ConfigScope>,
        config: ConfigScope.() -> Unit = {}
    ): AviatorPluginInstance

    fun add(instance: AviatorPluginInstance)
}
