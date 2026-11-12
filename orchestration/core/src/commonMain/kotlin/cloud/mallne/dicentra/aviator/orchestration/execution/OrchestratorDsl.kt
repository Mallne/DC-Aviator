package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginInstallationScope
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.orchestration.plugin.installOrchestrationPlugins
import io.ktor.openapi.OpenApiDoc

/** Prevent accidental access to an outer orchestration DSL receiver from nested blocks. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class OrchestrationDsl

/** Materialized services and the shared orchestration plugin snapshot passed to an engine factory. */
class OrchestratorEnvironment internal constructor(
    val services: List<AviatorServiceDataHolder>,
    val plugins: OrchestrationPluginRegistry,
)

/** Assemble a format-specific engine around Aviator's existing converter and plugin DSL. */
fun orchestrator(configure: OrchestratorBuilder.() -> Unit): Orchestrator =
    OrchestratorBuilder().apply(configure).build()

@OrchestrationDsl
class OrchestratorBuilder internal constructor() {
    private val services = mutableListOf<AviatorServiceDataHolder>()
    private val pluginBlocks = mutableListOf<OrchestrationPluginInstallationScope.() -> Unit>()
    private var engineFactory: ((OrchestratorEnvironment) -> Orchestrator)? = null

    var logger: AviatorLogger? = null

    /** Calls the original Aviator converter with its original plugin activation scope. */
    fun aviator(
        converter: APIToServiceConverter,
        api: OpenApiDoc,
        plugins: (@OrchestrationDsl AviatorPluginActivationScope).() -> Unit = {},
    ) {
        services += converter.build(api, plugins)
    }

    /** Use already materialized services, including services created outside this builder. */
    fun aviator(services: Iterable<AviatorServiceDataHolder>) {
        this.services.addAll(services)
    }

    fun plugins(configure: OrchestrationPluginInstallationScope.() -> Unit) {
        pluginBlocks += configure
    }

    /** Install exactly one format-specific engine; its factory receives the same registry for all layers. */
    fun engine(factory: (OrchestratorEnvironment) -> Orchestrator) {
        check(engineFactory == null) { "An orchestration engine is already configured" }
        engineFactory = factory
    }

    internal fun build(): Orchestrator {
        val factory = checkNotNull(engineFactory) { "An orchestration engine must be configured" }
        val registry = installOrchestrationPlugins(logger) { this@OrchestratorBuilder.pluginBlocks.forEach { it(this) } }
        return factory(OrchestratorEnvironment(services.toList(), registry))
    }
}
