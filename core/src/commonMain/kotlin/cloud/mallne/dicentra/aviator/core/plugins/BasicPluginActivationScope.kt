package cloud.mallne.dicentra.aviator.core.plugins

class BasicPluginActivationScope : AviatorPluginActivationScope {
    val registry: MutableList<AviatorPluginInstance> = mutableListOf()

    override fun <ConfigScope : AviatorPluginConfigScope> install(
        plugin: AviatorPlugin<ConfigScope>,
        config: ConfigScope.() -> Unit
    ): AviatorPluginInstance {
        val instance = plugin.install(config)
        registry + instance
        return instance
    }
}