@file:OptIn(ExperimentalJsExport::class)

import cloud.mallne.dicentra.aviator.plugin.interception.InterceptionPlugin as InterceptionPluginImpl

@JsExport
@JsName("AviatorInterceptionPlugin")
val InterceptionPluginExport = InterceptionPluginImpl
