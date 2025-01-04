package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.core.plugins.BasicPluginActivationScope
import cloud.mallne.dicentra.aviator.koas.OpenAPI

object MockConverter : APIToServiceConverter {
    override fun build(api: OpenAPI, plugins: AviatorPluginActivationScope.() -> Unit): MockedAviatorService {
        val mockPluginActivationScope = BasicPluginActivationScope()
        plugins.invoke(mockPluginActivationScope)
    }
}