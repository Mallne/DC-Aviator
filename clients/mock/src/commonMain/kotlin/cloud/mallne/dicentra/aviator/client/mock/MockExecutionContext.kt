package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.MutableRequestOptions
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Serializable
data class MockExecutionContext @OptIn(InternalAviatorAPI::class) constructor(
    override var stage: AviatorExecutionStages = AviatorExecutionStages.Unstarted,
    override val dataHolder: MockedAviatorService,
    override val outputClazz: Triple<KClass<JsonElement>, KType, KSerializer<JsonElement>> = AviatorServiceUtils.makeClazzDefinition(),
    override var bodyClazz: Triple<KClass<NoBody>, KType, KSerializer<NoBody>>? = null,
    override val bundle: MutableMap<String, JsonElement> = mutableMapOf(),
    override var result: JsonElement? = null,
    override var body: NoBody? = null,
    override val networkChain: MutableList<NetworkChain<MockedRequest, MockedResponse, MockedHeader>> = mutableListOf(),
    override val options: MutableRequestOptions = mutableMapOf(),
    override var requestParams: RequestParameters = RequestParameters(),
    @property:InternalAviatorAPI
    override var logger: AviatorLogger? = null
) : AviatorExecutionContext<JsonElement, NoBody>