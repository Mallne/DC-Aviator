package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginDsl
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry

@AviatorPluginDsl
@OptIn(ExperimentalApi::class)
class OpenTelemetryPluginConfig : AviatorPluginConfigScope {
    override var serviceFilter: MutableList<ServiceLocator> = mutableListOf()
    override val silentLoggingTags: MutableList<String> = mutableListOf()

    var openTelemetry: OpenTelemetry? = null

    var errorStatusCodes: IntRange = 500..599
}
