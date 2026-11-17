@file:OptIn(ExperimentalJsExport::class)

import cloud.mallne.dicentra.aviator.plugin.httpauth.HttpAuthPlugin as HttpAuthPluginImpl

@JsExport
@JsName("AviatorHttpAuthPlugin")
val HttpAuthPluginExport = HttpAuthPluginImpl
