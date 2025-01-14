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

data class BasicExecutionContext<O : @Serializable Any, B : @Serializable Any>(
    override var stage: AviatorExecutionStages = AviatorExecutionStages.Unstarted,
    override val dataHolder: AviatorServiceDataHolder,
    override val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
    override val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>? = null,
    override val bundle: MutableMap<String, JsonElement> = mutableMapOf(),
    override var result: O? = null,
    override var body: B? = null,
    override val networkChain: MutableList<NetworkChain<NetworkRequest, NetworkResponse>> = mutableListOf(),
    override val options: MutableRequestOptions = mutableMapOf(),
    override var requestParams: Map<String, List<String>> = mutableMapOf(),
) : AviatorExecutionContext<O, B>