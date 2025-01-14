package cloud.mallne.dicentra.aviator.koas.security


import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@Serializable(OAuthFlow.Companion.Serializer::class)
@KeepGeneratedSerializer
data class OAuthFlow(
    val authorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val refreshUrl: String? = null,
    @Required val scopes: Map<String, String> = emptyMap(),
    override var extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<OAuthFlow>(
                generatedSerializer(),
                OAuthFlow::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}

