package cloud.mallne.dicentra.aviator.koas.security

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@Serializable(SecurityScheme.Companion.Serializer::class)
@KeepGeneratedSerializer
data class SecurityScheme(
    val type: Type,
    val description: String? = null,
    val name: String? = null,
    @SerialName("in") val inside: In? = null,
    val scheme: String? = null,
    val bearerFormat: String? = null,
    val flows: OAuthFlows? = null,
    val openIdConnectUrl: String? = null,
    override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {

    companion object {
        internal object Serializer :
            KSerializerWithExtensions<SecurityScheme>(
                generatedSerializer(),
                SecurityScheme::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }

    init {
        if (type == Type.APIKEY) {
            require(name != null && inside != null) { "name and in is required" }
        }
        if (type == Type.HTTP) {
            require(scheme != null) { "scheme is required" }
        }
        if (type == Type.OAUTH2) {
            require(flows != null) { "flows are required" }
        }
        if (type == Type.OPENIDCONNECT) {
            require(openIdConnectUrl != null) { "openIdConnectUrl is required" }
        }
    }

    /**
     * Gets or Sets type
     */
    enum class Type(val value: String) {
        @SerialName("apiKey")
        APIKEY("apiKey"),

        @SerialName("http")
        HTTP("http"),

        @SerialName("oauth2")
        OAUTH2("oauth2"),

        @SerialName("openIdConnect")
        OPENIDCONNECT("openIdConnect"),

        @SerialName("mutualTLS")
        MUTUALTLS("mutualTLS");

        override fun toString(): String {
            return value.toString()
        }
    }

    /**
     * Gets or Sets in
     */
    enum class In(val value: String) {
        @SerialName("cookie")
        COOKIE("cookie"),

        @SerialName("header")
        HEADER("header"),

        @SerialName("query")
        QUERY("query");

        override fun toString(): String {
            return value.toString()
        }
    }
}

