package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.MutableRequestOptions
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import cloud.mallne.dicentra.aviator.core.execution.logging.DeferredAviatorLogger
import cloud.mallne.dicentra.aviator.core.io.*
import cloud.mallne.dicentra.aviator.core.io.adapter.request.RequestBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.ResponseBodyAdapter
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.uuid.Uuid

/**
 * The central execution context for a single Aviator HTTP request.
 *
 * [AviatorExecutionContext] carries all mutable state for one request through the
 * [AviatorExecutionPipeline]. Each pipeline stage reads from and writes to this context,
 * and plugins can intercept any stage via [cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor].
 *
 * ## Type Parameters
 *
 * - [O] — the deserialized response type (e.g. `MyResponseDto`). Must be `@Serializable`.
 * - [B] — the request body type (e.g. `MyRequestDto`). Must be `@Serializable`. Use [cloud.mallne.dicentra.aviator.core.NoBody]
 *   for requests without a body.
 *
 * ## Lifecycle
 *
 * A context is created per-request by the service client (e.g. [cloud.mallne.dicentra.aviator.client.ktor.KtorAviatorService]),
 * passed through the pipeline, and returned as the result of [cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionPipeline.run].
 * After the pipeline completes, [result] holds the deserialized response or `null` on failure.
 *
 * ## Pipeline Stages
 *
 * The context progresses through these [stage] values in order:
 * 1. [AviatorExecutionStages.Unstarted] — initial state
 * 2. [AviatorExecutionStages.Invocation] — pre-flight setup
 * 3. [AviatorExecutionStages.ConstraintValidation] — request parameter validation
 * 4. [AviatorExecutionStages.PathMatching] — URL template resolution
 * 5. [AviatorExecutionStages.FormingRequest] — request body/headers assembly
 * 6. [AviatorExecutionStages.Requesting] — network I/O (Ktor call)
 * 7. [AviatorExecutionStages.PaintingResponse] — response deserialization
 * 8. [AviatorExecutionStages.Finished] — post-processing, span finalization
 *
 * ## Plugin Extension Points
 *
 * Plugins intercept the pipeline by implementing [cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor]
 * and registering via [dataHolder.plugins]. Each stage has `before*` and `after*` hooks that receive
 * this context. Plugins can:
 * - Read/write [bundle] to pass data between stages (e.g. trace context, auth tokens)
 * - Mutate [requestParams], [options], or [networkChain] to modify the request
 * - Read [result] after deserialization to add post-processing
 *
 * ## Bundle
 *
 * [bundle] is a `MutableMap<String, JsonElement>` shared across all pipeline stages and plugins.
 * It is the primary inter-plugin communication mechanism. Keys should be namespaced by plugin
 * identity (e.g. `"dc-otel-span-id"`, `"dc-otel-trace-id"`). Values must be `@Serializable` JSON.
 *
 * ## Logging
 *
 * Logging is built into the context via [log]. The [logger] is set by the logging plugin
 * ([cloud.mallne.dicentra.aviator.core.execution.logging.LoggingPlugin]) and wrapped in a
 * [DeferredAviatorLogger] that automatically injects metadata from all plugins before each
 * log call. Callers provide a [loggingTag] (stable ID) to allow suppressions via
 * [cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope.silentLoggingTags].
 *
 * Example:
 * ```kotlin
 * context.log("Auth.Check") {
 *     debug("Token refreshed, expires in ${ttl}s")
 * }
 * ```
 *
 * ## Thread Safety
 *
 * A single [AviatorExecutionContext] is only accessed within one coroutine (the pipeline's caller).
 * Multiple concurrent requests each get their own context instance. Do not share contexts across
 * coroutines.
 *
 * @see AviatorExecutionPipeline
 * @see AviatorExecutionStages
 * @see cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
 */
interface AviatorExecutionContext<O : @Serializable Any, B : @Serializable Any> {

    /**
     * The current pipeline stage. Mutated by [AviatorExecutionPipeline.escalate] as the
     * request progresses through validation, network I/O, and deserialization.
     *
     * Plugins should generally not set this directly — the pipeline manages stage transitions.
     */
    var stage: AviatorExecutionStages

    /**
     * Static service configuration: service locator, registered adapters/deserializers,
     * installed plugins, and the HTTP client engine.
     *
     * This is shared across all requests to the same service and should not be mutated
     * after service creation.
     */
    val dataHolder: AviatorServiceDataHolder

