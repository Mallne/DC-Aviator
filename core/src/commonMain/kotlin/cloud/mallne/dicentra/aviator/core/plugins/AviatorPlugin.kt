package cloud.mallne.dicentra.aviator.core.plugins

interface AviatorPlugin<ConfigScope : AviatorPluginConfigScope> {
    val identity: String
    fun install(config: ConfigScope.() -> Unit): AviatorPluginInstance
}