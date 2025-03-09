package cloud.mallne.dicentra.aviator.ktor

import cloud.mallne.dicentra.aviator.core.MutableRequestOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.execution.AviatorLogger
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.ktor.io.AvKtorRequest
import cloud.mallne.dicentra.aviator.ktor.io.AvKtorResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

data class KtorExecutionContext<O : @Serializable Any, B : @Serializable Any>(
    override var stage: AviatorExecutionStages = AviatorExecutionStages.Unstarted,
    override val dataHolder: KtorAviatorService,
    override val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
    override val bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>? = null,
    override val bundle: MutableMap<String, JsonElement> = mutableMapOf(),
    override var result: O? = null,
    override var body: B? = null,
    override val options: MutableRequestOptions = mutableMapOf(),
    override val networkChain: MutableList<NetworkChain<AvKtorRequest, AvKtorResponse, NetworkHeader>> = mutableListOf(),
    override var requestParams: Map<String, List<String>> = mutableMapOf(),
    override var logger: AviatorLogger? = null,
) : AviatorExecutionContext<O, B>