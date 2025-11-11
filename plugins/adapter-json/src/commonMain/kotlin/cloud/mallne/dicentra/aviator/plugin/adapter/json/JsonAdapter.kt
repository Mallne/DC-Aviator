package cloud.mallne.dicentra.aviator.plugin.adapter.json

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import kotlinx.serialization.json.Json

object JsonAdapter : AviatorPlugin<JsonAdapterPluginConfig> {
    override val identity: String = "adapter:JSON"
    override fun install(config: JsonAdapterPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = JsonAdapterPluginConfig()
        config.invoke(pluginConfig)
        return JsonAdapterPluginInstance(pluginConfig)
    }

    val <T : AviatorServiceDataHolder> T.json: Json
        get() = serializers.find { it is Json } as? Json ?: Json
}