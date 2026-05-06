package cloud.mallne.dicentra.aviator.plugin.httpauth

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginDsl
import cloud.mallne.dicentra.aviator.model.ServiceLocator

@AviatorPluginDsl
class HttpAuthPluginConfig : AviatorPluginConfigScope {
    override var serviceFilter: MutableList<ServiceLocator> = mutableListOf()
    override val silentLoggingTags: MutableList<String> = mutableListOf()
    var doBase64Encode: Boolean = false
    var lazyToken: (suspend () -> String?)? = null
    var lazySchemaHint: (suspend () -> String?)? = null
}