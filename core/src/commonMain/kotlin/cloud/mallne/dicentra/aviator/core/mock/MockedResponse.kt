package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import cloud.mallne.dicentra.aviator.koas.typed.Serializers
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MockedResponse(
    override val headers: MockedHeader = MockedHeader(),
    override val status: @Serializable(Serializers.HttpStatusCodeSerializer::class) HttpStatusCode,
    override val content: JsonElement?,
    override val time: GMTDate
) : NetworkResponse