package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.orchestration.plugin.*
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import cloud.mallne.dicentra.aviator.core.execution.logging.AviatorLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertNotSame
import kotlinx.coroutines.launch

class OrchestrationPluginTest {
    @Test
    fun installAndLookupRejectsDuplicateIdentity() {
        val plugin = orchestrationPlugin("transform")
        assertEquals("transform", OrchestrationPluginRegistry(listOf(plugin)).get("transform")?.identity)
        assertFailsWith<IllegalArgumentException> { OrchestrationPluginRegistry(listOf(plugin, plugin)) }
    }

    @Test
    fun configurationSnapshotFilteringAndMetadataRedactionAreDeterministic() {
        val config = OrchestrationPluginConfig().apply {
            serviceFilter += ServiceLocator("target")
            stages += OrchestrationStage.COMPLETE
            metadataProvider = OrchestrationMetadataProvider { mapOf("apiKey" to "secret", "trace" to "t-1") }
        }
        val plugin = orchestrationPlugin("telemetry") { 
            serviceFilter += config.serviceFilter
            stages += config.stages
            metadataProvider = config.metadataProvider
        }
        config.stages.clear()
        val registry = OrchestrationPluginRegistry(listOf(plugin))
        val invocation = CapabilityInvocation(ServiceLocator("target"), CapabilityPolicy(
            ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
            PartialFailurePolicy.ALLOW_PARTIAL, EmptySelectionPolicy.FAIL,
        ))
        val context = OrchestrationStageContext(OrchestrationStage.COMPLETE, invocation,
            TestScope, OrchestrationPluginState())

        assertEquals(mapOf("telemetry.apiKey" to "[REDACTED]", "telemetry.trace" to "t-1"), registry.metadata(context))
        val excluded = OrchestrationStageContext(OrchestrationStage.PREPARE, invocation,
            TestScope, OrchestrationPluginState())
        assertEquals(emptyMap(), registry.metadata(excluded))
        assertNull(registry.get("missing"))
    }

    @Test
    fun reconfigurationKeepsInstalledRegistrySnapshotAndUpdatesTargetFilter() = kotlinx.coroutines.test.runTest {
        val targetA = ServiceLocator("weather>gather")
        val targetB = ServiceLocator("weather>update")
        val observed = mutableListOf<String>()
        val original = orchestrationPlugin("snapshot") {
            serviceFilter += targetA
            stages += OrchestrationStage.COMPLETE
            executor = OrchestrationPluginExecutor { observed += "original" }
        }
        val originalRegistry = OrchestrationPluginRegistry(listOf(original))
        val reconfigured = original.reconfigure {
            serviceFilter.clear()
            serviceFilter += targetB
            executor = OrchestrationPluginExecutor { observed += "reconfigured" }
        }
        val updatedRegistry = OrchestrationPluginRegistry(listOf(reconfigured))
        fun context(target: ServiceLocator) = OrchestrationStageContext(
            OrchestrationStage.COMPLETE,
            invocation = CapabilityInvocation(target, CapabilityPolicy(
                ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.ALLOW_PARTIAL, EmptySelectionPolicy.FAIL,
            )),
            scope = TestScope,
            state = OrchestrationPluginState(),
        )

        originalRegistry.execute(context(targetA))
        originalRegistry.execute(context(targetB))
        updatedRegistry.execute(context(targetA))
        updatedRegistry.execute(context(targetB))

        assertEquals(listOf("original", "reconfigured"), observed)
        assertEquals(setOf(targetA), original.configurationBundle.serviceFilter)
        assertEquals(setOf(targetB), reconfigured.configurationBundle.serviceFilter)
    }

