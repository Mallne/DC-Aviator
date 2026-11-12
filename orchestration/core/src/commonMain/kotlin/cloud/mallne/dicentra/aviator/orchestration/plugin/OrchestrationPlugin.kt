package cloud.mallne.dicentra.aviator.orchestration.plugin

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationPolicyPlugin
import cloud.mallne.dicentra.aviator.orchestration.execution.OrchestrationDsl
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** Stable lifecycle points shared by format adapters and orchestration bindings. */
enum class OrchestrationStage {
    PREPARE, VALIDATE, RESOLVE, SELECT, SCHEDULE, MAP_INPUT, BEFORE_ATTEMPT, AFTER_ATTEMPT,
    HANDLE_RESULT, AGGREGATE, MAP_OUTPUT, COMPLETE, FAILURE, CLEANUP,
}

enum class OrchestrationHookPhase { BEFORE, AFTER }

private val afterStages = setOf(OrchestrationStage.AFTER_ATTEMPT, OrchestrationStage.HANDLE_RESULT,
    OrchestrationStage.AGGREGATE, OrchestrationStage.MAP_OUTPUT, OrchestrationStage.COMPLETE,
    OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP)

private fun defaultHookPhase(stage: OrchestrationStage) =
    if (stage in afterStages) OrchestrationHookPhase.AFTER else OrchestrationHookPhase.BEFORE

enum class OrchestrationLifecycleScope { RUN, STEP, BINDING, ATTEMPT }

/** Plugin-namespaced state shared only inside one run scope; actual attempt data stays in stage payloads. */
class OrchestrationPluginState internal constructor(
    private val values: MutableMap<String, Any?> = mutableMapOf(),
) {
    private val mutex = Mutex()
    private val children = mutableMapOf<String, OrchestrationPluginState>()

    suspend fun get(key: String): Any? = mutex.withLock { values[key] }
    suspend fun put(key: String, value: Any?) { mutex.withLock { values[key] = value } }
    suspend fun <T> update(key: String, transform: (Any?) -> Pair<Any?, T>): T = mutex.withLock {
        val (value, result) = transform(values[key])
        values[key] = value
        result
    }

    internal suspend fun forPlugin(identity: String): OrchestrationPluginState = mutex.withLock {
        children.getOrPut(identity) { OrchestrationPluginState() }
    }
}

/**
 * Scoped stage data. Input/payload values are defined by the engine adapter for [stage]; [result],
 * [invocation], [scope] and [constituent] retain the core orchestration types. Adapter-owned payload
 * replacements are validated by that adapter before use. `reject` stops the current lifecycle;
 * `stopWith` is honored only at explicitly documented result-owning stages.
 */
class OrchestrationStageContext(
    val stage: OrchestrationStage,
    val invocation: CapabilityInvocation? = null,
    val scope: InvocationScope? = null,
    state: OrchestrationPluginState,
    val constituent: ConstituentIdentity? = null,
    payload: Any? = null,
    input: Any? = null,
    result: CapabilityResult? = null,
    val lifecycleScope: OrchestrationLifecycleScope = OrchestrationLifecycleScope.BINDING,
    phase: OrchestrationHookPhase = defaultHookPhase(stage),
) {
    internal var logger: AviatorLogger? = null
    internal var isLoggingSuppressed: (String) -> Boolean = { false }
    internal var loggingMetadata: (String) -> Map<String, String> = { emptyMap() }

    var phase: OrchestrationHookPhase = phase
        internal set
    var state: OrchestrationPluginState = state
        internal set
    var payload: Any? = payload
    var input: Any? = input
    var result: CapabilityResult? = result
    var shortCircuit: CapabilityResult? = null
        private set
    var rejection: ExecutionFailure? = null
        private set

    fun stopWith(result: CapabilityResult) { shortCircuit = result }
    fun reject(failure: ExecutionFailure) { rejection = failure }

    /** Log through the configured host logger with stage and installed-plugin metadata. */
    fun log(id: String = Uuid.random().toString(), loggerScope: AviatorLogger.() -> Unit) {
        if (isLoggingSuppressed(id)) return
        logger?.let { loggerScope(OrchestrationDeferredLogger(it, loggingMetadata(id))) }
    }
}

