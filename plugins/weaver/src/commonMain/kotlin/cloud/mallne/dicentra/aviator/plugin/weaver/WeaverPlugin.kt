@file:OptIn(ExperimentalJsExport::class)

package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder.Companion.json
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
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
                val schema = pluginConfig.schema ?: serviceOptions(context)?.schema
                if (schema == null) return@preExecution
                val current = getThisPlugin<WVPInstance>(context)
                current?.engine = pluginConfig.weaver.engine(schema)
            }

            before(AviatorExecutionStages.PaintingResponse) { context ->
                // The normal response adapter must see the mapped shape, not upstream JSON.
                if (!pluginConfig.preserveDefaultOutput) transformResponse(context)
            }
            after(AviatorExecutionStages.PaintingResponse) { context ->
                // Explicit preservation still returns the original deserialized response.
                if (pluginConfig.preserveDefaultOutput) transformResponse(context)
            }
        })
    }

    private fun transformResponse(context: AviatorExecutionContext<*, *>) {
        val engine = getThisPlugin<WVPInstance>(context)?.engine ?: return
        val response = context.networkChain.firstOrNull { (it.response?.status?.value ?: 500) < 400 }?.response ?: return
        val content = response.content ?: return
        val transformed = engine.executeElement(context.dataHolder.json.parseToJsonElement(content.decodeToString()))
        response.content = context.dataHolder.json.encodeToString(JsonElement.serializer(), transformed).encodeToByteArray()
        context.bundle[RESULT] = transformed
    }

    private fun serviceOptions(
        context: AviatorExecutionContext<*, *>,
    ): WeaverServiceObject? = try {
        val configuredJson = context.dataHolder.json
        val optionsJson = if (configuredJson.configuration.ignoreUnknownKeys) configuredJson else {
            Json(configuredJson) { ignoreUnknownKeys = true }
        }
        optionsJson.decodeFromJsonElement<WeaverServiceObject>(configuredJson.encodeToJsonElement(context.dataHolder.options))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
