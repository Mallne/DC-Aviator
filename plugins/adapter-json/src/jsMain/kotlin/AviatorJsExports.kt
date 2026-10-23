@file:OptIn(ExperimentalJsExport::class)

import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter as JsonAdapterPlugin

@JsExport
@JsName("AviatorJsonAdapter")
val JsonAdapterExport = JsonAdapterPlugin