    /**
     * Triple of (KClass, KType, KSerializer) for the response type [O].
     *
     * Used by the pipeline to deserialize the network response into the correct type.
     * Created at request time via `typeOf<O>()` and `serializer<O>()`.
     */
    val outputClazz: Triple<KClass<O>, KType, KSerializer<O>>

    /**
     * Triple of (KClass, KType, KSerializer) for the request body type [B].
     * `null` for requests without a body (e.g. GET, DELETE).
     *
     * Set during [AviatorExecutionStages.Invocation] and consumed during
     * [AviatorExecutionStages.FormingRequest] to serialize the body.
     */
    var bodyClazz: Triple<KClass<B>, KType, KSerializer<B>>?

    /**
     * Shared mutable map for inter-plugin and inter-stage communication.
     *
     * Plugins store and retrieve data here using namespaced keys. Common uses:
     * - **Trace context**: `"dc-otel-span-id"`, `"dc-otel-trace-id"` (set by the OTEL plugin)
     * - **Auth tokens**: set by the HTTP auth plugin during [AviatorExecutionStages.Invocation]
     * - **Metadata**: any plugin can attach data for downstream consumers
     *
     * Values must be [JsonElement] instances (use [JsonPrimitive][kotlinx.serialization.json.JsonPrimitive],
     * [JsonObject][kotlinx.serialization.json.JsonObject], etc.). The bundle is per-request and
     * does not persist across requests.
     *
     * ```kotlin
     * // Writing
     * context.bundle["my-plugin.key"] = JsonPrimitive("value")
     * // Reading
     * val value = (context.bundle["my-plugin.key"] as? JsonPrimitive)?.content
     * ```
     */
    val bundle: MutableMap<String, JsonElement>

    /**
     * The deserialized response body after [AviatorExecutionStages.PaintingResponse].
     *
     * Set to `null` initially. The response adapter populates this after deserializing
     * the network response. `null` after pipeline completion indicates deserialization failure
     * (check network status and raw response via [networkChain]).
     */
    var result: O?

    /**
     * The parsed request body before serialization. Set by the caller during context creation.
     *
     * During [AviatorExecutionStages.FormingRequest], the registered [RequestBodyAdapter]
     * serializes this into the HTTP request body. `null` for bodyless requests.
     */
    var body: B?

    /**
     * The network request/response chain for this request.
     *
     * Each entry represents one HTTP round-trip. For most requests this list has a single
     * entry. Redirects or retries may produce multiple entries.
     *
     * - **Before [AviatorExecutionStages.Requesting]**: the last entry holds the outgoing
     *   [NetworkRequest] (headers, URL, body).
     * - **After [AviatorExecutionStages.Requesting]**: the last entry holds the incoming
     *   [NetworkResponse] (status, headers, body bytes).
     *
     * Plugins inspect this to read response status, set auth headers, or implement retries.
     */
    val networkChain: MutableList<out NetworkChain<out NetworkRequest<out NetworkHeader>, out NetworkResponse<out NetworkHeader>, out NetworkHeader>>

    /**
     * Mutable per-request options that override the service-level defaults.
     *
     * Set by the caller to customize headers, timeouts, query parameters, etc.
     * Plugins can mutate this during earlier stages to inject dynamic values.
     *
     * @see RequestOptions
     */
    val options: MutableRequestOptions

    /**
     * Resolved request parameters (path variables, query parameters, headers).
     *
     * Populated during [AviatorExecutionStages.PathMatching] from the route template
     * and caller-provided values. Plugins can mutate this to add or override parameters.
     */
    var requestParams: RequestParameters

    /**
     * The logger for this request. Set by the logging plugin during initialization.
     *
     * Wrapped in [DeferredAviatorLogger] which automatically collects metadata from all
     * plugins via [AviatorPluginInstance.logMetadataProvider] before each log call.
     * Annotated [InternalAviatorAPI] because direct access bypasses metadata injection —
     * use [log] instead.
     *
     * @see log
     */
    @InternalAviatorAPI
    var logger: AviatorLogger?

