package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalApi::class)
class OpenTelemetryPluginExecutor(
    private val config: OpenTelemetryPluginConfig
) : PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any> {

    private val tracer by lazy {
        config.openTelemetry!!.tracerProvider.getTracer(
            name = "io.opentelemetry.aviator",
            version = "1.0.0"
        )
    }

    private val activeSpans = mutableMapOf<String, Span>()

    override suspend fun beforeRequesting(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        if (!tracer.enabled()) return

        val chain = context.networkChain.lastOrNull() ?: return
        val request = chain.request ?: return
        val serviceLocator = context.dataHolder.serviceLocator
        val route = context.dataHolder.route

        val spanName = "${request.method.value} ${serviceLocator}/${route.operationId ?: route.path}"

        val span = tracer.startSpan(
            name = spanName,
            spanKind = SpanKind.CLIENT,
        ) {
            setStringAttribute("aviator.service", serviceLocator.toString())
            setStringAttribute("aviator.operation", route.operationId ?: "unknown")
            setStringAttribute("http.request.method", request.method.value)
            setStringAttribute("url.full", chain.url)
            setStringAttribute("http.route", route.path)

            val requestSize = when (val body = request.outgoingContent) {
                is NetworkBody.Text -> body.string.length.toLong()
                is NetworkBody.Form -> body.formData.size.toLong()
                else -> 0L
            }
            if (requestSize > 0) {
                setLongAttribute("http.request.body.size", requestSize)
            }
        }

        context.bundle[SpanKeys.SPAN_ID] = JsonPrimitive(span.spanContext.spanId)
        activeSpans[span.spanContext.spanId] = span
    }

    override suspend fun afterRequesting(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        val spanId = (context.bundle[SpanKeys.SPAN_ID] as? JsonPrimitive)?.content ?: return
        val span = activeSpans[spanId] ?: return

        val response = context.networkChain.lastOrNull()?.response
        if (response != null) {
            val statusCode = response.status.value
            span.setLongAttribute("http.response.status_code", statusCode.toLong())

            val responseSize = response.content?.size?.toLong() ?: 0L
            if (responseSize > 0) {
                span.setLongAttribute("http.response.body.size", responseSize)
            }

            span.setStatus(
                if (statusCode in config.errorStatusCodes) {
                    StatusData.Error("HTTP $statusCode")
                } else {
                    StatusData.Ok
                }
            )
        } else {
            span.setStatus(StatusData.Error("No response received"))
        }
    }

    override suspend fun afterPaintingResponse(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        val spanId = (context.bundle[SpanKeys.SPAN_ID] as? JsonPrimitive)?.content ?: return
        val span = activeSpans.remove(spanId) ?: return

        val httpOk = (context.networkChain.lastOrNull()?.response?.status?.value ?: 500) < 400
        if (httpOk && context.result == null) {
            span.setBooleanAttribute("aviator.deserialization.success", false)
            span.setStatus(StatusData.Error("Deserialization failed"))
        } else {
            span.setBooleanAttribute("aviator.deserialization.success", true)
        }

        span.end()
    }

    override suspend fun afterFinished(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        val spanId = (context.bundle[SpanKeys.SPAN_ID] as? JsonPrimitive)?.content ?: return
        val span = activeSpans.remove(spanId) ?: return
        if (span.spanContext.isValid) {
            span.end()
        }
    }
}
