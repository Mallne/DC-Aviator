package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import io.ktor.openapi.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeaverServiceObject(
    @SerialName("x-dicentra-weaver-schema")
    val schema: WeaverSchema? = null,
    /** Maps provider-neutral request keys to the operation's OpenAPI parameter names. */
    val requestParameterNames: Map<String, String> = emptyMap(),
    /** Defaults added only when the caller did not supply the target parameter. */
    val requestDefaults: Map<String, String> = emptyMap(),
    /** Provider-neutral request keys unsupported by this operation. */
    val ignoredRequestParameters: Set<String> = emptySet(),
) : InflatedServiceOptions {
    fun adaptRequestParameters(parameters: RequestParameters) {
        ignoredRequestParameters.forEach(parameters::remove)
        requestParameterNames.forEach { (commonName, providerName) ->
            parameters.remove(commonName)?.let { value -> parameters[providerName] = value }
        }
        requestDefaults.forEach { (name, value) ->
            if (name !in parameters) parameters[name] = RequestParameter.Single(value)
        }
    }

    override fun usable(): ServiceOptions {
        return GenericElementWrapper(this, serializer())
    }
}
