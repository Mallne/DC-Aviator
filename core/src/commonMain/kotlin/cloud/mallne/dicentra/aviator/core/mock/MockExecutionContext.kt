package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.MutableRequestOptions
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Serializable
data class MockExecutionContext(
    override var stage: AviatorExecutionStages = AviatorExecutionStages.Unstarted,
    override val dataHolder: MockedAviatorService,
    override val outputClazz: Triple<KClass<JsonElement>, KType, KSerializer<JsonElement>> = AviatorServiceUtils.makeClazzDefinition(),
    override val bodyClazz: Triple<KClass<NoBody>, KType, KSerializer<NoBody>>? = null,
    override val bundle: MutableMap<String, JsonElement> = mutableMapOf(),
    override var result: JsonElement? = null,
    override val body: NoBody? = null,
    override val networkChain: MutableList<NetworkChain<MockedRequest, MockedResponse>> = mutableListOf(),
    override val options: MutableRequestOptions = mutableMapOf(),
    override val requestParams: Map<String, List<String>> = mutableMapOf(),
) : AviatorExecutionContext<JsonElement, NoBody>