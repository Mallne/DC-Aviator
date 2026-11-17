@file:OptIn(ExperimentalJsExport::class)

package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.plugins.UniformAviatorPlugin
import io.opentelemetry.kotlin.ExperimentalApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalApi::class)
@JsExport
object OpenTelemetryPlugin : UniformAviatorPlugin<OpenTelemetryPluginConfig> {
    override val identity: String = "DC-AV-OpenTelemetry"

    override fun install(config: OpenTelemetryPluginConfig.() -> Unit): OpenTelemetryPluginInstance {
        val pluginConfig = OpenTelemetryPluginConfig()
        config.invoke(pluginConfig)
        requireNotNull(pluginConfig.openTelemetry) {
            "OpenTelemetry instance must be configured via OpenTelemetryPluginConfig.openTelemetry"
        }
        return OpenTelemetryPluginInstance(
            configurationBundle = pluginConfig,
            x = OpenTelemetryPluginExecutor(pluginConfig),
        )
    }
}
