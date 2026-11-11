package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.orchestration.execution.OperationBinding
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.Orchestrator
import cloud.mallne.dicentra.aviator.orchestration.execution.ResolvedOperation
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionRequest
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionResult
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionEventSink
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityCheck
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityRequirements
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionMode
import cloud.mallne.dicentra.aviator.orchestration.policy.OrchestrationCapability
import cloud.mallne.dicentra.aviator.orchestration.policy.OrchestratorCapabilities
import cloud.mallne.dicentra.aviator.orchestration.policy.WorkflowFormatCapability
import cloud.mallne.dicentra.aviator.orchestration.policy.check
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class OrchestrationContractTest {
    @Test
    fun workflowIdentityIsIndependentFromRunIdentity() {
        val workflow = workflowIdentity()
        val first = ExecutionResult(RunId("run-1"), workflow, ExecutionOutcome.Succeeded())
        val second = ExecutionResult(RunId("run-2"), workflow, ExecutionOutcome.Succeeded())

        assertEquals(first.workflow, second.workflow)
        assertNotEquals(first.runId, second.runId)
    }

    @Test
    fun capabilityPreflightReportsUnsupportedFormatVersionModeAndFeatures() {
        val capabilities = OrchestratorCapabilities(
            formats = setOf(WorkflowFormatCapability("arazzo", setOf("1.0"))),
            executionModes = setOf(ExecutionMode.IN_PROCESS),
        )

        val result = capabilities.check(
            CapabilityRequirements(
                format = "unknown",
                formatVersion = "2.0",
                features = setOf(OrchestrationCapability("conditionals")),
                executionMode = ExecutionMode.REMOTE,
            ),
        )

        val unsupported = assertIs<CapabilityCheck.Unsupported>(result)
        assertEquals(2, unsupported.reasons.size) // Unknown format; mode is also checked.
        assertTrue(unsupported.reasons.any { "format 'unknown'" in it })
        assertTrue(unsupported.reasons.any { "REMOTE" in it })
    }

    @Test
    fun capabilityPreflightAcceptsSupportedFormatAndFeature() {
        val feature = OrchestrationCapability("status-criteria", "1")
        val capabilities = OrchestratorCapabilities(
            formats = setOf(WorkflowFormatCapability("arazzo", setOf("1.1"), setOf(feature))),
            executionModes = setOf(ExecutionMode.IN_PROCESS),
        )

        assertEquals(
            CapabilityCheck.Supported,
            capabilities.check(CapabilityRequirements("arazzo", "1.1", setOf(feature))),
        )
    }

    @Test
    fun capabilityPreflightRejectsKnownFormatWithUnsupportedVersionAndFeature() {
        val capabilities = OrchestratorCapabilities(
            formats = setOf(WorkflowFormatCapability("arazzo", setOf("1.1"))),
            executionModes = setOf(ExecutionMode.IN_PROCESS),
        )
        val result = assertIs<CapabilityCheck.Unsupported>(capabilities.check(
            CapabilityRequirements("arazzo", "2.0", setOf(OrchestrationCapability("conditionals"))),
        ))
        assertEquals(2, result.reasons.size)
        assertTrue(result.reasons.any { "version '2.0'" in it })
        assertTrue(result.reasons.any { "conditionals" in it })
    }

    @Test
    fun failedOutcomeCanExposePartialCompletionWithoutRollbackClaim() {
        val result = ExecutionResult(
            RunId("run-3"),
            workflowIdentity(),
            ExecutionOutcome.Failed(
                failure = ExecutionFailure("operation_failed", "Provider returned an error", stepId = "step-2"),
                completedSteps = listOf("step-1"),
            ),
        )

        val failure = assertIs<ExecutionOutcome.Failed>(result.outcome)
        assertEquals(listOf("step-1"), failure.completedSteps)
        assertEquals("step-2", failure.failure.stepId)
    }

    @Test
    fun locatorBindingResolvesSeparatelyAndReturnsMultipleResultsWithInvocationMetadata() {
        runSuspend {
            val binding = RecordingBinding()
            val target = ServiceLocator("weather>gather")
            val resolved = binding.resolve(CapabilityInvocation(target, contractPolicy()))

            assertEquals(target, binding.resolvedTarget)
            assertFalse(binding.invoked)

            val metadata = ExecutionMetadata(RunId("run-4"), workflowIdentity(), "step-1", mapOf("trace.id" to "abc"))
            val result = binding.invoke(resolved, "input", ContractScope(metadata))
            assertEquals(
                BodyValue.JsonValue(JsonArray(listOf(JsonPrimitive("provider-a"), JsonPrimitive("provider-b")))),
                assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate,
            )
            assertTrue(binding.invoked)
            assertEquals(metadata, binding.receivedMetadata)
        }
    }

    @Test
    fun executionLifecycleReturnsTerminalResultThroughCallerOwnedCall() {
        val orchestrator = ImmediateOrchestrator()
        val request = ExecutionRequest(workflowIdentity())

        // The caller owns this suspend invocation and therefore its coroutine lifetime/cancellation.
        val result = runSuspend { orchestrator.executeForTest(request) }

        assertEquals(request.workflow, result.workflow)
        assertIs<ExecutionOutcome.Succeeded>(result.outcome)
    }

    private fun workflowIdentity() = WorkflowIdentity(
        format = "arazzo",
        formatVersion = "1.1",
        documentId = "https://example.test/workflows/weather",
        revision = "sha256:abc",
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        var value: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                value = result
            }
        })
        return checkNotNull(value) { "Test coroutine suspended unexpectedly" }.getOrThrow()
    }

    private class Handle(override val invocation: CapabilityInvocation) : ResolvedOperation

    private class RecordingBinding : OperationBinding<String> {
        var resolvedTarget: ServiceLocator? = null
        var invoked = false
        var receivedMetadata: ExecutionMetadata? = null

        override suspend fun resolve(invocation: CapabilityInvocation): ResolvedOperation {
            resolvedTarget = invocation.locator
            return Handle(invocation)
        }

        override suspend fun invoke(
            operation: ResolvedOperation,
            input: String,
            scope: InvocationScope,
        ): CapabilityResult {
            assertIs<Handle>(operation)
            assertEquals("input", input)
            invoked = true
            receivedMetadata = scope.metadata
            return CapabilityResult(
                operation.invocation.locator,
                CapabilityOutcome.Succeeded(BodyValue.JsonValue(JsonArray(listOf(JsonPrimitive("provider-a"), JsonPrimitive("provider-b"))))),
                emptyList(),
            )
        }
    }

    private class ImmediateOrchestrator : Orchestrator {
        override val capabilities = OrchestratorCapabilities(
            formats = emptySet(),
            executionModes = setOf(ExecutionMode.IN_PROCESS),
        )

        override fun checkCapabilities(requirements: CapabilityRequirements) = capabilities.check(requirements)

        override suspend fun execute(request: ExecutionRequest, budget: ExecutionBudget, events: ExecutionEventSink) =
            ExecutionResult(RunId("run-test"), request.workflow, ExecutionOutcome.Succeeded())

        suspend fun executeForTest(request: ExecutionRequest) = execute(request)
    }
}
