package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.plugins.weaver.WVPInstance
import kotlinx.serialization.SerializationException

object WeaverPlugin : AviatorPlugin<WeaverPluginConfig> {
    const val RESULT = "WV-Result"
    override val identity: String = "DC-WVxAV"
    override fun install(config: WeaverPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = WeaverPluginConfig()
        config.invoke(pluginConfig)
        return WVPInstance(pluginConfig, identity, PluginStagedExecutorBuilder.steps {
            preExecution { context ->
                val schema = if (pluginConfig.schema != null) {
                    pluginConfig.schema!!
                } else {
                    val weaverServiceObject =
                        AviatorServiceUtils.optionBundle<WeaverServiceObject>(context.dataHolder.options)
                    weaverServiceObject.schema
                }
                val current = getThisPlugin<WVPInstance>(context)
                current?.engine = pluginConfig.weaver.engine(schema)
            }

            after(AviatorExecutionStages.PaintingResponse) { context ->
                val current = getThisPlugin<WVPInstance>(context)
                val engine = current?.engine
                if (engine != null) {
                    context.networkChain.forEach { net ->
                        net.response?.content?.let {
                            net.response?.content = engine.execute(it)
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
}