package cloud.mallne.dicentra.aviator.core.io

import kotlinx.serialization.Serializable

/** An explicitly serialized request. The caller is responsible for validating its media type. */
@Serializable
data class RawRequestBody(val bytes: ByteArray, val contentType: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RawRequestBody

        if (!bytes.contentEquals(other.bytes)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}
