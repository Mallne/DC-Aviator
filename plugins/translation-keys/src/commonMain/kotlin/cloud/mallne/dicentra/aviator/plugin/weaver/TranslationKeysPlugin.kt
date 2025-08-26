package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec.`x-dicentra-aviator-serviceOptions`
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

object TranslationKeysPlugin : AviatorPlugin<TranslationKeysPluginConfig> {
    const val RESULT = "WV-TranslationKeys-Result"
    override val identity: String = "DC-WVxAV-TranslationKeys"
    override fun install(config: TranslationKeysPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = TranslationKeysPluginConfig()
        config.invoke(pluginConfig)
        return TKPInstance(pluginConfig, identity, PluginStagedExecutorBuilder.steps {
            if (pluginConfig.onPath) {
                before(AviatorExecutionStages.PathMatching) { context ->
                    val tk = extractKeys(context)
                    context.requestParams =
                        RequestParameters(context.requestParams.map { (key, value) -> (tk[key] ?: key) to value }
                            .toMap().toMutableMap())
                }
            }
            if (pluginConfig.onInput) {
                after(AviatorExecutionStages.FormingRequest) { context ->
                    val tk = extractKeys(context)
                    context.networkChain.forEach { net ->
                        net.request?.outgoingContent?.let {
                            net.request?.outgoingContent = SubstitutionKeysSerializer.translate(
                                tk,
                                it,
                                context.dataHolder.json
                            )
                        }
                    }
                }
            }
            if (pluginConfig.onOutput) {
                after(AviatorExecutionStages.PaintingResponse) { context ->
                    val tk = extractKeys(context)
                    context.networkChain.forEach { net ->
                        net.response?.content?.let {
                            net.response?.content = SubstitutionKeysSerializer.translateJson(
                                SubstitutionKeysSerializer.flipTK(tk),
                                it,
                                context.dataHolder.json
                            )
                        }
                    }
                    val successful =
                        context.networkChain.find { (it.response?.status?.value ?: 500) < 400 }
                    val res = successful?.response
                    res?.content?.let {
                        context.bundle.plus(RESULT to it)
                        if (!(pluginConfig.preserveDefaultOutput)) {
                            context.result = try {
                                res.parseBody(context.outputClazz.third, context.dataHolder.json)
                            } catch (e: SerializationException) {
                                null
                            } catch (e: IllegalArgumentException) {
                                null
                            }
                        }
                    }
                }
            }
        })
    }

    private fun <T : AviatorExecutionContext<out @Serializable Any, out @Serializable Any>> extractKeys(
        context: T
    ): Map<String, String> {
        val tk =
            context.dataHolder.route.`x-dicentra-aviator-serviceOptions`?.jsonObject?.get("translation-keys")
        val map = tk?.let {
            context.dataHolder.json.decodeFromJsonElement<Map<String, String>>(
                serializer(),
                it
            )
        }
        return map ?: emptyMap()
    }
}