package cloud.mallne.dicentra.aviator.plugin.orchestration.otel

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.arazzo
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.execution.*
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AnyValue
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.config.ConfigProvider
import io.opentelemetry.kotlin.factory.ContextFactory
import io.opentelemetry.kotlin.logging.LoggerProvider
import io.opentelemetry.kotlin.metrics.MeterProvider
import io.opentelemetry.kotlin.propagation.TextMapPropagator
import io.opentelemetry.kotlin.tracing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalApi::class)
class OrchestratorOtelDslTest {
    @Test
    fun dslInstallsOtelForRunAndBindingAndClosesSpans() = runTest {
        val started = mutableListOf<String>()
        val ended = mutableListOf<String>()
        val telemetry = object : OpenTelemetry {
            override val tracerProvider = object : TracerProvider {
                override fun getTracer(name: String, version: String?, schemaUrl: String?,
                    attributes: (AttributesMutator.() -> Unit)?): Tracer = object : Tracer {
                    override fun enabled() = true
                    override fun startSpan(name: String, parentContext: io.opentelemetry.kotlin.context.Context?,
                        spanKind: SpanKind, startTimestamp: Long?, action: (SpanCreationAction.() -> Unit)?): Span {
                        started += name
                        return RecordingSpan { ended += name }
                    }
                }
            }
            override val loggerProvider: LoggerProvider get() = error("unused")
            override val meterProvider: MeterProvider get() = error("unused")
            override val configProvider: ConfigProvider get() = error("unused")
            override val context: ContextFactory get() = error("unused")
            override val propagator: TextMapPropagator get() = error("unused")
        }
        val identity = ConstituentIdentity(ServiceLocator("weather>get"), "provider", "source", "revision", "forecast")
        val engine = orchestrator {
            plugins { install(orchestrationOpenTelemetryPlugin(telemetry)) }
            arazzo {
                workflow = { ArazzoWorkflow("flow", listOf(ArazzoStep("fetch", operationId = "forecast"))) }
                authorize = { null }
                operationIndex = listOf(identity)
                policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                    PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
                binding = { env -> MaterializedOperationBinding(
                    listOf(MaterializedConstituent<JsonElement>(identity) {
                        val body = BodyValue.JsonValue(JsonObject(emptyMap()))
                        ConstituentExecution(ConstituentOutcome.Succeeded(body), ResponseSnapshot(body, http = HttpResponseMetadata(200)))
                    }), isUsable = { true }, aggregate = { it.single() }, plugins = env.plugins,
                ) }
            }
        }
        assertIs<ExecutionOutcome.Succeeded>(engine.execute(ExecutionRequest(WorkflowIdentity("arazzo", "1.1.0", "doc", "rev"))).outcome)
        assertEquals(listOf("orchestration arazzo", "orchestration binding arazzo"), started)
        assertEquals(listOf("orchestration binding arazzo", "orchestration arazzo"), ended)
    }
}

@OptIn(ExperimentalApi::class)
private class RecordingSpan(private val onEnd: () -> Unit) : Span {
    override val spanContext: SpanContext get() = error("unused")
    override val parent: SpanContext get() = error("unused")
    override fun isRecording() = true
    override fun setName(name: String) = Unit
    override fun setStatus(status: StatusData) = Unit
    override fun end() = onEnd()
    override fun end(timestamp: Long) = onEnd()
    override fun addLink(spanContext: SpanContext, attributes: (AttributesMutator.() -> Unit)?) = Unit
    override fun addEvent(name: String, timestamp: Long?, attributes: (AttributesMutator.() -> Unit)?) = Unit
    override fun setBooleanAttribute(key: String, value: Boolean) = Unit
    override fun setStringAttribute(key: String, value: String) = Unit
    override fun setLongAttribute(key: String, value: Long) = Unit
    override fun setDoubleAttribute(key: String, value: Double) = Unit
    override fun setBooleanListAttribute(key: String, value: List<Boolean>) = Unit
    override fun setStringListAttribute(key: String, value: List<String>) = Unit
    override fun setLongListAttribute(key: String, value: List<Long>) = Unit
    override fun setDoubleListAttribute(key: String, value: List<Double>) = Unit
    override fun setByteArrayAttribute(key: String, value: ByteArray) = Unit
    override fun setAnyValueAttribute(key: String, value: AnyValue) = Unit
}
