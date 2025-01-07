package cloud.mallne.dicentra.aviator.ktor

import cloud.mallne.dicentra.aviator.core.RequestOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.ktor.io.AvKtorRequest
import cloud.mallne.dicentra.aviator.ktor.io.AvKtorResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KType

data class KtorExecutionContext<O : @Serializable Any, B : @Serializable Any>(
    override var stage: AviatorExecutionStages = AviatorExecutionStages.Unstarted,
    override val dataHolder: KtorAviatorService,
    override val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
    override val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>? = null,
    override val bundle: MutableMap<String, @Serializable Any> = mutableMapOf(),
    override var result: O? = null,
    override var body: B? = null,
    override val options: RequestOptions = mutableMapOf<String, @Serializable Any>(),
    override val networkChain: MutableList<NetworkChain<AvKtorRequest, AvKtorResponse>> = mutableListOf(),
    override val requestParams: Map<String, List<String>> = mutableMapOf(),
) : AviatorExecutionContext<O, B>