fun interface OrchestrationPluginExecutor {
    /** Called once for each configured stage/phase/scope/target match. */
    suspend fun execute(context: OrchestrationStageContext)
}

fun interface OrchestrationMetadataProvider {
    fun provide(context: OrchestrationStageContext): Map<String, String>
}

/** Per-log metadata provider, corresponding to Aviator's plugin `logMetadataProvider`. */
fun interface OrchestrationLogMetadataProvider {
    fun provide(loggingTag: String, context: OrchestrationStageContext): Map<String, String>
}

interface OrchestrationPluginInstance {
    val identity: String
    val configurationBundle: OrchestrationPluginConfigurationBundle
    val serviceFilter: Set<ServiceLocator>
    val stages: Set<OrchestrationStage>
    /** Empty means the stage's conventional phase (before for forward stages, after for unwind/result stages). */
    val phases: Set<OrchestrationHookPhase>
    val lifecycleScopes: Set<OrchestrationLifecycleScope>
    val executor: OrchestrationPluginExecutor
    val metadataProvider: OrchestrationMetadataProvider
    val logMetadataProvider: OrchestrationLogMetadataProvider
        get() = OrchestrationLogMetadataProvider { _, _ -> emptyMap() }
    val silentLoggingTags: Set<String>
        get() = emptySet()
    /** Optional run policy installed alongside lifecycle hooks. */
    val invocationPolicy: InvocationPolicyPlugin? get() = null
    /** Return a new instance; existing executions retain their installation snapshot. */
    fun reconfigure(config: OrchestrationPluginConfig.() -> Unit): OrchestrationPluginInstance
}

interface OrchestrationPlugin {
    val identity: String
    fun install(config: OrchestrationPluginConfig.() -> Unit = {}): OrchestrationPluginInstance
}

@OrchestrationDsl
class OrchestrationPluginConfig {
    val serviceFilter: MutableSet<ServiceLocator> = linkedSetOf()
    val stages: MutableSet<OrchestrationStage> = linkedSetOf()
    val phases: MutableSet<OrchestrationHookPhase> = linkedSetOf()
    val lifecycleScopes: MutableSet<OrchestrationLifecycleScope> = linkedSetOf()
    var executor: OrchestrationPluginExecutor = OrchestrationPluginExecutor { }
    var metadataProvider: OrchestrationMetadataProvider = OrchestrationMetadataProvider { emptyMap() }
    var logMetadataProvider: OrchestrationLogMetadataProvider = OrchestrationLogMetadataProvider { _, _ -> emptyMap() }
    val silentLoggingTags: MutableSet<String> = linkedSetOf()
    var invocationPolicy: InvocationPolicyPlugin? = null
}

data class OrchestrationPluginConfigurationBundle(
    val serviceFilter: Set<ServiceLocator>,
    val stages: Set<OrchestrationStage>,
    val phases: Set<OrchestrationHookPhase>,
    val lifecycleScopes: Set<OrchestrationLifecycleScope>,
    val silentLoggingTags: Set<String> = emptySet(),
)

/** Declared stage surface for one format/engine adapter. Compose these when installing one registry
 * across a runner, binding and transport boundary. */
data class OrchestrationEngineStageCapabilities(
    val engineId: String,
    val supportedStages: Set<OrchestrationStage>,
) {
    init { require(engineId.isNotBlank()) }
}

/**
 * Immutable installation snapshot. Duplicate plugin identities are rejected by the registry.
 * Before hooks run in installation order; result and unwind hooks run in reverse order.
 * Rejection stops forward processing; FAILURE/CLEANUP always visit every applicable plugin and
 * aggregate hook exceptions so one plugin cannot suppress another plugin's cleanup.
 */
