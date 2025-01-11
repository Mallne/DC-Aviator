package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.io.NetworkRequest
import cloud.mallne.dicentra.aviator.koas.typed.Serializers
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MockedRequest(
    override val headers: MockedHeader = MockedHeader(),
    override val method: @Serializable(Serializers.HttpMethodSerializer::class) HttpMethod,
    override val url: @Serializable(Serializers.UrlSerializer::class) Url,
    override val outgoingContent: JsonElement?
) : NetworkRequest