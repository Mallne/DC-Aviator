package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline.Companion.TRANSPORT_EXCEPTION_KEY
import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
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
    private val spanNames = mutableMapOf<String, String>()

    override suspend fun beforeRequesting(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        if (!tracer.enabled()) {
            context.log("otel") { warn("tracer.enabled()=false — span NOT created") }
            return
        }

        val chain = context.networkChain.lastOrNull()
        if (chain == null) {
            context.log("otel") { warn("networkChain is empty — span NOT created") }
            return
        }
        val request = chain.request
        if (request == null) {
            context.log("otel") { warn("request is null — span NOT created") }
            return
        }
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

            // HTTP semconv: server address and port
            try {
                val url = io.ktor.http.Url(chain.url)
                setStringAttribute("server.address", url.host)
                if (url.port > 0) {
                    setLongAttribute("server.port", url.port.toLong())
                }
            } catch (_: Exception) {
                // URL parsing failure — don't crash the span
            }

            val requestSize = when (val body = request.outgoingContent) {
                is NetworkBody.Text -> body.string.length.toLong()
                is NetworkBody.Form -> body.formData.size.toLong()
                else -> 0L
            }
            if (requestSize > 0) {
                setLongAttribute("http.request.body.size", requestSize)
            }
        }

        val sampled = span.spanContext.traceFlags.isSampled
        context.log("otel") {
            debug(
                "span CREATED: name='$spanName' spanId=${span.spanContext.spanId} traceId=${span.spanContext.traceId} sampled=$sampled",
                mapOf(
                    "spanName" to spanName,
                    "spanId" to span.spanContext.spanId,
                    "traceId" to span.spanContext.traceId,
                    "sampled" to sampled.toString(),
                )
            )
        }

        context.bundle[SpanKeys.SPAN_ID] = JsonPrimitive(span.spanContext.spanId)
        context.bundle[SpanKeys.TRACE_ID] = JsonPrimitive(span.spanContext.traceId)
        activeSpans[span.spanContext.spanId] = span
        spanNames[span.spanContext.spanId] = spanName

        if (config.enableContextPropagation) {
            val traceparent = ContextPropagation.buildTraceparent(span)
            if (traceparent != null) {
                request.headers.values[ContextPropagation.TRACEPARENT_HEADER] = RequestParameter.Single(traceparent)
            }
        }
    }

    override suspend fun afterRequesting(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        val spanId = (context.bundle[SpanKeys.SPAN_ID] as? JsonPrimitive)?.content ?: return
        val span = activeSpans[spanId]
        if (span == null) {
            context.log("otel") { warn("afterRequesting: span NOT found for spanId=$spanId") }
            return
        }

        // Handle transport-level exception (connection refused, DNS, timeout, SSL)
        val transportError = (context.bundle[TRANSPORT_EXCEPTION_KEY] as? JsonPrimitive)?.content
        if (transportError != null) {
            span.setStringAttribute("error.type", transportError)
            span.setStatus(StatusData.Error(transportError))
            context.log("otel") { warn("afterRequesting: transport error — $transportError") }
            return
        }

        val response = context.networkChain.lastOrNull()?.response
        if (response != null) {
            val statusCode = response.status.value
            span.setLongAttribute("http.response.status_code", statusCode.toLong())

            // HTTP semconv: response content type
            val responseContentType = response.contentType.toString()
            if (responseContentType.isNotEmpty()) {
                span.setStringAttribute("http.response_content_type", responseContentType)
            }

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
        val span = activeSpans.remove(spanId)
        val spanName = spanNames.remove(spanId) ?: "unknown"
        if (span == null) {
            context.log("otel") { warn("afterPaintingResponse: span NOT found for spanId=$spanId") }
            return
        }

        val httpOk = (context.networkChain.lastOrNull()?.response?.status?.value ?: 500) < 400
        if (httpOk && context.result == null) {
            span.setBooleanAttribute("aviator.deserialization.success", false)
            span.setStatus(StatusData.Error("Deserialization failed"))
        } else {
            span.setBooleanAttribute("aviator.deserialization.success", true)
        }

        context.log("otel") {
            debug(
                "span ENDING: spanId=$spanId name=$spanName",
                mapOf("spanId" to spanId, "spanName" to spanName)
            )
        }
        span.end()
        context.log("otel") {
            debug(
                "span ENDED: spanId=$spanId",
                mapOf("spanId" to spanId)
            )
        }
    }

    override suspend fun afterFinished(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        val spanId = (context.bundle[SpanKeys.SPAN_ID] as? JsonPrimitive)?.content ?: return
        val span = activeSpans.remove(spanId) ?: return
        spanNames.remove(spanId)
        context.log("otel") {
            debug(
                "afterFinished: safety-net ending spanId=$spanId (should have been ended in afterPaintingResponse)",
                mapOf("spanId" to spanId)
            )
        }
        if (span.spanContext.isValid) {
            span.end()
        }
    }
}
