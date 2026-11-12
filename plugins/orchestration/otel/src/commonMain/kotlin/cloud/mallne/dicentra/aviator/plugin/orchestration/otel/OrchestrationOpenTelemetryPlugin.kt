package cloud.mallne.dicentra.aviator.plugin.orchestration.otel

import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginInstance
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginExecutor
import cloud.mallne.dicentra.aviator.orchestration.plugin.orchestrationPlugin
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData

private const val RUN_SPAN_KEY = "run-span"
private const val BINDING_SPAN_KEY = "binding-span"

/** Optional OpenTelemetry tracing for Arazzo runs and materialized bindings; payloads are never recorded. */
@OptIn(ExperimentalApi::class)
fun orchestrationOpenTelemetryPlugin(
    openTelemetry: OpenTelemetry,
    identity: String = "orchestration.otel",
): OrchestrationPluginInstance {
    val tracer = openTelemetry.tracerProvider.getTracer("io.opentelemetry.aviator.orchestration", "1.0.0")
    return orchestrationPlugin(identity) {
        stages += OrchestrationStage.PREPARE
        stages += OrchestrationStage.SCHEDULE
        stages += OrchestrationStage.FAILURE
        stages += OrchestrationStage.CLEANUP
        lifecycleScopes += OrchestrationLifecycleScope.RUN
        lifecycleScopes += OrchestrationLifecycleScope.BINDING
        executor = OrchestrationPluginExecutor { context ->
            val spanKey = when (context.lifecycleScope) {
                OrchestrationLifecycleScope.RUN -> RUN_SPAN_KEY
                OrchestrationLifecycleScope.BINDING -> BINDING_SPAN_KEY
                else -> return@OrchestrationPluginExecutor
            }
            when (context.stage) {
                OrchestrationStage.PREPARE, OrchestrationStage.SCHEDULE -> {
                    val startsHere = (context.stage == OrchestrationStage.PREPARE &&
                        context.lifecycleScope == OrchestrationLifecycleScope.RUN) ||
                        (context.stage == OrchestrationStage.SCHEDULE &&
                            context.lifecycleScope == OrchestrationLifecycleScope.BINDING)
                    if (!startsHere) return@OrchestrationPluginExecutor
                    val metadata = context.scope?.metadata ?: return@OrchestrationPluginExecutor
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
                    val span = context.state.get(spanKey) as? Span ?: return@OrchestrationPluginExecutor
                    val failure = context.payload as? ExecutionFailure
                    span.setStringAttribute("orchestration.outcome", "failed")
                    failure?.let { span.setStringAttribute("orchestration.failure.code", it.code) }
                    span.setStatus(StatusData.Error("Orchestration run failed"))
                }
                OrchestrationStage.CLEANUP -> {
                    val span = context.state.get(spanKey) as? Span ?: return@OrchestrationPluginExecutor
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
}
