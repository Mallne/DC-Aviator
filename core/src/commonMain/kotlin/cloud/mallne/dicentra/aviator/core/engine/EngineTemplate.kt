package cloud.mallne.dicentra.aviator.core.engine

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.core.plugins.EngineInvocationContext
import cloud.mallne.dicentra.aviator.core.plugins.EngineAuthorizationPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginRegistry

interface EngineTemplate<in I : Any, O : Any> {
    val identity: String

    val authorization: EngineAuthorization<I>
        get() = EngineAuthorization.allowAll()
    val authorizationPlugins: List<EngineAuthorizationPlugin<I>>
        get() = emptyList()

    /** Template execution remains statically typed from input through result. */
    suspend fun execute(
        definition: ExecutableDefinition,
        input: I,
        context: EngineInvocationContext,
    ): Result<O>

    /**
     * Classifies a completed execution for engine fallback. Workflow adapters can override this
     * classification to apply their own response criteria. Authorization, transport and plugin
     * failures returned or thrown by [execute] are never reclassified.
     */
    fun classifyFailure(output: O): Exception? = null
}

enum class InvocationMode { ALL, FIRST, FIRST_NOT_FAILING }

class InvocationOutcome<out O : Any>(
    val definition: ExecutableDefinition,
    val result: Result<O>,
)

interface AviatorEngine {
    companion object {
        /**
         * Creates Aviator's included engine. Normally share one instance per application.
         * Implementing this interface is an advanced extension point, not required for setup.
         */
        operator fun invoke(
            coordinator: DefinitionCoordinator,
            authorization: EngineDefinitionAuthorization = EngineDefinitionAuthorization.DenyAll,
            outboundTargetPolicy: OutboundTargetPolicy,
            plugins: AviatorPluginRegistry = AviatorPluginRegistry.Empty,
        ): AviatorEngine = BuiltInAviatorEngine(coordinator, authorization, outboundTargetPolicy, plugins)
    }

    /** [selectDefinition] is a hard targeting restriction, applied before plugins and authorization. */
    suspend fun <I : Any, O : Any> invoke(
        locator: ServiceLocator,
        template: EngineTemplate<I, O>,
        input: I,
        mode: InvocationMode,
        selectDefinition: (ExecutableDefinition) -> Boolean = { true },
    ): List<InvocationOutcome<O>>
}