    @Test
    fun pluginLogUsesHostLoggerAndAggregatesMetadataWithSuppression() = kotlinx.coroutines.test.runTest {
        val logger = RecordingLogger()
        val registry = installOrchestrationPlugins(logger) {
            install("trace-context") {
                logMetadataProvider = OrchestrationLogMetadataProvider { tag, _ ->
                    assertEquals(true, tag in setOf("example.work", "orchestration.lifecycle"))
                    mapOf("trace.id" to "trace-123", "provider.marker" to "present", "api.token" to "must-redact")
                }
            }
            install("example") {
                stages += OrchestrationStage.COMPLETE
                silentLoggingTags += "example.silent"
                executor = OrchestrationPluginExecutor { context ->
                    context.log("example.work") {
                        info("plugin work", mapOf("operation" to "completed", "trace.id" to "call-override"))
                    }
                    context.log("example.silent") { info("must not be logged") }
                }
            }
        }

        registry.execute(OrchestrationStageContext(
            OrchestrationStage.COMPLETE, scope = TestScope, state = OrchestrationPluginState(),
        ))

        assertEquals(listOf("info", "info"), logger.levels)
        assertEquals("COMPLETE", logger.metadata.last()["orchestration.stage"])
        assertEquals("call-override", logger.metadata.last()["trace.id"])
        assertEquals("present", logger.metadata.last()["provider.marker"])
        assertEquals("[REDACTED]", logger.metadata.last()["api.token"])
        assertEquals("completed", logger.metadata.last()["operation"])
    }

    @Test
    fun lifecycleLoggingIsProvidedByCoreAndHonorsSilentTagSuppression() = kotlinx.coroutines.test.runTest {
        val logger = RecordingLogger()
        val registry = installOrchestrationPlugins(logger = logger) {
            install("silent-lifecycle") {
                silentLoggingTags += "orchestration.lifecycle"
            }
        }

        registry.execute(OrchestrationStageContext(
            OrchestrationStage.MAP_INPUT, scope = TestScope, state = OrchestrationPluginState(),
        ))
        registry.execute(OrchestrationStageContext(
            OrchestrationStage.FAILURE, scope = TestScope, state = OrchestrationPluginState(),
            payload = ExecutionFailure("failure.code", "sensitive details"),
        ))

        assertEquals(emptyList(), logger.levels)
        assertFalse(logger.metadata.toString().contains("sensitive details"))

        val unsuppressedLogger = RecordingLogger()
        val unsuppressedRegistry = installOrchestrationPlugins(logger = unsuppressedLogger) { }
        unsuppressedRegistry.execute(OrchestrationStageContext(
            OrchestrationStage.FAILURE, scope = TestScope, state = OrchestrationPluginState(),
            payload = ExecutionFailure("failure.code", "sensitive details"),
        ))
        assertEquals(listOf("warn"), unsuppressedLogger.levels)
        assertEquals("failure.code", unsuppressedLogger.metadata.single()["failure.code"])
        assertFalse(unsuppressedLogger.metadata.toString().contains("sensitive details"))
    }

    @Test
    fun pluginInstancesAreNamespacedAndUnwindInReverseOrder() = kotlinx.coroutines.test.runTest {
        val order = mutableListOf<String>()
        val first = orchestrationPlugin("first") {
            stages += OrchestrationStage.COMPLETE
            executor = OrchestrationPluginExecutor { context ->
                context.state.put("value", "first")
                order += "first:${context.state.get("value")}"
            }
        }
        val second = orchestrationPlugin("second") {
            stages += OrchestrationStage.COMPLETE
            executor = OrchestrationPluginExecutor { context ->
                context.state.put("value", "second")
                order += "second:${context.state.get("value")}"
            }
        }
        val registry = OrchestrationPluginRegistry(listOf(first, second))
        val context = OrchestrationStageContext(OrchestrationStage.COMPLETE, state = OrchestrationPluginState())

        registry.execute(context)

        assertEquals(listOf("second:second", "first:first"), order)
        val updated = first.reconfigure { stages += OrchestrationStage.FAILURE }
        assertEquals(setOf(OrchestrationStage.COMPLETE, OrchestrationStage.FAILURE), updated.configurationBundle.stages)
        assertEquals(emptySet(), updated.reconfigure { stages.clear() }.configurationBundle.stages)
    }

