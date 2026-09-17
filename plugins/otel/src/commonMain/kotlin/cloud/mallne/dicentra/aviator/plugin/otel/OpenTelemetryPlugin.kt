package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import io.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
object OpenTelemetryPlugin : AviatorPlugin<OpenTelemetryPluginConfig> {
    override val identity: String = "DC-AV-OpenTelemetry"

    override fun install(config: OpenTelemetryPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = OpenTelemetryPluginConfig()
        config.invoke(pluginConfig)
        requireNotNull(pluginConfig.openTelemetry) {
            "OpenTelemetry instance must be configured via OpenTelemetryPluginConfig.openTelemetry"
        }
        return OpenTelemetryPluginInstance(
            configurationBundle = pluginConfig,
            x = OpenTelemetryPluginExecutor(pluginConfig)
        )
    }
}
