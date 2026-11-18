@file:OptIn(ExperimentalJsExport::class)

package cloud.mallne.dicentra.aviator.plugin.adapter.json

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.plugins.UniformAviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.UniformAviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.AviatorJsExport
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@AviatorJsExport
object JsonAdapter :
    UniformAviatorPlugin<JsonAdapterPluginConfig> {
    override val identity: String = "adapter:JSON"
    override fun install(config: JsonAdapterPluginConfig.() -> Unit): UniformAviatorPluginInstance {
        val pluginConfig = JsonAdapterPluginConfig()
        config.invoke(pluginConfig)
        return JsonAdapterPluginInstance(pluginConfig)
    }

    @JsExport.Ignore
    val <T : AviatorServiceDataHolder> T.json: Json
        get() = serializers.find { it is Json } as? Json ?: Json
}
