@file:OptIn(ExperimentalJsExport::class)

import cloud.mallne.dicentra.aviator.plugin.synapse.SynapsePlugin as SynapsePluginImpl

@JsExport
@JsName("AviatorSynapsePlugin")
val SynapsePluginExport = SynapsePluginImpl
