package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.engine.EnginePluginContext
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalApi::class)
internal class OpenTelemetryEnginePluginExecutor(
    config: OpenTelemetryPluginConfig,
) {
    private val tracer = config.openTelemetry!!.tracerProvider.getTracer("io.opentelemetry.aviator.engine", "1.0.0")

    suspend fun <I : Any, O : Any> execute(
        context: EnginePluginContext<I>,
        proceed: suspend () -> Result<O>,
    ): Result<O> {
        val span = tracer.startSpan(
            name = "${context.definition.delegation} ${context.locator}",
            spanKind = SpanKind.INTERNAL,
        ) {
            setStringAttribute("aviator.service", context.locator.toString())
            setStringAttribute("aviator.template", context.definition.delegation)
        }
        return try {
            proceed().also { result ->
                val failure = result.exceptionOrNull()
                if (failure == null) {
                    span.setStatus(StatusData.Ok)
                } else {
                    span.setStringAttribute("aviator.outcome", "failed")
                    span.setStringAttribute("error.type", failure::class.simpleName ?: "Exception")
                    span.setStatus(StatusData.Error("Engine invocation failed"))
                }
            }
        } catch (cancelled: CancellationException) {
            span.setStringAttribute("aviator.outcome", "cancelled")
            throw cancelled
        } catch (failure: Throwable) {
            span.setStringAttribute("aviator.outcome", "failed")
            span.setStringAttribute("error.type", failure::class.simpleName ?: "Throwable")
            span.setStatus(StatusData.Error("Engine invocation failed"))
            throw failure
        } finally {
            span.end()
        }
    }
}
