package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.engine.EngineAuthorizationContext
import cloud.mallne.dicentra.aviator.core.engine.EngineAuthorizationDecision
import cloud.mallne.dicentra.aviator.core.engine.OpenApiClientInput
import kotlinx.serialization.Serializable

/** Authorization plugins add vetoes; they cannot override a host denial. */
fun interface EngineAuthorizationPlugin<in I : Any> {
    suspend fun authorize(context: EngineAuthorizationContext<I>): EngineAuthorizationDecision

    companion object {
        fun <B : @Serializable Any> requireRequestParameter(
            name: String,
        ): EngineAuthorizationPlugin<OpenApiClientInput<B>> =
            EngineAuthorizationPlugin { context ->
                if (name in context.input.parameters) {
                    EngineAuthorizationDecision.Allowed
                } else {
                    EngineAuthorizationDecision.Denied("Required request parameter '$name' is missing")
                }
            }
    }
}
