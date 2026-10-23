@file:OptIn(ExperimentalJsExport::class)

package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@AviatorPluginDsl
@JsExport
interface AviatorPluginConfigScope {
    var serviceFilter: MutableList<ServiceLocator>
    val silentLoggingTags: MutableList<String>
        get() = mutableListOf()
}
