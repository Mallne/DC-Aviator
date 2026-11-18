package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.client.mock.MockClientDefinition
import cloud.mallne.dicentra.aviator.client.mock.MockConverter
import cloud.mallne.dicentra.aviator.client.mock.MockEngineTemplate
import cloud.mallne.dicentra.aviator.client.mock.MockExecutionContext
import cloud.mallne.dicentra.aviator.core.engine.AviatorEngine
import cloud.mallne.dicentra.aviator.core.engine.OpenApiClientInput
import cloud.mallne.dicentra.aviator.core.engine.DefinitionCoordinator
import cloud.mallne.dicentra.aviator.core.engine.EngineDefinitionAuthorization
import cloud.mallne.dicentra.aviator.core.engine.EngineAuthorizationDecision
import cloud.mallne.dicentra.aviator.core.engine.InMemoryDefinitionCoordinator
import cloud.mallne.dicentra.aviator.core.engine.OutboundTargetPolicy
import cloud.mallne.dicentra.aviator.core.engine.InvocationMode
import cloud.mallne.dicentra.aviator.core.plugins.DefaultAviatorPluginRegistry
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.arazzo
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoEngineStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.resolveArazzoEngineAction
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.arazzoDocument
import cloud.mallne.dicentra.aviator.orchestration.execution.*
import cloud.mallne.dicentra.aviator.orchestration.execution.OrchestrationPluginScope
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import cloud.mallne.dicentra.aviator.plugin.orchestration.budget.ExecutionBudgetPlugin
import cloud.mallne.dicentra.aviator.core.plugins.PluginActivation
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationStage
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginExecutor
import cloud.mallne.dicentra.aviator.orchestration.plugin.orchestrationPlugin
import cloud.mallne.dicentra.aviator.orchestration.execution.OrchestrationPluginRegistration
import io.ktor.openapi.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineWorkflowIntegrationTest {
    private val workflowLocator = ServiceLocator("workflow")
    private val actionLocator = ServiceLocator("action")
    private val identity = WorkflowIdentity("arazzo", "1.1.0", "workflow", "1")

    @Test
    fun workflowDispatchesPlainOpenApiActionThroughEngineAndClientPipeline() = runTest {
        val calls = mutableListOf<Int>()
        val engine = engine(calls)
        val result = engine.invoke(
            workflowLocator, OrchestrationEngineTemplate, WorkflowInput(), InvocationMode.FIRST,
        ).single().result
        assertTrue(result.isSuccess, (result.exceptionOrNull() as? WorkflowInvocationException)?.execution.toString())
        val outcome = result.getOrThrow()
        assertIs<ExecutionOutcome.Succeeded>(outcome.outcome)
        assertEquals(listOf(200), calls)
    }

    @Test
    fun engineStepResolutionAndSelectionHooksCanVetoDispatch() = runTest {
        for (stage in listOf(OrchestrationStage.RESOLVE, OrchestrationStage.SELECT)) {
            val calls = mutableListOf<Int>()
            val observed = mutableListOf<String>()
            val plugin = orchestrationPlugin("reject-$stage") {
                stages += stage
                phases += cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationHookPhase.BEFORE
                phases += cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationHookPhase.AFTER
                executor = OrchestrationPluginExecutor { context ->
                    observed += context.phase.name
                    if (context.phase ==
                        cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationHookPhase.AFTER
                    ) context.reject(ExecutionFailure("target_denied", "Target denied"))
                }
            }
            val outcome = engine(
                calls, orchestrationPlugins = listOf(OrchestrationPluginRegistration(plugin, PluginActivation.PASSIVE)),
            ).invoke(workflowLocator, OrchestrationEngineTemplate, WorkflowInput(), InvocationMode.FIRST).single()
            val failure = assertIs<WorkflowInvocationException>(outcome.result.exceptionOrNull())
            assertEquals("target_denied", assertIs<ExecutionOutcome.Failed>(failure.execution.outcome).failure.code)
            assertEquals(listOf("BEFORE", "AFTER"), observed)
            assertTrue(calls.isEmpty())
        }
    }

    @Test
    fun engineStepHooksCannotWidenOrReplaceTheResolvedTarget() = runTest {
        for (stage in listOf(OrchestrationStage.RESOLVE, OrchestrationStage.SELECT)) {
            val calls = mutableListOf<Int>()
            val plugin = orchestrationPlugin("widen-$stage") {
                stages += stage
                executor = OrchestrationPluginExecutor { context ->
                    val original = context.payload as CapabilityInvocation
                    val target = checkNotNull(original.constituents).single()
                    context.payload = original.copy(constituents = setOf(target.copy(sourceId = "other-source")))
                }
            }
            val outcome = engine(
                calls, orchestrationPlugins = listOf(OrchestrationPluginRegistration(plugin, PluginActivation.PASSIVE)),
            ).invoke(workflowLocator, OrchestrationEngineTemplate, WorkflowInput(), InvocationMode.FIRST).single()
            val failure = assertIs<WorkflowInvocationException>(outcome.result.exceptionOrNull())
            assertEquals("invalid_plugin_target", assertIs<ExecutionOutcome.Failed>(failure.execution.outcome).failure.code)
            assertTrue(calls.isEmpty())
        }
    }

    @Test
    fun workflowAuthorizationDenialPreventsClientExecution() = runTest {
        val calls = mutableListOf<Int>()
        val engine = engine(calls, deny = true)
        val outcome = engine.invoke(
            workflowLocator, OrchestrationEngineTemplate, WorkflowInput(), InvocationMode.FIRST,
        ).single()
        val failure = assertIs<WorkflowInvocationException>(outcome.result.exceptionOrNull())
        assertIs<ExecutionOutcome.Failed>(failure.execution.outcome)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun engineAuthorizationDenialPreventsWorkflowClientDispatch() = runTest {
        val calls = mutableListOf<Int>()
        val outcome = engine(calls, denyClient = true)
            .invoke(workflowLocator, OrchestrationEngineTemplate, WorkflowInput(), InvocationMode.FIRST).single()

        assertIs<WorkflowInvocationException>(outcome.result.exceptionOrNull())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun callBudgetStillAppliesAtClientTransportBoundary() = runTest {
        val calls = mutableListOf<Int>()
        val engine = engine(calls)
        val outcome = engine.invoke(
            workflowLocator, OrchestrationEngineTemplate,
            WorkflowInput(budget = ExecutionBudget(maxCalls = 0)), InvocationMode.FIRST,
        ).single()
        val failure = assertIs<WorkflowInvocationException>(outcome.result.exceptionOrNull())
        assertIs<ExecutionOutcome.Failed>(failure.execution.outcome)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun engineSuppliedTypedOrchestrationRegistryRunsForWorkflow() = runTest {
        val calls = mutableListOf<Int>()
        val observed = mutableListOf<String>()
        var reconfigurations = 0
        val plugin = orchestrationPlugin("engine-snapshot") {
            stages += OrchestrationStage.COMPLETE
            executor = OrchestrationPluginExecutor { observed += "engine-registry" }
        }

        val outcome = engine(
            calls,
            orchestrationPlugins = listOf(
                OrchestrationPluginRegistration(plugin, PluginActivation.DEFINITION) { configuration ->
                    assertEquals(kotlinx.serialization.json.JsonPrimitive(true), configuration)
                    reconfigurations++
                    plugin
                },
            ),
            workflowPluginConfiguration = mapOf(
                plugin.identity to kotlinx.serialization.json.JsonPrimitive(true),
            ),
        )
            .invoke(
                workflowLocator, OrchestrationEngineTemplate, WorkflowInput(), InvocationMode.FIRST,
            ).single().result.getOrThrow()

        assertIs<ExecutionOutcome.Succeeded>(outcome.outcome)
        assertEquals(1, reconfigurations)
        assertEquals(listOf("engine-registry"), observed)
        assertEquals(listOf(200), calls)
    }

    @Test
    fun engineRegistryAttemptHookCanRejectBeforeClientExecution() = runTest {
        val calls = mutableListOf<Int>()
        val observed = mutableListOf<OrchestrationStage>()
        val plugin = orchestrationPlugin("attempt-veto") {
            stages += setOf(OrchestrationStage.BEFORE_ATTEMPT, OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP)
            lifecycleScopes += cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope.ATTEMPT
            executor = OrchestrationPluginExecutor { context ->
                observed += context.stage
                if (context.stage == OrchestrationStage.BEFORE_ATTEMPT) {
                    context.reject(ExecutionFailure("attempt_denied", "Rejected by attempt hook"))
                }
            }
        }
        val outcome = engine(
            calls,
            orchestrationPlugins = listOf(OrchestrationPluginRegistration(plugin, PluginActivation.PASSIVE)),
        ).invoke(workflowLocator, OrchestrationEngineTemplate, WorkflowInput(), InvocationMode.FIRST).single()

        val failure = assertIs<WorkflowInvocationException>(outcome.result.exceptionOrNull())
        assertEquals("attempt_denied", assertIs<ExecutionOutcome.Failed>(failure.execution.outcome).failure.code)
        assertEquals(
            listOf(OrchestrationStage.BEFORE_ATTEMPT, OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP),
            observed,
        )
        assertTrue(calls.isEmpty())
    }

    @Test
    fun engineRegistrySharesPluginStateAcrossRunAndAttemptHooks() = runTest {
        val calls = mutableListOf<Int>()
        val observed = mutableListOf<OrchestrationStage>()
        val plugin = orchestrationPlugin("attempt-lifecycle") {
            stages += setOf(
                OrchestrationStage.PREPARE, OrchestrationStage.BEFORE_ATTEMPT,
                OrchestrationStage.AFTER_ATTEMPT, OrchestrationStage.CLEANUP,
            )
            executor = OrchestrationPluginExecutor { context ->
                if (context.stage == OrchestrationStage.PREPARE) context.state.put("prepared", true)
                if (context.lifecycleScope ==
                    cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationLifecycleScope.ATTEMPT
                ) {
                    assertEquals(true, context.state.get("prepared"))
                    observed += context.stage
                }
            }
        }
        val outcome = engine(
            calls,
            orchestrationPlugins = listOf(OrchestrationPluginRegistration(plugin, PluginActivation.PASSIVE)),
        ).invoke(workflowLocator, OrchestrationEngineTemplate, WorkflowInput(), InvocationMode.FIRST)
            .single().result.getOrThrow()

        assertIs<ExecutionOutcome.Succeeded>(outcome.outcome)
        assertEquals(
            listOf(OrchestrationStage.BEFORE_ATTEMPT, OrchestrationStage.AFTER_ATTEMPT, OrchestrationStage.CLEANUP),
            observed,
        )
        assertEquals(listOf(200), calls)
    }

    private fun engine(
        calls: MutableList<Int>,
        deny: Boolean = false,
        denyClient: Boolean = false,
        orchestrationPlugins: List<OrchestrationPluginRegistration> = emptyList(),
        workflowPluginConfiguration: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ): AviatorEngine {
        val api = OpenApiDoc(
            info = OpenApiInfo(title = "plain", version = "1"),
            servers = listOf(Server(url = "https://mock.test")),
            paths = mapOf("/weather" to ReferenceOr.Value(PathItem(get = Operation(
                operationId = "weather", responses = Responses(),
            )))),
        )
        val service = MockConverter().buildOperation(api, api.routes().single(), actionLocator)
        val clientDefinition = MockClientDefinition(service)
        val constituent = ConstituentIdentity(actionLocator, "provider", "source", "rev", "weather")
        val workflow = arazzoDocument("Weather", "1") {
            source("weather", "urn:test:weather")
            workflow("weather-workflow") { step("first", "weather") }
        }.workflows.single()
        val engineOrchestrationPlugins =
            listOf(
                OrchestrationPluginRegistration(ExecutionBudgetPlugin.install(), PluginActivation.PASSIVE),
            ) + orchestrationPlugins
        var definitions = emptyList<cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition>()
        val engine: AviatorEngine = AviatorEngine(
            DefinitionCoordinator { locator -> definitions.filter { it.locator == locator } },
            EngineDefinitionAuthorization { context -> if (denyClient && context.locator == actionLocator) EngineAuthorizationDecision.Denied("Denied") else EngineAuthorizationDecision.Allowed },
             OutboundTargetPolicy { _, _, _ -> null },
             plugins = DefaultAviatorPluginRegistry(
                 scopeProviders = listOf(OrchestrationPluginScope.provider(engineOrchestrationPlugins)),
             ),
        )
        val orchestrator = orchestrator {
            arazzo {
                this.workflow = { requested -> if (requested == identity) workflow else null }
                authorize = { if (deny) ExecutionFailure("denied", "Denied") else null }
                policy = CapabilityPolicy(
                    ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                    PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL,
                )
                stepExecutor = {
                    ArazzoEngineStepExecutor(
                        engine = engine,
                        policy = checkNotNull(policy),
                        resolveAction = { step, inputs ->
                            resolveArazzoEngineAction(
                                step = step,
                                inputs = inputs,
                                operationIndex = listOf(constituent),
                                policy = checkNotNull(policy),
                                 template = MockEngineTemplate,
                                 matchesDefinition = { requested, definition ->
                                     requested == constituent && definition === clientDefinition
                                 },
                                 inputFor = { _, _, _ -> OpenApiClientInput() },
                                 mapResult = { value ->
                                     val context = value
                                     val response = checkNotNull(context.networkChain.single().response)
                                     calls += response.status.value
                                     val body = BodyValue.BytesValue(checkNotNull(response.content).toList())
                                     CapabilityResult(
                                         actionLocator,
                                         CapabilityOutcome.Succeeded(body),
                                         listOf(ConstituentResult(
                                             constituent,
                                             ConstituentOutcome.Succeeded(body),
                                             ResponseSnapshot(body, response.contentType.toString(), HttpResponseMetadata(response.status.value)),
                                         )),
                                     )
                                 },
                             )
                         },
                     )
                }
                newRunId = { RunId("test-run") }
            }
        }
        definitions = listOf(
            WorkflowDefinition(workflowLocator, identity, orchestrator, workflowPluginConfiguration),
            clientDefinition,
        )
        return engine
    }
}
