package cloud.mallne.dicentra.aviator.plugin.otel

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.Span

/**
 * W3C Trace Context propagation for Aviator HTTP requests.
 * Injects the `traceparent` header into outgoing requests so downstream servers
 * (Codex, Synapse) can correlate traces.
 *
 * Format: https://www.w3.org/TR/trace-context/#traceparent-header
 * `00-{traceId}-{parentSpanId}-{traceFlags}`
 */
@OptIn(ExperimentalApi::class)
object ContextPropagation {

    const val TRACEPARENT_HEADER = "traceparent"

    /**
     * Build a W3C `traceparent` header value from an OTEL [Span].
     * Returns null if the span context is not valid (no active span).
     */
    fun buildTraceparent(span: Span): String? {
        val ctx = span.spanContext
        if (!ctx.isValid) return null

        val traceId = ctx.traceId.toString().lowercase()
        val spanId = ctx.spanId.toString().lowercase()
        val traceFlags = ctx.traceFlags.toString().lowercase().padStart(2, '0')

        return "00-$traceId-$spanId-$traceFlags"
    }

    /**
     * Parse a `traceparent` header value into its components.
     * Returns null if the header is missing or malformed.
     */
    fun parseTraceparent(header: String): TraceparentComponents? {
        val parts = header.split("-")
        if (parts.size != 4) return null

        return try {
            TraceparentComponents(
                version = parts[0],
                traceId = parts[1],
                parentSpanId = parts[2],
                traceFlags = parts[3],
            )
        } catch (e: Exception) {
            null
        }
    }

    data class TraceparentComponents(
        val version: String,
        val traceId: String,
        val parentSpanId: String,
        val traceFlags: String,
    )
}
