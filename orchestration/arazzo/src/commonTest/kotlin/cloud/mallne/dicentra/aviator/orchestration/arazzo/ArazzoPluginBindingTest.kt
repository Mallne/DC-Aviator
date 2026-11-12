package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoBindingStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoRunExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoStepExecution
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.execution.*
import cloud.mallne.dicentra.aviator.core.execution.controlledAviatorCall
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.plugin.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArazzoPluginBindingTest {
    @Test
    fun thrownResolutionAndSelectionHooksBecomeSanitizedRunFailuresWithCleanup() = runTest {
        for (failingStage in listOf(OrchestrationStage.RESOLVE, OrchestrationStage.SELECT)) {
            val locator = ServiceLocator("weather>get")
            val exact = ConstituentIdentity(locator, "provider", "source", "rev", operationId = "forecast")
            var dispatches = 0
            val observed = mutableListOf<OrchestrationStage>()
            val binding = object : OperationBinding<kotlinx.serialization.json.JsonElement> {
                override suspend fun resolve(invocation: CapabilityInvocation) = object : ResolvedOperation {
                    override val invocation = invocation
                }
                override suspend fun invoke(operation: ResolvedOperation,
                    input: kotlinx.serialization.json.JsonElement, scope: InvocationScope): CapabilityResult {
                    dispatches++
                    return CapabilityResult(locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(input)), emptyList())
                }
            }
            val plugins = installOrchestrationPlugins {
                install("throw-$failingStage") {
                    stages += failingStage
                    executor = OrchestrationPluginExecutor { error("private detail at $failingStage") }
                }
                install("observe-unwind") {
                    stages += OrchestrationStage.FAILURE
                    stages += OrchestrationStage.CLEANUP
                    executor = OrchestrationPluginExecutor { observed += it.stage }
                }
            }
            val stepExecutor = ArazzoBindingStepExecutor(binding, listOf(exact), policy, plugins = plugins)
            val runner = ArazzoRunExecutor(stepExecutor, plugins = plugins)

            val result = runner.execute(
                ArazzoWorkflow("weather", listOf(ArazzoStep("step", operationId = "forecast"))),
                WorkflowIdentity("arazzo", "1.1.0", "doc", "rev"), RunId("throw-$failingStage"),
            )

            val failure = assertIs<ExecutionOutcome.Failed>(result.outcome).failure
            assertEquals("operation_failed", failure.code)
            assertEquals("Arazzo step execution failed", failure.message)
            assertEquals(0, dispatches)
            assertEquals(listOf(OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), observed)
        }
    }

    @Test
    fun exactOperationSelectionPluginCanRejectWithoutDispatch() = runTest {
        val locator = ServiceLocator("weather>get")
        val exact = ConstituentIdentity(locator, "provider", "source", "rev", operationId = "forecast")
        var invocations = 0
        val binding = object : OperationBinding<kotlinx.serialization.json.JsonElement> {
            override suspend fun resolve(invocation: CapabilityInvocation) = object : ResolvedOperation {
                override val invocation = invocation
            }
            override suspend fun invoke(operation: ResolvedOperation, input: kotlinx.serialization.json.JsonElement, scope: InvocationScope): CapabilityResult {
                invocations++
                return CapabilityResult(locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(input)), emptyList())
            }
        }
        val plugins = installOrchestrationPlugins {
            install("select-reject") {
                stages += OrchestrationStage.SELECT
                executor = OrchestrationPluginExecutor { it.reject(ExecutionFailure("selection_denied", "Selection denied")) }
            }
        }
        val executor = ArazzoBindingStepExecutor(binding, listOf(exact), policy, plugins = plugins)
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("select-reject"), WorkflowIdentity("arazzo", "1.1.0", "doc", "rev")), ExecutionBudget(),
        )

        val result = executor.execute(ArazzoStep("one", operationId = "forecast"), "{}", scope)

        assertEquals("selection_denied", assertIs<ArazzoStepExecution.Failed>(result).failure.code)
        assertEquals(0, invocations)
    }

    @Test
    fun resolveHookRunsBeforeThenAfterWithNarrowedTarget() = runTest {
        val locator = ServiceLocator("weather>get")
        val exact = ConstituentIdentity(locator, "provider", "source", "rev", operationId = "forecast")
        val observed = mutableListOf<String>()
        val binding = object : OperationBinding<kotlinx.serialization.json.JsonElement> {
            override suspend fun resolve(invocation: CapabilityInvocation) = object : ResolvedOperation {
                override val invocation = invocation
            }
            override suspend fun invoke(operation: ResolvedOperation, input: kotlinx.serialization.json.JsonElement, scope: InvocationScope) =
                CapabilityResult(locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(input)), emptyList())
        }
        val plugins = installOrchestrationPlugins {
            install("resolve-phases") {
                stages += OrchestrationStage.RESOLVE
                phases += OrchestrationHookPhase.BEFORE
                phases += OrchestrationHookPhase.AFTER
                executor = OrchestrationPluginExecutor { context ->
                    val invocation = context.payload as CapabilityInvocation
                    observed += "${context.phase}:${invocation.constituents?.size}"
                }
            }
        }
        val executor = ArazzoBindingStepExecutor(binding, listOf(exact), policy, plugins = plugins)
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("resolve-phases"), WorkflowIdentity("arazzo", "1.1.0", "doc", "rev")), ExecutionBudget(),
        )

        assertIs<ArazzoStepExecution.Completed>(executor.execute(ArazzoStep("one", operationId = "forecast"), "{}", scope))
        assertEquals(listOf("BEFORE:1", "AFTER:1"), observed)
    }

    @Test
    fun bindingAdapterForwardsRealTransportAttemptsToTheInstalledPluginRegistry() = runTest {
        val locator = ServiceLocator("weather>get")
        val exact = ConstituentIdentity(locator, "provider", "source", "rev", operationId = "forecast")
        val observed = mutableListOf<Pair<OrchestrationStage, Boolean>>()
        val plugins = installOrchestrationPlugins {
            install("attempts") {
                stages += OrchestrationStage.BEFORE_ATTEMPT
                stages += OrchestrationStage.AFTER_ATTEMPT
                lifecycleScopes += OrchestrationLifecycleScope.ATTEMPT
                executor = OrchestrationPluginExecutor { context ->
                    if (context.stage == OrchestrationStage.BEFORE_ATTEMPT || context.stage == OrchestrationStage.AFTER_ATTEMPT) {
                        observed += context.stage to (context.payload as OrchestrationAttempt).retry
                    }
                }
            }
        }
        val binding = object : OperationBinding<kotlinx.serialization.json.JsonElement> {
            override suspend fun resolve(invocation: CapabilityInvocation) = object : ResolvedOperation {
                override val invocation = invocation
            }
            override suspend fun invoke(operation: ResolvedOperation, input: kotlinx.serialization.json.JsonElement, scope: InvocationScope): CapabilityResult {
                controlledAviatorCall(call = { Unit })
                controlledAviatorCall(retry = true, call = { Unit })
                return CapabilityResult(locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(input)), emptyList())
            }
        }
        val executor = ArazzoBindingStepExecutor(binding, listOf(exact), policy, plugins = plugins)
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("attempt-forward"), WorkflowIdentity("arazzo", "1.1.0", "doc", "rev")), ExecutionBudget(),
        )

        assertIs<ArazzoStepExecution.Completed>(executor.execute(ArazzoStep("one", operationId = "forecast"), "{}", scope))
        assertEquals(listOf(
            OrchestrationStage.BEFORE_ATTEMPT to false,
            OrchestrationStage.AFTER_ATTEMPT to false,
            OrchestrationStage.BEFORE_ATTEMPT to true,
            OrchestrationStage.AFTER_ATTEMPT to true,
        ), observed)
    }

    @Test
    fun resolveHookCannotWidenExactOperationTarget() = runTest {
        val locator = ServiceLocator("weather>get")
        val exact = ConstituentIdentity(locator, "provider", "source", "rev", operationId = "forecast")
        val other = exact.copy(operationId = "other")
        var invocations = 0
        val binding = object : OperationBinding<kotlinx.serialization.json.JsonElement> {
            override suspend fun resolve(invocation: CapabilityInvocation): ResolvedOperation = object : ResolvedOperation {
                override val invocation = invocation
            }
            override suspend fun invoke(
                operation: ResolvedOperation,
                input: kotlinx.serialization.json.JsonElement,
                scope: InvocationScope,
            ): CapabilityResult {
                invocations++
                return CapabilityResult(locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(input)), emptyList())
            }
        }
        val plugins = installOrchestrationPlugins {
            install("widen") {
                stages += OrchestrationStage.RESOLVE
                executor = OrchestrationPluginExecutor { context ->
                    val original = context.payload as CapabilityInvocation
                    context.payload = original.copy(constituents = setOf(other))
                }
            }
        }
        val stepExecutor = ArazzoBindingStepExecutor(binding, listOf(exact, other), policy, plugins = plugins)
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("arazzo", "1.1.0", "doc", "rev")), ExecutionBudget(),
        )

        val result = stepExecutor.execute(ArazzoStep("one", operationId = "forecast"), "{}", scope)

        assertEquals(0, invocations)
        assertEquals("invalid_plugin_target", assertIs<ArazzoStepExecution.Failed>(result).failure.code)
    }

    private val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
        PartialFailurePolicy.ALLOW_PARTIAL, EmptySelectionPolicy.FAIL)
}
