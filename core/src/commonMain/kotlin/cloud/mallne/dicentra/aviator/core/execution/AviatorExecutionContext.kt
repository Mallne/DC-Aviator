package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.MutableRequestOptions
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.core.io.NetworkRequest
import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface AviatorExecutionContext<O : @Serializable Any, B : @Serializable Any> {
    var stage: AviatorExecutionStages
    val dataHolder: AviatorServiceDataHolder
    val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>
    val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>?
    val bundle: MutableMap<String, JsonElement>
    var result: O?
    val body: B?
    val networkChain: List<NetworkChain<out NetworkRequest, out NetworkResponse>>
    val options: MutableRequestOptions
    val requestParams: Map<String, List<String>>
}
