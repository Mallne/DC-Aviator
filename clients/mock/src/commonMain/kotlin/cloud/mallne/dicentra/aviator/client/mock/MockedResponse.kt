package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import cloud.mallne.dicentra.aviator.koas.typed.Serializers
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.Serializable

@Serializable
data class MockedResponse(
    override var headers: MockedHeader = MockedHeader(),
    override var status: @Serializable(Serializers.HttpStatusCodeSerializer::class) HttpStatusCode,
    override var content: ByteArray?,
    override var time: GMTDate,
    override var contentType: @Serializable(Serializers.ContentTypeSerializer::class) ContentType
) : NetworkResponse<MockedHeader> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MockedResponse

        if (headers != other.headers) return false
        if (status != other.status) return false
        if (!content.contentEquals(other.content)) return false
        if (time != other.time) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = headers.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (content?.contentHashCode() ?: 0)
        result = 31 * result + time.hashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}