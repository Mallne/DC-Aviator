package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Serializable
open class BasicExecutionContext<O : @Serializable Any, B : @Serializable Any>(
    override var stage: AviatorExecutionStages,
    override val dataHolder: AviatorServiceDataHolder,
    override val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
    override val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>?,
    @Contextual
    override val bundle: MutableMap<String, out @Serializable Any>,
    override var result: O?,
    override val body: B?,
    override val networkChain: List<NetworkChain<*, *>>,
    override val options: RequestOptions,
    override val requestParams: Map<String, List<String>>

) : AviatorExecutionContext<O, B>