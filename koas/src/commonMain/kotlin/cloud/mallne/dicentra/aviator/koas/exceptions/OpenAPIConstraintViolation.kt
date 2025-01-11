package cloud.mallne.dicentra.aviator.koas.exceptions

open class OpenAPIConstraintViolation(override val message: String?) : IllegalArgumentException()