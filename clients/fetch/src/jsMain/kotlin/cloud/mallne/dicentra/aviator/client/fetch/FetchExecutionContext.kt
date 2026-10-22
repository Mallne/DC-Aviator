package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.MutableRequestOptions
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import cloud.mallne.dicentra.aviator.core.io.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

data class FetchExecutionContext<O : @Serializable Any, B : @Serializable Any> @OptIn(InternalAviatorAPI::class) constructor(
    override var stage: AviatorExecutionStages = AviatorExecutionStages.Unstarted,
    override val dataHolder: FetchAviatorService,
    override val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>,
    override var bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>? = null,
    override val bundle: MutableMap<String, JsonElement> = mutableMapOf(),
    override var result: O? = null,
    override var body: B? = null,
    override val options: MutableRequestOptions = mutableMapOf(),
    override val networkChain: MutableList<NetworkChain<FetchRequest, FetchResponse, FetchHeaders>> = mutableListOf(),
    override var requestParams: RequestParameters = RequestParameters(),
    @property:InternalAviatorAPI override var logger: AviatorLogger? = null,
) : AviatorExecutionContext<O, B>
