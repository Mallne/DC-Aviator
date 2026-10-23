@file:OptIn(ExperimentalJsExport::class)

package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder.Companion.json
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
object WeaverPlugin : AviatorPlugin<WeaverPluginConfig> {
    const val RESULT = "WV-Result"
    override val identity: String = "DC-WVxAV"
    override fun install(config: WeaverPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = WeaverPluginConfig()
        config.invoke(pluginConfig)
        return WVPInstance(pluginConfig, identity, PluginStagedExecutorBuilder.steps {
            before(AviatorExecutionStages.PathMatching) { context ->
                val serviceOptions = serviceOptions(context) ?: return@before
                serviceOptions.adaptRequestParameters(context.requestParams)
            }

            preExecution { context ->
                val schema = pluginConfig.schema ?: serviceSchema(context)
                if (schema == null) return@preExecution
                val current = getThisPlugin<WVPInstance>(context)
                current?.engine = pluginConfig.weaver.engine(schema)
            }

            after(AviatorExecutionStages.PaintingResponse) { context ->
                val current = getThisPlugin<WVPInstance>(context)
                val engine = current?.engine
                val response = context.networkChain
                    .firstOrNull { (it.response?.status?.value ?: 500) < 400 }
                    ?.response ?: return@after
                val content = response.content ?: return@after
                if (engine == null) return@after

                val input = context.dataHolder.json.parseToJsonElement(content.decodeToString())
                val transformed = engine.executeElement(input)
                val transformedBytes = context.dataHolder.json.encodeToString(JsonElement.serializer(), transformed).encodeToByteArray()
                response.content = transformedBytes
                context.bundle.plus(RESULT to transformedBytes)
                if (!pluginConfig.preserveDefaultOutput) {
                    context.result = try {
                        context.deserializerFor(response.contentType)?.deserialize(transformedBytes, context)
                    } catch (_: SerializationException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }
            }
        })
    }

    private fun serviceSchema(
        context: cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext<*, *>,
    ): WeaverSchema? = try {
        serviceOptions(context)?.schema
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun serviceOptions(
        context: cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext<*, *>,
    ): WeaverServiceObject? = try {
        AviatorServiceUtils.optionBundle<WeaverServiceObject>(
            context.dataHolder.options,
            json = Json(context.dataHolder.json) { ignoreUnknownKeys = true },
        )
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
