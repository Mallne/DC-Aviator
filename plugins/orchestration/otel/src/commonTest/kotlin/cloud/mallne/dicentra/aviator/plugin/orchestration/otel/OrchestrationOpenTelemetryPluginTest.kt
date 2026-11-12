package cloud.mallne.dicentra.aviator.plugin.orchestration.otel

import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.config.ConfigProvider
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.logging.LoggerProvider
import io.opentelemetry.kotlin.metrics.MeterProvider
import io.opentelemetry.kotlin.propagation.TextMapPropagator
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.TracerProvider
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalApi::class)
class OrchestrationOpenTelemetryPluginTest {
    @Test
    fun pluginIsOptionalAndDeclaresRunAndBindingLifecycleBoundaries() {
        var tracerName: String? = null
        val telemetry = object : OpenTelemetry {
            override val tracerProvider = object : TracerProvider {
                override fun getTracer(
                    name: String,
                    version: String?,
                    schemaUrl: String?,
                    attributes: (io.opentelemetry.kotlin.attributes.AttributesMutator.() -> Unit)?,
                ): Tracer {
                    tracerName = name
                    return object : Tracer {
                        override fun enabled() = true
                        override fun startSpan(
                            name: String,
                            parentContext: io.opentelemetry.kotlin.context.Context?,
                            spanKind: io.opentelemetry.kotlin.tracing.SpanKind,
                            startTimestamp: Long?,
                            action: (io.opentelemetry.kotlin.tracing.SpanCreationAction.() -> Unit)?,
                        ): io.opentelemetry.kotlin.tracing.Span = error("span not requested by installation")
                    }
                }
            }
            override val loggerProvider: LoggerProvider get() = error("unused")
            override val meterProvider: MeterProvider get() = error("unused")
            override val configProvider: ConfigProvider get() = error("unused")
            override val context: ContextFactory get() = error("unused")
            override val propagator: TextMapPropagator get() = error("unused")
        }

        val plugin = orchestrationOpenTelemetryPlugin(telemetry)

        assertEquals("io.opentelemetry.aviator.orchestration", tracerName)
        assertEquals(setOf(OrchestrationStage.PREPARE, OrchestrationStage.SCHEDULE,
            OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), plugin.stages)
        assertEquals(setOf(OrchestrationLifecycleScope.RUN, OrchestrationLifecycleScope.BINDING), plugin.lifecycleScopes)
    }
}
