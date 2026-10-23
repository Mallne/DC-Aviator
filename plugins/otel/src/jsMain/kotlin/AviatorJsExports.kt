@file:OptIn(ExperimentalJsExport::class)

import cloud.mallne.dicentra.aviator.plugin.otel.OpenTelemetryPlugin as OpenTelemetryPluginImpl

@JsExport
@JsName("AviatorOpenTelemetryPlugin")
val OpenTelemetryPluginExport = OpenTelemetryPluginImpl
