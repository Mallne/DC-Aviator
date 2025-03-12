package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.MutableRequestOptions
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.NetworkRequest
import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface AviatorExecutionContext<O : @Serializable Any, B : @Serializable Any> {
    var stage: AviatorExecutionStages
    val dataHolder: AviatorServiceDataHolder
    val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>
    val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>?
    val bundle: MutableMap<String, JsonElement>
    var result: O?
    var body: B?
    val networkChain: MutableList<out NetworkChain<out NetworkRequest<out NetworkHeader>, out NetworkResponse<out NetworkHeader>, out NetworkHeader>>
    val options: MutableRequestOptions
    var requestParams: Map<String, List<String>>

    @InternalAviatorAPI
    var logger: AviatorLogger?

    @OptIn(InternalAviatorAPI::class, ExperimentalUuidApi::class)
    fun log(id: String = Uuid.random().toString(), loggerScope: AviatorLogger.() -> Unit) {
        val loggingSuppressions = getAllLoggingSuppressions()
        if (!loggingSuppressions.contains(id)) {
            logger?.loggerScope()
        }
    }

    private fun getAllLoggingSuppressions(): Set<String> {
        val set = mutableSetOf<String>()
        for (plugin in dataHolder.plugins) {
            set.addAll(plugin.configurationBundle.silentLoggingTags)
        }
        return set
    }
}
