package cloud.mallne.dicentra.aviator.plugin.orchestration.authorization

import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPlugin
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginConfig
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginExecutor
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginInstance
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.orchestration.plugin.orchestrationPlugin

/** Optional authorization extension. Required host dispatch authorization remains separate and always runs first. */
class ConstituentAuthorizationPlugin(
    override val identity: String,
    private val authorize: (ConstituentIdentity) -> ExecutionFailure?,
) : OrchestrationPlugin {
    init { require(identity.isNotBlank()) }

    override fun install(config: OrchestrationPluginConfig.() -> Unit): OrchestrationPluginInstance {
        val configured = OrchestrationPluginConfig().apply(config)
        val configuredExecutor = configured.executor
        configured.stages += OrchestrationStage.BEFORE_ATTEMPT
        configured.executor = OrchestrationPluginExecutor { context ->
            context.constituent?.let(authorize)?.let(context::reject)
            if (context.rejection == null) configuredExecutor.execute(context)
        }
        return orchestrationPlugin(identity) {
            serviceFilter += configured.serviceFilter
            stages += configured.stages
            phases += configured.phases
            lifecycleScopes += configured.lifecycleScopes
            executor = configured.executor
            metadataProvider = configured.metadataProvider
        }
    }
}

fun constituentAuthorizationPlugin(
    identity: String,
    authorize: (ConstituentIdentity) -> ExecutionFailure?,
): OrchestrationPluginInstance = ConstituentAuthorizationPlugin(identity, authorize).install()
