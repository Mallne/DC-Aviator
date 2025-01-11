package cloud.mallne.dicentra.aviator.koas.exceptions

import kotlinx.serialization.SerializationException

open class OpenAPISerializationException(override val message: String?) : SerializationException()