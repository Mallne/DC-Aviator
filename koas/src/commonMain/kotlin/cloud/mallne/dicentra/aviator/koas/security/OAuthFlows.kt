package cloud.mallne.dicentra.aviator.koas.security

import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPIConstraintViolation
import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.polyfill.ensure
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@Serializable(OAuthFlows.Companion.Serializer::class)
@KeepGeneratedSerializer
data class OAuthFlows(
    val implicit: OAuthFlow? = null,
    val password: OAuthFlow? = null,
    val clientCredentials: OAuthFlow? = null,
    val authorizationCode: OAuthFlow? = null,
    override var extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {

    init {
        if (implicit != null) {
            ensure(implicit.authorizationUrl?.isNotBlank() == true) { OpenAPIConstraintViolation("authorizationUrl on implicit flows should not be blank") }
        }
        if (authorizationCode != null) {
            ensure(authorizationCode.authorizationUrl?.isNotBlank() == true) { OpenAPIConstraintViolation("authorizationUrl on authorizationCode flows should not be blank") }
            ensure(authorizationCode.tokenUrl?.isNotBlank() == true) { OpenAPIConstraintViolation("tokenUrl on authorizationCode flows should not be blank") }
        }
        if (password != null) {
            ensure(password.tokenUrl?.isNotBlank() == true) { OpenAPIConstraintViolation("tokenUrl on password flows should not be blank") }
        }
        if (clientCredentials != null) {
            ensure(clientCredentials.tokenUrl?.isNotBlank() == true) { OpenAPIConstraintViolation("tokenUrl on clientCredentials flows should not be blank") }
        }
    }

    companion object {
        internal object Serializer :
            KSerializerWithExtensions<OAuthFlows>(
                generatedSerializer(),
                OAuthFlows::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}

