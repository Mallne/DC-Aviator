package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder.Companion.json
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.io.RawRequestBody
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.io.encoding.Base64
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object SynapsePlugin : AviatorPlugin<SynapsePluginConfig> {
    override val identity: String = "DC-AVSyn"
    override fun install(config: SynapsePluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = SynapsePluginConfig()
        config.invoke(pluginConfig)
        return SynapsePluginInstance(pluginConfig, identity, PluginStagedExecutorBuilder.steps {
            before(AviatorExecutionStages.FormingRequest) { context ->
                val current = getThisPlugin<SynapsePluginInstance>(context)
                val active = current?.configurationBundle?.active ?: false
                if (!active) return@before
                val raw = context.body as? RawRequestBody
                val body = if (raw == null && context.body != null && context.bodyClazz != null) {
                    context.dataHolder.json.encodeToJsonElement(
                        context.bodyClazz!!.third,
                        context.body!!
                    )
                } else {
                    null
                }
                context.body = CatalystRequest(
                    body = raw?.let { JsonPrimitive(Base64.encode(it.bytes)) } ?: body,
                    bodyFormat = raw?.let { CatalystBodyFormat.Base64(it.contentType) } ?: CatalystBodyFormat.Json,
                    typedParameters = context.requestParams.toCatalystParameters(context.dataHolder.json),
                )
                context.bodyClazz = Triple(
                    CatalystRequest::class,
                    typeOf<CatalystRequest>(),
                    serializer<CatalystRequest>()
                ) as Triple<KClass<@Serializable Any>, KType, KSerializer<@Serializable Any>>
            }
        })
    }
}
