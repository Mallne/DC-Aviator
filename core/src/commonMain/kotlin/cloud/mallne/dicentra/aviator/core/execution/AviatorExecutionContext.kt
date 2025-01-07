package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface AviatorExecutionContext<O : @Serializable Any, B : @Serializable Any> {
    var stage: AviatorExecutionStages
    val dataHolder: AviatorServiceDataHolder
    val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>
    val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>?
    val bundle: MutableMap<String, out @Serializable Any>
    var result: O?
    val body: B?
    val networkChain: List<NetworkChain<*, *>>
    val options: RequestOptions
    val requestParams: Map<String, List<String>>
}
