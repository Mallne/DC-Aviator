package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import cloud.mallne.dicentra.aviator.core.engine.EnginePluginContext
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import cloud.mallne.dicentra.aviator.core.plugins.PluginScopeKey
import cloud.mallne.dicentra.aviator.core.plugins.PluginScopeSnapshot
import cloud.mallne.dicentra.aviator.core.plugins.TypedPluginScopeProvider
import cloud.mallne.dicentra.aviator.core.plugins.UniformAviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.DelegationInvocationContext
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationPolicyPlugin
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationHookPhase
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLogMetadataProvider
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationMetadataProvider
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginConfig
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginConfigurationBundle
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginExecutor
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginInstance
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStageContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData

@OptIn(ExperimentalApi::class)
data class OpenTelemetryPluginInstance(
    override val configurationBundle: OpenTelemetryPluginConfig,
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any>,
    override val orchestrationConfigurationBundle: OrchestrationPluginConfigurationBundle =
        orchestrationConfiguration(configurationBundle),
    override val executor: OrchestrationPluginExecutor =
        openTelemetryOrchestrationExecutor(configurationBundle),
    override val metadataProvider: OrchestrationMetadataProvider = OrchestrationMetadataProvider { emptyMap() },
    override val orchestrationLogMetadataProvider: OrchestrationLogMetadataProvider =
        OrchestrationLogMetadataProvider { _, _ -> emptyMap() },
    override val invocationPolicy: InvocationPolicyPlugin? = null,
) : UniformAviatorPluginInstance, OrchestrationPluginInstance {
    override val identity: String = OpenTelemetryPlugin.identity
    override val key = PluginScopeKey<OpenTelemetryPluginConfig>(identity)
    override val serviceFilter: Set<ServiceLocator> get() = orchestrationConfigurationBundle.serviceFilter
    override val stages: Set<OrchestrationStage> get() = orchestrationConfigurationBundle.stages
    override val phases: Set<OrchestrationHookPhase> get() = orchestrationConfigurationBundle.phases
    override val lifecycleScopes: Set<OrchestrationLifecycleScope>
        get() = orchestrationConfigurationBundle.lifecycleScopes
    override val silentLoggingTags: Set<String> get() = orchestrationConfigurationBundle.silentLoggingTags
    override val logMetadataProvider: AviatorLogger.Companion.MetadataProvider = { logId, context ->
        val traceId = (context.bundle[SpanKeys.TRACE_ID] as? JsonPrimitive)?.content
        val spanId = (context.bundle[SpanKeys.SPAN_ID] as? JsonPrimitive)?.content
        buildMap {
            if (traceId != null) put("traceId", traceId)
            if (spanId != null) put("spanId", spanId)
        }
    }

    override suspend fun accepts(locator: ServiceLocator, definition: cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition) =
        true

    override suspend fun <I : Any, O : Any> execute(
        context: DelegationInvocationContext<I>,
        proceed: suspend () -> Result<O>,
    ): Result<O> = proceed()

    override suspend fun provide(
        locator: ServiceLocator,
        requested: Map<String, JsonElement>,
    ): PluginScopeSnapshot =
        TypedPluginScopeProvider(key) { _, _ -> configurationBundle }.provide(locator, requested)

    override suspend fun <I : Any, O : Any> execute(
        context: EnginePluginContext<I>,
        proceed: suspend () -> Result<O>,
    ): Result<O> = OpenTelemetryEnginePluginExecutor(configurationBundle).execute(context, proceed)

    override fun reconfigure(config: OrchestrationPluginConfig.() -> Unit): OrchestrationPluginInstance {
        val updated = OrchestrationPluginConfig().apply {
            serviceFilter += this@OpenTelemetryPluginInstance.serviceFilter
            stages += this@OpenTelemetryPluginInstance.stages
            phases += this@OpenTelemetryPluginInstance.phases
            lifecycleScopes += this@OpenTelemetryPluginInstance.lifecycleScopes
            executor = this@OpenTelemetryPluginInstance.executor
            metadataProvider = this@OpenTelemetryPluginInstance.metadataProvider
            logMetadataProvider = this@OpenTelemetryPluginInstance.orchestrationLogMetadataProvider
            silentLoggingTags += this@OpenTelemetryPluginInstance.silentLoggingTags
            invocationPolicy = this@OpenTelemetryPluginInstance.invocationPolicy
        }.apply(config)
        return copy(
            orchestrationConfigurationBundle = OrchestrationPluginConfigurationBundle(
                updated.serviceFilter.toSet(),
                updated.stages.toSet(),
                updated.phases.toSet(),
                updated.lifecycleScopes.toSet(),
                updated.silentLoggingTags.toSet(),
            ),
            executor = updated.executor,
            metadataProvider = updated.metadataProvider,
            orchestrationLogMetadataProvider = updated.logMetadataProvider,
            invocationPolicy = updated.invocationPolicy,
        )
    }
}

