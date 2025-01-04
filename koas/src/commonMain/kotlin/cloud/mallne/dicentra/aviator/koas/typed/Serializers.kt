package cloud.mallne.dicentra.aviator.koas.typed

import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object Serializers {
    object HttpStatusCodeSerializer : KSerializer<HttpStatusCode> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
            "cloud.mallne.dicentra.aviator.koas.typed.Serializers.HttpStatusCodeSerializer",
            PrimitiveKind.INT
        )

        override fun deserialize(decoder: Decoder): HttpStatusCode {
            return HttpStatusCode.fromValue(decoder.decodeInt())
        }

        override fun serialize(encoder: Encoder, value: HttpStatusCode) {
            return encoder.encodeInt(value.value)
        }
    }

    object HttpMethodSerializer : KSerializer<HttpMethod> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
            "cloud.mallne.dicentra.aviator.koas.typed.Serializers.HttpStatusCodeSerializer",
            PrimitiveKind.STRING
        )

        override fun deserialize(decoder: Decoder): HttpMethod {
            return HttpMethod.parse(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: HttpMethod) {
            return encoder.encodeString(value.value)
        }
    }

    object ContentTypeSerializer : KSerializer<ContentType> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
            "cloud.mallne.dicentra.aviator.koas.typed.Serializers.ContentTypeSerializer",
            PrimitiveKind.STRING
        )

        override fun deserialize(decoder: Decoder): ContentType {
            return ContentType.parse(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: ContentType) {
            return encoder.encodeString("${value.contentType}/${value.contentSubtype}")
        }
    }
}