    @Test
    fun explicitHookPhasesFilterAndOrderForwardAndUnwindCallbacks() = kotlinx.coroutines.test.runTest {
        val order = mutableListOf<String>()
        val registry = installOrchestrationPlugins {
            install("first") {
                stages += OrchestrationStage.MAP_INPUT
                phases += OrchestrationHookPhase.BEFORE
                phases += OrchestrationHookPhase.AFTER
                executor = OrchestrationPluginExecutor { order += "first-${it.phase}" }
            }
            install("second") {
                stages += OrchestrationStage.MAP_INPUT
                phases += OrchestrationHookPhase.BEFORE
                phases += OrchestrationHookPhase.AFTER
                executor = OrchestrationPluginExecutor { order += "second-${it.phase}" }
            }
        }
        val state = OrchestrationPluginState()

        registry.execute(OrchestrationStageContext(OrchestrationStage.MAP_INPUT,
            phase = OrchestrationHookPhase.BEFORE, state = state))
        registry.execute(OrchestrationStageContext(OrchestrationStage.MAP_INPUT,
            phase = OrchestrationHookPhase.AFTER, state = state))

        assertEquals(listOf("first-BEFORE", "second-BEFORE", "second-AFTER", "first-AFTER"), order)
        assertEquals(setOf(OrchestrationHookPhase.BEFORE, OrchestrationHookPhase.AFTER),
            registry.get("first")?.configurationBundle?.phases)
    }

    @Test
    fun rejectionStopsForwardHooksAndUnwindStillVisitsEveryPlugin() = kotlinx.coroutines.test.runTest {
        val visited = mutableListOf<String>()
        val registry = installOrchestrationPlugins {
            install("first") {
                stages += OrchestrationStage.SELECT
                stages += OrchestrationStage.CLEANUP
                executor = OrchestrationPluginExecutor { context ->
                    visited += "first:${context.stage}"
                    if (context.stage == OrchestrationStage.SELECT) context.reject(ExecutionFailure("blocked", "Blocked"))
                }
            }
            install("second") {
                stages += OrchestrationStage.SELECT
                stages += OrchestrationStage.CLEANUP
                executor = OrchestrationPluginExecutor { visited += "second:${it.stage}" }
            }
        }
        val context = OrchestrationStageContext(OrchestrationStage.SELECT, state = OrchestrationPluginState())

        registry.execute(context)
        registry.execute(OrchestrationStageContext(OrchestrationStage.CLEANUP, state = OrchestrationPluginState()))

        assertEquals(ExecutionFailure("blocked", "Blocked"), context.rejection)
        assertEquals(listOf("first:SELECT", "second:CLEANUP", "first:CLEANUP"), visited)
    }

    @Test
    fun cleanupAggregatesFailuresInsteadOfSkippingLaterHooks() = kotlinx.coroutines.test.runTest {
        val visited = mutableListOf<String>()
        val registry = installOrchestrationPlugins {
            install("first") {
                stages += OrchestrationStage.CLEANUP
                executor = OrchestrationPluginExecutor { visited += "first"; error("cleanup-first") }
            }
            install("second") {
                stages += OrchestrationStage.CLEANUP
                executor = OrchestrationPluginExecutor { visited += "second"; error("cleanup-second") }
            }
        }

        val failure = kotlin.test.assertFailsWith<IllegalStateException> {
            registry.execute(OrchestrationStageContext(OrchestrationStage.CLEANUP, state = OrchestrationPluginState()))
        }

        assertEquals(listOf("second", "first"), visited)
        assertEquals("cleanup-second", failure.message)
        assertEquals(1, failure.suppressedExceptions.size)
    }

