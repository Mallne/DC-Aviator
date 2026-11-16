package cloud.mallne.dicentra.aviator.core.engine

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginRegistry
import cloud.mallne.dicentra.aviator.core.plugins.EngineInvocationContext
import cloud.mallne.dicentra.aviator.core.plugins.DelegationInvocationContext
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlin.coroutines.cancellation.CancellationException

/**
 * Dispatches matching definitions in coordinator order. Empty resolutions fail; failures from
 * individual delegations are retained, and cancellation always propagates.
 */
internal class BuiltInAviatorEngine(
    private val coordinator: DefinitionCoordinator,
    private val authorization: EngineDefinitionAuthorization = EngineDefinitionAuthorization.DenyAll,
    private val outboundTargetPolicy: OutboundTargetPolicy,
    private val plugins: AviatorPluginRegistry = AviatorPluginRegistry.Empty,
) : AviatorEngine {
    override suspend fun <I : Any, O : Any> invoke(
        locator: ServiceLocator,
        template: EngineTemplate<I, O>,
        input: I,
        mode: InvocationMode,
        selectDefinition: (ExecutableDefinition) -> Boolean,
    ): List<InvocationOutcome<O>> {
        require(template.identity.isNotBlank()) { "Engine template identity must not be blank" }
        val resolved = coordinator.resolve(locator).toList()
        require(resolved.all { it.locator == locator }) { "Coordinator returned a different locator" }
        val outcomes = mutableListOf<InvocationOutcome<O>>()
        for (definition in resolved) {
            if (definition.delegation != template.identity) continue
            if (!selectDefinition(definition)) continue
            val result = try {
                val pluginSnapshot = plugins.invocationPlugins(locator, definition.pluginConfiguration)
                if (pluginSnapshot.enginePlugins.any { !it.accepts(locator, definition) }) continue
                val targetDenial = outboundTargetPolicy.authorize(locator, definition, definition.outboundTargets.toList())
                val definitionContext = EngineDefinitionAuthorizationContext(locator, definition)
                val definitionDecision = if (targetDenial == null) {
                    authorization.authorize(definitionContext)
                } else {
                    EngineAuthorizationDecision.Allowed
                }
                val authorizationContext = EngineAuthorizationContext(locator, definition, input)
                val templateDecision = if (
                    targetDenial == null && definitionDecision == EngineAuthorizationDecision.Allowed
                ) {
                    template.authorization.authorize(authorizationContext)
                } else {
                    EngineAuthorizationDecision.Allowed
                }
                val pluginDenial = if (
                    targetDenial == null &&
                    definitionDecision == EngineAuthorizationDecision.Allowed &&
                    templateDecision == EngineAuthorizationDecision.Allowed
                ) {
                    template.authorizationPlugins.firstNotNullOfOrNull { plugin ->
                        plugin.authorize(authorizationContext)
                            .takeIf { it is EngineAuthorizationDecision.Denied }
                    }
                } else {
                    null
                }
                val denial = when {
                    targetDenial != null -> OutboundTargetDeniedException(targetDenial)
                    definitionDecision is EngineAuthorizationDecision.Denied ->
                        EngineAuthorizationException(definitionDecision.reason)
                    templateDecision is EngineAuthorizationDecision.Denied ->
                        EngineAuthorizationException(templateDecision.reason)
                    pluginDenial is EngineAuthorizationDecision.Denied ->
                        EngineAuthorizationException(pluginDenial.reason)
                    else -> null
                }
                if (denial != null) {
                    Result.failure(denial)
                } else {
                    val invocationContext = EngineInvocationContext(
                        pluginSnapshot.clientPlugins,
                        pluginSnapshot.scopes,
                    )
                    val delegationContext = DelegationInvocationContext(locator, definition, input)
                    var invoke: suspend () -> Result<O> = {
                        template.execute(definition, input, invocationContext)
                    }
                    for (plugin in pluginSnapshot.delegationPlugins.asReversed()) {
                        val proceed = invoke
                        invoke = { plugin.execute(delegationContext, proceed) }
                    }
                    val execution = invoke()
                    execution.getOrNull()?.let { value ->
                        val failure = template.classifyFailure(value)
                        if (failure == null) execution else Result.failure(failure)
                    } ?: execution
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            outcomes += InvocationOutcome(definition, result)
            if (mode == InvocationMode.FIRST || mode == InvocationMode.FIRST_NOT_FAILING && result.isSuccess) break
        }
        require(outcomes.isNotEmpty()) {
            "No definition for locator $locator matched engine template ${template.identity}"
        }
        return outcomes
    }
}
