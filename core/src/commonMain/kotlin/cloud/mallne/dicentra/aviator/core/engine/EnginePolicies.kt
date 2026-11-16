package cloud.mallne.dicentra.aviator.core.engine

import cloud.mallne.dicentra.aviator.model.ServiceLocator
data class EngineAuthorizationContext<out I : Any>(
    val locator: ServiceLocator,
    val definition: ExecutableDefinition,
    val input: I,
)

sealed interface EngineAuthorizationDecision {
    data object Allowed : EngineAuthorizationDecision
    data class Denied(val reason: String) : EngineAuthorizationDecision
}

/** Typed policy attached to a template; it can inspect that template's actual input. */
fun interface EngineAuthorization<in I : Any> {
    suspend fun authorize(context: EngineAuthorizationContext<I>): EngineAuthorizationDecision

    companion object {
        fun <I : Any> allowAll(): EngineAuthorization<I> =
            EngineAuthorization { EngineAuthorizationDecision.Allowed }

        fun <I : Any> denyAll(reason: String = "No request authorization policy is configured") =
            EngineAuthorization<I> { EngineAuthorizationDecision.Denied(reason) }
    }
}

/** Host policy shared across templates; it can make locator/definition-level decisions. */
data class EngineDefinitionAuthorizationContext(
    val locator: ServiceLocator,
    val definition: ExecutableDefinition,
)

fun interface EngineDefinitionAuthorization {
    suspend fun authorize(context: EngineDefinitionAuthorizationContext): EngineAuthorizationDecision

    companion object {
        /** Safe default until the host supplies an authorization policy. */
        val DenyAll = EngineDefinitionAuthorization {
            EngineAuthorizationDecision.Denied("No engine authorization policy is configured")
        }

        /** Explicit opt-in for hosts where authorization is handled elsewhere. */
        val AllowAll = EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed }
    }
}

/** Mandatory host policy for outbound targets. Targets are the definition's raw OpenAPI servers. */
fun interface OutboundTargetPolicy {
    suspend fun authorize(
        locator: ServiceLocator,
        definition: ExecutableDefinition,
        targets: List<String>,
    ): String?
}

class EngineAuthorizationException(message: String) : Exception(message)

class OutboundTargetDeniedException(message: String) : Exception(message)
