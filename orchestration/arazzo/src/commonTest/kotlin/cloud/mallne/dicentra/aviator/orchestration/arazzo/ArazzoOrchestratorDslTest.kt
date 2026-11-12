package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.arazzo
import cloud.mallne.dicentra.aviator.orchestration.execution.orchestrator
import cloud.mallne.dicentra.aviator.orchestration.execution.MaterializedOperationBinding
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.*

class ArazzoOrchestratorDslTest {
    @Test
    fun missingConfigurationAndDuplicateEngineFailAtConstruction() {
        assertFailsWith<IllegalStateException> { orchestrator {} }
        assertFailsWith<IllegalStateException> { orchestrator { arazzo {} } }
        assertFailsWith<IllegalStateException> { orchestrator { arazzo {}; arazzo {} } }
    }

    @Test
    fun unsupportedAndMissingWorkflowsNeverInvokeLookupOrOperations() = runTest {
        var lookups = 0
        val engine = orchestrator {
            arazzo {
                workflow = { lookups++; null }
                authorize = { null }
                policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
                    PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL)
                binding = { MaterializedOperationBinding<JsonElement>(emptyList(), { true }, { BodyValue.Empty }) }
                newRunId = { RunId("test") }
            }
        }
        val identity = WorkflowIdentity("arazzo", "1.1.0", "unknown", "1")
        val unsupported = engine.execute(ExecutionRequest(identity.copy(formatVersion = "0")))
        assertEquals("unsupported_workflow", assertIs<ExecutionOutcome.Failed>(unsupported.outcome).failure.code)
        assertEquals(0, lookups)
        val missing = engine.execute(ExecutionRequest(identity))
        assertEquals("workflow_not_found", assertIs<ExecutionOutcome.Failed>(missing.outcome).failure.code)
        assertEquals(1, lookups)
    }
}
