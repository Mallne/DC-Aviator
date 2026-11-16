package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.serialization.json.JsonElement

/** Install-time plugin definition; installation bakes configuration into an immutable instance. */
interface DelegationAviatorPlugin<ConfigScope : AviatorPluginConfigScope> {
    val identity: String
    fun install(config: ConfigScope.() -> Unit = {}): DelegationAviatorPluginInstance
}

/** Middleware that can change how one selected definition is delegated to its template. */
interface DelegationAviatorPluginInstance {
    val identity: String
    val configurationBundle: AviatorPluginConfigScope
    fun requestReconfigure(oasConfig: JsonElement): DelegationAviatorPluginInstance = this

    suspend fun <I : Any, O : Any> execute(
        context: DelegationInvocationContext<I>,
        proceed: suspend () -> Result<O>,
    ): Result<O>
}

data class DelegationInvocationContext<out I : Any>(
    val locator: ServiceLocator,
    val definition: ExecutableDefinition,
    val input: I,
)
