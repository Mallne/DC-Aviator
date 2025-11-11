package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext

interface AviatorPlugin<ConfigScope : AviatorPluginConfigScope> {
    val identity: String
    fun install(config: ConfigScope.() -> Unit = {}): AviatorPluginInstance
    fun <T> getThisPlugin(context: AviatorExecutionContext<*, *>): T? = context.dataHolder.plugins.find { it.identity == identity } as? T
}