class OrchestrationPluginRegistry(
    instances: List<OrchestrationPluginInstance>,
    private val logger: AviatorLogger? = null,
) {
    private val installed = instances.toList()
    init {
        require(installed.all { it.identity.isNotBlank() }) { "Plugin identity must not be blank" }
        require(installed.map { it.identity }.distinct().size == installed.size) { "Duplicate orchestration plugin identity" }
        require(installed.count { it.invocationPolicy != null } <= 1) {
            "Only one atomic invocation policy plugin may be installed per registry"
        }
    }

    fun get(identity: String): OrchestrationPluginInstance? = installed.firstOrNull { it.identity == identity }

    /** Factories consumed by a run scope; policy state is never shared between executions. */
    fun invocationPolicyPlugins(): List<InvocationPolicyPlugin> = installed.mapNotNull { it.invocationPolicy }

    /** Fail during configuration rather than silently registering a stage an adapter never calls. */
    fun requireSupportedBy(capabilities: OrchestrationEngineStageCapabilities) {
        val unsupported = installed.flatMap { plugin ->
            plugin.stages.filterNot(capabilities.supportedStages::contains).map { plugin.identity to it }
        }
        require(unsupported.isEmpty()) {
            "Engine '${capabilities.engineId}' does not support installed plugin stages: " +
                unsupported.joinToString { (identity, stage) -> "$identity:$stage" }
        }
    }

    suspend fun execute(context: OrchestrationStageContext, phase: OrchestrationHookPhase = context.phase) {
        val previousPhase = context.phase
        val previousLogger = context.logger
        val previousSuppression = context.isLoggingSuppressed
        val previousLoggingMetadata = context.loggingMetadata
        context.phase = phase
        context.logger = logger
        context.isLoggingSuppressed = { tag -> installed.any { tag in it.silentLoggingTags } }
        context.loggingMetadata = { tag -> loggingMetadata(tag, context) }
        try {
            logLifecycle(context)
            executeInPhase(context)
        } finally {
            context.phase = previousPhase
            context.logger = previousLogger
            context.isLoggingSuppressed = previousSuppression
            context.loggingMetadata = previousLoggingMetadata
        }
    }

    /** Core lifecycle logging; unlike custom logging, this is not an installable plugin. */
    private fun logLifecycle(context: OrchestrationStageContext) {
        context.log("orchestration.lifecycle") {
            val metadata = (context.payload as? ExecutionFailure)?.let { failure ->
                mapOf("failure.code" to failure.code)
            }.orEmpty()
            when (context.stage) {
                OrchestrationStage.FAILURE -> warn("Orchestration lifecycle failed", metadata)
                OrchestrationStage.CLEANUP -> debug("Orchestration lifecycle cleanup", metadata)
                else -> info("Orchestration lifecycle stage", metadata)
            }
        }
    }

    private fun loggingMetadata(tag: String, context: OrchestrationStageContext): Map<String, String> = buildMap {
        put("orchestration.stage", context.stage.name)
        put("orchestration.phase", context.phase.name)
        put("orchestration.scope", context.lifecycleScope.name)
        context.scope?.metadata?.let { run ->
            put("workflow.format", run.workflow.format)
            put("workflow.id", run.workflow.documentId)
            put("workflow.revision", run.workflow.revision)
            put("run.id", run.runId.value)
            run.stepId?.let { put("step.id", it) }
        }
        context.constituent?.let { constituent ->
            put("service.locator", constituent.locator.toString())
            put("constituent.source", constituent.sourceId)
            constituent.operationId?.let { put("operation.id", it) }
        }
        for (plugin in installed) {
            plugin.logMetadataProvider.provide(tag, context).forEach { (key, value) ->
                put(key, if (isSensitiveMetadataKey(key)) "[REDACTED]" else value)
            }
        }
    }

    private suspend fun executeInPhase(context: OrchestrationStageContext) {
        val reverse = context.phase == OrchestrationHookPhase.AFTER
        val unwind = context.stage == OrchestrationStage.FAILURE || context.stage == OrchestrationStage.CLEANUP
        val ordered = if (reverse) installed.asReversed() else installed
        var unwindFailure: Throwable? = null
        for (plugin in ordered) {
            val stageMatches = plugin.stages.isEmpty() || context.stage in plugin.stages
            val phaseMatches = context.phase in (plugin.phases.ifEmpty { setOf(defaultHookPhase(context.stage)) })
            val scopeMatches = plugin.lifecycleScopes.isEmpty() || context.lifecycleScope in plugin.lifecycleScopes
            val target = context.invocation?.locator ?: context.constituent?.locator
            val targetMatches = plugin.serviceFilter.isEmpty() || target in plugin.serviceFilter
            if (stageMatches && phaseMatches && scopeMatches && targetMatches) {
                val previousState = context.state
                context.state = previousState.forPlugin(plugin.identity)
                try {
                    plugin.executor.execute(context)
                } catch (cause: Throwable) {
                    if (!unwind) throw cause
                    val primary = unwindFailure
                    if (primary == null) unwindFailure = cause else primary.addSuppressed(cause)
                } finally {
                    context.state = previousState
                }
                if (!unwind && (context.shortCircuit != null || context.rejection != null)) return
            }
        }
        unwindFailure?.let { throw it }
    }

    /** Namespaces keys by plugin identity; sensitive-looking keys are redacted before exposure. */
    fun metadata(context: OrchestrationStageContext): Map<String, String> = buildMap {
        for (plugin in installed) {
            val stageMatches = plugin.stages.isEmpty() || context.stage in plugin.stages
            val phaseMatches = context.phase in (plugin.phases.ifEmpty { setOf(defaultHookPhase(context.stage)) })
            val scopeMatches = plugin.lifecycleScopes.isEmpty() || context.lifecycleScope in plugin.lifecycleScopes
            val target = context.invocation?.locator ?: context.constituent?.locator
            val targetMatches = plugin.serviceFilter.isEmpty() || target in plugin.serviceFilter
            if (stageMatches && phaseMatches && scopeMatches && targetMatches) {
                plugin.metadataProvider.provide(context).forEach { (key, value) ->
                    val fullKey = "${plugin.identity}.$key"
                    put(fullKey, if (isSensitiveMetadataKey(key)) "[REDACTED]" else value)
                }
            }
        }
    }.toMap()

    companion object { val Empty = OrchestrationPluginRegistry(emptyList()) }
}

