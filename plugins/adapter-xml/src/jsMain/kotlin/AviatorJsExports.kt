@file:OptIn(ExperimentalJsExport::class)

import cloud.mallne.dicentra.aviator.plugin.adapter.xml.XmlAdapter as XmlAdapterPlugin

@JsExport
@JsName("AviatorXmlAdapter")
val XmlAdapterExport = XmlAdapterPlugin
