package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

data class OpenTelemetryPluginInstance(
    override val configurationBundle: OpenTelemetryPluginConfig,
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any>
) : AviatorPluginInstance {
    override val identity: String = OpenTelemetryPlugin.identity
    override val logMetadataProvider: AviatorLogger.Companion.MetadataProvider = { logId, context ->
        val traceId = (context.bundle[SpanKeys.TRACE_ID] as? JsonPrimitive)?.content
        val spanId = (context.bundle[SpanKeys.SPAN_ID] as? JsonPrimitive)?.content
        buildMap {
            if (traceId != null) put("traceId", traceId)
            if (spanId != null) put("spanId", spanId)
        }
    }
}