private data class InstalledOrchestrationPlugin(
    override val identity: String,
    override val serviceFilter: Set<ServiceLocator>,
    override val stages: Set<OrchestrationStage>,
    override val phases: Set<OrchestrationHookPhase>,
    override val lifecycleScopes: Set<OrchestrationLifecycleScope>,
    override val executor: OrchestrationPluginExecutor,
    override val metadataProvider: OrchestrationMetadataProvider,
    override val logMetadataProvider: OrchestrationLogMetadataProvider,
    override val silentLoggingTags: Set<String>,
    override val invocationPolicy: InvocationPolicyPlugin?,
) : OrchestrationPluginInstance {
    override val configurationBundle = OrchestrationPluginConfigurationBundle(
        serviceFilter.toSet(), stages.toSet(), phases.toSet(), lifecycleScopes.toSet(), silentLoggingTags.toSet(),
    )

    override fun reconfigure(config: OrchestrationPluginConfig.() -> Unit): OrchestrationPluginInstance {
        val scope = OrchestrationPluginConfig().apply {
            serviceFilter += this@InstalledOrchestrationPlugin.serviceFilter
            stages += this@InstalledOrchestrationPlugin.stages
            phases += this@InstalledOrchestrationPlugin.phases
            lifecycleScopes += this@InstalledOrchestrationPlugin.lifecycleScopes
            executor = this@InstalledOrchestrationPlugin.executor
            metadataProvider = this@InstalledOrchestrationPlugin.metadataProvider
            logMetadataProvider = this@InstalledOrchestrationPlugin.logMetadataProvider
            silentLoggingTags += this@InstalledOrchestrationPlugin.silentLoggingTags
            invocationPolicy = this@InstalledOrchestrationPlugin.invocationPolicy
        }.apply(config)
        return copy(serviceFilter = scope.serviceFilter.toSet(), stages = scope.stages.toSet(), phases = scope.phases.toSet(),
            lifecycleScopes = scope.lifecycleScopes.toSet(),
            executor = scope.executor, metadataProvider = scope.metadataProvider,
            logMetadataProvider = scope.logMetadataProvider, silentLoggingTags = scope.silentLoggingTags.toSet(),
            invocationPolicy = scope.invocationPolicy)
    }
}

private class PluginDefinition(override val identity: String) : OrchestrationPlugin {
    override fun install(config: OrchestrationPluginConfig.() -> Unit): OrchestrationPluginInstance =
        orchestrationPlugin(identity, config)
}

