package cloud.mallne.dicentra.aviator.plugin.httpauth

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import io.ktor.openapi.*
import kotlin.io.encoding.Base64

object HttpAuthPlugin : AviatorPlugin<HttpAuthPluginConfig> {
    const val PARAMETER = "Authorization"
    const val SCHEME_HINT = "SECURITY_REQUIREMENT_HINT"
    override val identity: String = "DC-AV-BearerAuth"
    override fun install(config: HttpAuthPluginConfig.() -> Unit): HttpAuthPluginInstance {
        val pluginConfig = HttpAuthPluginConfig()
        config.invoke(pluginConfig)
        return HttpAuthPluginInstance(pluginConfig, identity, PluginStagedExecutorBuilder.steps {
            after(AviatorExecutionStages.FormingRequest) { context ->
                val param = pluginConfig.lazyToken?.invoke()?.let { RequestParameter.Single(it) }
                    ?: context.requestParams[PARAMETER]
                val hint = pluginConfig.lazySchemaHint?.invoke()?.let { RequestParameter.Single(it) }
                    ?: context.requestParams[SCHEME_HINT]
                if (param == null || param.isEmpty()) {
                    context.log("DC-HTTPAUTH-NO-TOKEN") { warn("No token provided for authentication") }
                    return@after
                }

                val usable =
                    context.dataHolder.route.securities.methods.filter { it.scheme.type == SecuritySchemeType.HTTP && it.scheme is HttpSecurityScheme }
                val using = (usable.find { it.name == hint?.toString() } ?: usable.firstOrNull())
                val encoded = if (pluginConfig.doBase64Encode) {
                    Base64.encode(param.toString().encodeToByteArray())
                } else {
                    param.toString()
                }
                if (using == null) {
                    context.log("DC-HTTPAUTH-NO-SCHEME") { warn("No security scheme found for authentication") }
                    return@after
                }

                context.log("DC-HTTPAUTH-SCHEME") { info("Using security scheme ${using.name} for authentication with Header: ${(using.scheme as? HttpSecurityScheme)?.scheme}") }
                context.log("DC-HTTPAUTH-TOKEN") { debug("Using encoded token: $encoded") }

                context.networkChain.forEach { chain ->
                    chain.request?.headers?.values?.put(
                        key = PARAMETER,
                        value = RequestParameter.Single("${(using.scheme as? HttpSecurityScheme)?.scheme} $encoded")
                    )
                }
            }
        })
    }
}