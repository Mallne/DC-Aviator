@file:OptIn(ExperimentalJsExport::class)

import cloud.mallne.dicentra.aviator.plugin.weaver.WeaverPlugin as WeaverPluginImpl

@JsExport
@JsName("AviatorWeaverPlugin")
val WeaverPluginExport = WeaverPluginImpl
