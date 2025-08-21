package cloud.mallne.dicentra.aviator.plugin.httpauth

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import cloud.mallne.dicentra.aviator.koas.security.SecurityScheme

object HttpAuthPlugin : AviatorPlugin<HttpAuthPluginConfig> {
    const val PARAMETER = "Authorization"
    const val SCHEME_HINT = "SECURITY_REQUIREMENT_HINT"
    override val identity: String = "DC-AV-BearerAuth"
    override fun install(config: HttpAuthPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = HttpAuthPluginConfig()
        config.invoke(pluginConfig)
        return HttpAuthPluginInstance(pluginConfig, identity, PluginStagedExecutorBuilder.steps {
            after(AviatorExecutionStages.FormingRequest) { context ->
                val param = context.requestParams[PARAMETER]
                val hint = context.requestParams[SCHEME_HINT]
                if (param == null || param.isEmpty()) return@after

                val usable =
                    context.dataHolder.route.securities.methods.filter { it.scheme.type == SecurityScheme.Type.HTTP && it.scheme.inside == SecurityScheme.In.HEADER }
                val using = usable.find { it.name == hint?.firstOrNull() } ?: usable.firstOrNull()
                if (using == null) return@after

                context.networkChain.forEach { chain ->
                    chain.request?.headers?.values?.put(
                        key = "Authorization",
                        value = listOf("${using.scheme.scheme}: ${param.firstOrNull()}")
                    )
                }
            }
        })
    }
}