    @Test
    fun pluginStateIsSharedByRunChildrenButNotAcrossRuns() = kotlinx.coroutines.test.runTest {
        fun scope(id: String) = EnforcedInvocationScope(
            ExecutionMetadata(RunId(id), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        )
        val first = scope("first")
        val childState = first.forStep("step").pluginState("orchestration")
        val rootState = first.pluginState("orchestration")
        val otherRun = scope("other").pluginState("orchestration")

        assertSame(rootState, childState)
        assertNotSame(rootState, otherRun)
    }

    @Test
    fun lifecycleScopeFilterDistinguishesRunAndBindingCleanup() = kotlinx.coroutines.test.runTest {
        var calls = 0
        val registry = installOrchestrationPlugins {
            install("run-resource") {
                stages += OrchestrationStage.CLEANUP
                lifecycleScopes += OrchestrationLifecycleScope.RUN
                executor = OrchestrationPluginExecutor { calls++ }
            }
        }
        val state = OrchestrationPluginState()
        registry.execute(OrchestrationStageContext(OrchestrationStage.CLEANUP, state = state,
            lifecycleScope = OrchestrationLifecycleScope.BINDING))
        registry.execute(OrchestrationStageContext(OrchestrationStage.CLEANUP, state = state,
            lifecycleScope = OrchestrationLifecycleScope.RUN))

        assertEquals(1, calls)
    }

    @Test
    fun unsupportedStageRegistrationFailsDuringEngineConfiguration() {
        val registry = installOrchestrationPlugins {
            install("a-razzo-stage") { stages += OrchestrationStage.RESOLVE }
        }
        assertFailsWith<IllegalArgumentException> {
            registry.requireSupportedBy(OrchestrationEngineStageCapabilities("simple-engine", setOf(OrchestrationStage.PREPARE)))
        }
        registry.requireSupportedBy(OrchestrationEngineStageCapabilities("arazzo-binding", setOf(OrchestrationStage.RESOLVE)))
    }

    @Test
    fun pluginStateUpdateIsAtomicAcrossConcurrentChildren() = kotlinx.coroutines.test.runTest {
        val state = EnforcedInvocationScope(
            ExecutionMetadata(RunId("concurrent"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        ).pluginState("orchestration")
        val workers = List(100) {
            launch {
                state.update("counter") { previous -> ((previous as? Int ?: 0) + 1) to Unit }
            }
        }

        workers.forEach { it.join() }

        assertEquals(100, state.get("counter"))
    }

    private object TestScope : InvocationScope {
        private val states = mutableMapOf<String, OrchestrationPluginState>()
        override val metadata = ExecutionMetadata(RunId("plugin"), WorkflowIdentity("test", "1", "source", "rev"))
        override val budget = cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget()
        override suspend fun pluginState(owner: String) = states.getOrPut(owner) { OrchestrationPluginState() }
        override suspend fun authorize(identity: ConstituentIdentity): ExecutionFailure? = null
        override suspend fun acquireCall(identity: ConstituentIdentity, retry: Boolean): ExecutionFailure? = null
        override suspend fun releaseCall(identity: ConstituentIdentity) = Unit
        override suspend fun checkpoint(transitions: Int, nestingDepth: Int): ExecutionFailure? = null
    }

    private class RecordingLogger : AviatorLogger {
        val levels = mutableListOf<String>()
        val metadata = mutableListOf<Map<String, String>>()

        private fun record(level: String, values: Map<String, String>) {
            levels += level
            metadata += values
        }

        override fun error(message: String, metadata: Map<String, String>) = record("error", metadata)
        override fun error(message: String, cause: Throwable, metadata: Map<String, String>) = record("error", metadata)
        override fun warn(message: String, metadata: Map<String, String>) = record("warn", metadata)
        override fun warn(message: String, cause: Throwable, metadata: Map<String, String>) = record("warn", metadata)
        override fun info(message: String, metadata: Map<String, String>) = record("info", metadata)
        override fun info(message: String, cause: Throwable, metadata: Map<String, String>) = record("info", metadata)
        override fun debug(message: String, metadata: Map<String, String>) = record("debug", metadata)
        override fun debug(message: String, cause: Throwable, metadata: Map<String, String>) = record("debug", metadata)
        override fun trace(message: String, metadata: Map<String, String>) = record("trace", metadata)
        override fun trace(message: String, cause: Throwable, metadata: Map<String, String>) = record("trace", metadata)
    }
}