fun defineOrchestrationPlugin(identity: String): OrchestrationPlugin {
    require(identity.isNotBlank()) { "Plugin identity must not be blank" }
    return PluginDefinition(identity)
}

fun orchestrationPlugin(identity: String, configure: OrchestrationPluginConfig.() -> Unit = {}): OrchestrationPluginInstance {
    require(identity.isNotBlank()) { "Plugin identity must not be blank" }
    val scope = OrchestrationPluginConfig().apply(configure)
    return InstalledOrchestrationPlugin(identity, scope.serviceFilter.toSet(), scope.stages.toSet(), scope.phases.toSet(), scope.lifecycleScopes.toSet(),
        scope.executor, scope.metadataProvider, scope.logMetadataProvider, scope.silentLoggingTags.toSet(), scope.invocationPolicy)
}

/** Safe per-execution mutable state; create once for each run. */
fun newOrchestrationPluginState(): OrchestrationPluginState = OrchestrationPluginState()

/** Informational attempt data delivered once per Aviator transport attempt (including retries). */
data class OrchestrationAttempt(val retry: Boolean, val result: Any? = null, val failure: ExecutionFailure? = null)

private fun isSensitiveMetadataKey(key: String): Boolean {
    val normalized = key.lowercase().filter(Char::isLetterOrDigit)
    return normalized in setOf("authorization", "proxyauthorization", "accesstoken", "refreshtoken", "token",
        "password", "clientsecret", "secret", "apikey", "credential", "cookie", "setcookie") ||
        normalized.endsWith("secret") || normalized.endsWith("password") || normalized.endsWith("token") ||
        normalized.endsWith("apikey")
}

private class OrchestrationDeferredLogger(
    private val delegate: AviatorLogger,
    private val currentMetadata: Map<String, String>,
) : AviatorLogger {
    private fun safeMetadata(metadata: Map<String, String>) = (currentMetadata + metadata).mapValues { (key, value) ->
        if (isSensitiveMetadataKey(key)) "[REDACTED]" else value
    }

    override fun error(message: String, metadata: Map<String, String>) = delegate.error(message, safeMetadata(metadata))
    override fun error(message: String, cause: Throwable, metadata: Map<String, String>) = delegate.error(message, cause, safeMetadata(metadata))
    override fun warn(message: String, metadata: Map<String, String>) = delegate.warn(message, safeMetadata(metadata))
    override fun warn(message: String, cause: Throwable, metadata: Map<String, String>) = delegate.warn(message, cause, safeMetadata(metadata))
    override fun info(message: String, metadata: Map<String, String>) = delegate.info(message, safeMetadata(metadata))
    override fun info(message: String, cause: Throwable, metadata: Map<String, String>) = delegate.info(message, cause, safeMetadata(metadata))
    override fun debug(message: String, metadata: Map<String, String>) = delegate.debug(message, safeMetadata(metadata))
    override fun debug(message: String, cause: Throwable, metadata: Map<String, String>) = delegate.debug(message, cause, safeMetadata(metadata))
    override fun trace(message: String, metadata: Map<String, String>) = delegate.trace(message, safeMetadata(metadata))
    override fun trace(message: String, cause: Throwable, metadata: Map<String, String>) = delegate.trace(message, cause, safeMetadata(metadata))
}

/** Convenient DSL: `install { executor = ... }`. */
fun installOrchestrationPlugins(
    logger: AviatorLogger? = null,
    block: OrchestrationPluginInstallationScope.() -> Unit,
): OrchestrationPluginRegistry =
    OrchestrationPluginRegistry(OrchestrationPluginInstallationScope().apply(block).instances, logger)

@OrchestrationDsl
class OrchestrationPluginInstallationScope internal constructor() {
    internal val instances = mutableListOf<OrchestrationPluginInstance>()
    fun install(plugin: OrchestrationPluginInstance) { instances += plugin }
    fun install(identity: String, configure: OrchestrationPluginConfig.() -> Unit = {}) {
        install(orchestrationPlugin(identity, configure))
    }
}

/** Convenience factory for behavior-changing stages that return a JSON value. */
fun OrchestrationStageContext.replaceInput(value: BodyValue) { input = value }