    /**
     * Log a message with automatic metadata injection and suppression support.
     *
     * The [loggingTag] serves two purposes:
     * 1. **Suppressions**: if any plugin's
     *    [silentLoggingTags][cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope.silentLoggingTags]
     *    contains this ID, the log is skipped entirely.
     * 2. **Metadata routing**: passed to each plugin's
     *    [logMetadataProvider][cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance.logMetadataProvider]
     *    so plugins can attach context-specific metadata (e.g. trace ID, span ID).
     *
     * The [loggerScope] lambda receives the [AviatorLogger] with all metadata already injected.
     * Call [AviatorLogger.error], [AviatorLogger.warn], [AviatorLogger.info], [AviatorLogger.debug],
     * or [AviatorLogger.trace] on it.
     *
     * Example:
     * ```kotlin
     * log("HttpAuth.Refresh") {
     *     info("Token refreshed successfully")
     * }
     * log("Adapter.NotFound") {
     *     debug("Adapter for $contentType not found, maybe a plugin will handle it")
     * }
     * ```
     *
     * @param loggingTag stable identifier for this log call. Use a dot-separated convention
     *   (e.g. `"HttpAuth.Refresh"`, `"Adapter.NotFound"`). Must be unique within the request
     *   if suppression is needed.
     * @param loggerScope the logging lambda, invoked on the [AviatorLogger] with injected metadata.
     */
    @OptIn(InternalAviatorAPI::class)
    fun log(id: String = Uuid.random().toString(), loggerScope: AviatorLogger.() -> Unit) {
        val loggingSuppressions = getAllLoggingSuppressions()
        if (!loggingSuppressions.contains(id)) {
            if (logger is DeferredAviatorLogger) {
                (logger as DeferredAviatorLogger).currentMetadata.putAll(getLoggingMetadata(id))
            }
            logger?.loggerScope()
        }
    }

    /**
     * Collect suppression IDs from all installed plugins.
     *
     * A log call with an [id] matching any suppression is silently dropped.
     */
    private fun getAllLoggingSuppressions(): Set<String> {
        val set = mutableSetOf<String>()
        for (plugin in dataHolder.plugins) {
            set.addAll(plugin.configurationBundle.silentLoggingTags)
        }
        return set
    }

    /**
     * Gather metadata from all installed plugins for the given log [forId].
     *
     * Each plugin's [logMetadataProvider][cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance.logMetadataProvider]
     * is called with the logging tag and this context. Results are merged; later plugins
     * overwrite earlier ones on key collision.
     */
    private fun getLoggingMetadata(forId: String): Map<String, String> {
        val set = mutableMapOf<String, String>()
        for (plugin in dataHolder.plugins) {
            set.putAll(plugin.logMetadataProvider(forId, this))
        }
        return set
    }

    /**
     * Find a [RequestBodyAdapter] for the given request body type.
     *
     * Returns `null` if no adapter is registered for this body type — the Ktor engine
     * or a plugin may handle serialization instead.
     *
     * @param T the network body type (e.g. [NetworkBody.Text], [NetworkBody.Form])
     * @return the matching adapter, or `null` if unhandled
     */
    fun <T : NetworkBody> adapterFor(body: T): RequestBodyAdapter<T>? {
        val f = dataHolder.adapters.find { body::class == it.target }
        if (f == null) {
            log("Adapter.NotFound") {
                debug("Adapter for $body not found, maybe a plugin or the client will handle the Type")
            }
        }
        return f as RequestBodyAdapter<T>
    }

    /**
     * Find a [RequestBodyAdapter] for the given content type.
     *
     * Used during [AviatorExecutionStages.FormingRequest] to serialize the request body
     * with the correct format (JSON, XML, form-encoded, etc.).
     *
     * @return the matching adapter, or `null` if no adapter understands this content type
     */
    fun adapterFor(contentType: ContentType): RequestBodyAdapter<*>? {
        val f = dataHolder.adapters.find { it.understands(contentType) }
        if (f == null) {
            log("Adapter.NotFound") {
                debug("Adapter for $contentType not found, maybe a plugin or the client will handle the Type")
            }
        }
        return f
    }

    /**
     * Find a [ResponseBodyAdapter] for the given content type.
     *
     * Used during [AviatorExecutionStages.PaintingResponse] to deserialize the response body.
     * The adapter is selected based on the response `Content-Type` header.
     *
     * @return the matching adapter, or `null` if no adapter can deserialize this content type
     */
    fun deserializerFor(contentType: ContentType): ResponseBodyAdapter? {
        val f = dataHolder.deserializers.find { it.understands(contentType, this) }
        if (f == null) {
            log("Adapter.NotFound") {
                debug("Adapter for $contentType not found, maybe a plugin or the client will handle the Type")
            }
        }
        return f
    }
}
