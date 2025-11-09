package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import cloud.mallne.dicentra.aviator.koas.typed.Serializers
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MockedResponse(
    override var headers: MockedHeader = MockedHeader(),
    override var status: @Serializable(Serializers.HttpStatusCodeSerializer::class) HttpStatusCode,
    override var content: JsonElement?,
    override var time: GMTDate
) : NetworkResponse<MockedHeader>