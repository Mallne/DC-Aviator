@file:OptIn(ExperimentalJsExport::class)

package cloud.mallne.dicentra.aviator.core.plugins

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
interface AviatorPluginActivationScope {
    fun <ConfigScope : AviatorPluginConfigScope> install(
        plugin: AviatorPlugin<out ConfigScope>,
        config: ConfigScope.() -> Unit = {}
    ): AviatorPluginInstance

    fun add(instance: AviatorPluginInstance)
}