private fun orchestrationConfiguration(
    config: OpenTelemetryPluginConfig,
) = OrchestrationPluginConfigurationBundle(
    serviceFilter = config.serviceFilter.toSet(),
    stages = setOf(
        OrchestrationStage.PREPARE,
        OrchestrationStage.SCHEDULE,
        OrchestrationStage.FAILURE,
        OrchestrationStage.CLEANUP,
    ),
    phases = emptySet(),
    lifecycleScopes = setOf(OrchestrationLifecycleScope.RUN, OrchestrationLifecycleScope.BINDING),
    silentLoggingTags = config.silentLoggingTags.toSet(),
)

@OptIn(ExperimentalApi::class)
private fun openTelemetryOrchestrationExecutor(
    config: OpenTelemetryPluginConfig,
) = object : OrchestrationPluginExecutor {
    private val tracer = config.openTelemetry!!.tracerProvider
        .getTracer("io.opentelemetry.aviator.orchestration", "1.0.0")

    override suspend fun execute(context: OrchestrationStageContext) {
        val spanKey = when (context.lifecycleScope) {
            OrchestrationLifecycleScope.RUN -> RUN_SPAN_KEY
            OrchestrationLifecycleScope.BINDING -> BINDING_SPAN_KEY
            else -> return
        }
        when (context.stage) {
            OrchestrationStage.PREPARE, OrchestrationStage.SCHEDULE -> {
                val startsHere = (context.stage == OrchestrationStage.PREPARE &&
                    context.lifecycleScope == OrchestrationLifecycleScope.RUN) ||
                    (context.stage == OrchestrationStage.SCHEDULE &&
                        context.lifecycleScope == OrchestrationLifecycleScope.BINDING)
                if (!startsHere) return
                val metadata = context.scope?.metadata ?: return
                if (context.state.get(spanKey) == null) {
                    val span = tracer.startSpan(
                        name = if (spanKey == RUN_SPAN_KEY) "orchestration ${metadata.workflow.format}"
                        else "orchestration binding ${metadata.workflow.format}",
                        spanKind = SpanKind.INTERNAL,
                    ) {
                        setStringAttribute("orchestration.workflow.format", metadata.workflow.format)
                        setStringAttribute("orchestration.workflow.id", metadata.workflow.documentId)
                        setStringAttribute("orchestration.workflow.revision", metadata.workflow.revision)
                        setStringAttribute("orchestration.run.id", metadata.runId.value)
                    }
                    context.state.put(spanKey, span)
                }
            }
            OrchestrationStage.FAILURE -> {
                val span = context.state.get(spanKey) as? Span ?: return
                val failure = context.payload as? ExecutionFailure
                span.setStringAttribute("orchestration.outcome", "failed")
                failure?.let { span.setStringAttribute("orchestration.failure.code", it.code) }
                span.setStatus(StatusData.Error("Orchestration run failed"))
            }
            OrchestrationStage.CLEANUP -> {
                val span = context.state.get(spanKey) as? Span ?: return
                (context.payload as? Boolean)?.let { failed ->
                    span.setStringAttribute("orchestration.outcome", if (failed) "failed" else "succeeded")
                    if (failed) span.setStatus(StatusData.Error("Orchestration run failed"))
                }
                span.end()
                context.state.put(spanKey, null)
            }
            else -> Unit
        }
    }
}

private const val RUN_SPAN_KEY = "run-span"
private const val BINDING_SPAN_KEY = "binding-span"
