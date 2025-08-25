package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.NetworkRequest
import cloud.mallne.dicentra.aviator.koas.typed.Serializers
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MockedRequest(
    override var headers: MockedHeader = MockedHeader(),
    override var method: @Serializable(Serializers.HttpMethodSerializer::class) HttpMethod,
    override var url: @Serializable(Serializers.UrlSerializer::class) Url,
    override var outgoingContent: NetworkBody = NetworkBody.Empty
) : NetworkRequest<MockedHeader>