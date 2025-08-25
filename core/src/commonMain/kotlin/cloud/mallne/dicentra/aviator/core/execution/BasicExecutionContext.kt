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

data class BasicExecutionContext<O : @Serializable Any, B : @Serializable Any> @OptIn(
    InternalAviatorAPI::class
) constructor(
    override var stage: AviatorExecutionStages = AviatorExecutionStages.Unstarted,
    override val dataHolder: AviatorServiceDataHolder,
    override val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
    override var bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>? = null,
    override val bundle: MutableMap<String, JsonElement> = mutableMapOf(),
    override var result: O? = null,
    override var body: B? = null,
    override val networkChain: MutableList<NetworkChain<NetworkRequest<NetworkHeader>, NetworkResponse<NetworkHeader>, NetworkHeader>> = mutableListOf(),
    override val options: MutableRequestOptions = mutableMapOf(),
    override var requestParams: RequestParameters = RequestParameters(),
    @property:InternalAviatorAPI
    override var logger: AviatorLogger? = null
) : AviatorExecutionContext<O, B>