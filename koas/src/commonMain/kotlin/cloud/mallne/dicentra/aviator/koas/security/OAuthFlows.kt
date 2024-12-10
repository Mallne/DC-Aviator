package cloud.mallne.dicentra.aviator.koas.security

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
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
            require(implicit.authorizationUrl?.isNotBlank() == true) { "authorizationUrl on implicit flows should not be blank" }
        }
        if (authorizationCode != null) {
            require(authorizationCode.authorizationUrl?.isNotBlank() == true) { "authorizationUrl on authorizationCode flows should not be blank" }
            require(authorizationCode.tokenUrl?.isNotBlank() == true) { "tokenUrl on authorizationCode flows should not be blank" }
        }
        if (password != null) {
            require(password.tokenUrl?.isNotBlank() == true) { "tokenUrl on password flows should not be blank" }
        }
        if (clientCredentials != null) {
            require(clientCredentials.tokenUrl?.isNotBlank() == true) { "tokenUrl on clientCredentials flows should not be blank" }
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

