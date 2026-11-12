package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.client.mock.MockConverter
import cloud.mallne.dicentra.aviator.client.mock.MockedAviatorService
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoBindingStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoRunExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.execution.ConstituentExecution
import cloud.mallne.dicentra.aviator.orchestration.execution.MaterializedConstituent
import cloud.mallne.dicentra.aviator.orchestration.execution.MaterializedOperationBinding
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.HttpResponseMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.ResultRepresentation
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.ResponseSnapshot
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MockClientDslIntegrationTest {
    @Test
    fun existingMockConverterWorksWithDslAndPolicies() = runTest {
        var completedCalls = 0
        exerciseClientDsl(MockConverter(), callCount = { completedCalls }) { service, _ ->
            val response = (service as MockedAviatorService).requestContextful().networkChain.single().response!!
            completedCalls++
            val body = BodyValue.BytesValue(response.content!!.toList())
            ConstituentExecution(ConstituentOutcome.Succeeded(body),
                ResponseSnapshot(body, response.contentType.toString(), HttpResponseMetadata(response.status.value)))
        }
    }

    @Test
    fun twoReadOnlyMockOperationsPassStatusOutputIntoTheNextBindingCall() = runTest {
        val converter = MockConverter()
        val operations = listOf("login", "forecast")
        val services = operations.map { converter.build(clientSpec(it)).single() }
        val identities = operations.map { operation ->
            ConstituentIdentity(ServiceLocator("weather>get"), "provider", "source", "rev", operation)
        }
        val receivedInputs = mutableListOf<String>()
        val calls = mutableListOf<String>()
        val constituents = services.zip(identities).map { (service, identity) ->
            MaterializedConstituent<JsonElement>(identity) { input ->
                receivedInputs += input.toString()
                calls += identity.operationId.orEmpty()
                val context = (service as MockedAviatorService).requestContextful()
                val response = context.networkChain.single().response!!
                val body = BodyValue.BytesValue(response.content!!.toList())
                ConstituentExecution(
                    ConstituentOutcome.Succeeded(body, ResultRepresentation.UNKNOWN),
                    ResponseSnapshot(body, response.contentType.toString(), HttpResponseMetadata(response.status.value)),
                )
            }
        }
        val binding = MaterializedOperationBinding(
            constituents,
            isUsable = { true },
            aggregate = { it.single() },
        )
        val executor = ArazzoRunExecutor(ArazzoBindingStepExecutor(
            binding,
            identities,
            CapabilityPolicy(
                ExecutionStrategy.AGGREGATE,
                ConstituentOrdering.RESOLUTION,
                PartialFailurePolicy.REQUIRE_ALL,
                EmptySelectionPolicy.FAIL,
            ),
        ))
        val workflow = Json.decodeFromString<ArazzoWorkflow>("""
            {
              "workflowId": "mock-two-step",
              "steps": [
                {"stepId": "first", "operationId": "login", "outputs": {"status": "${'$'}statusCode"}},
                {"stepId": "second", "operationId": "forecast", "parameters": [
                  {"name": "trace", "in": "header", "value": "${'$'}steps.first.outputs.status"}
                ]}
              ]
            }
        """.trimIndent())

        val result = executor.execute(
            workflow,
            WorkflowIdentity("arazzo", "1.1.0", "mock-two-step", "rev"),
            RunId("mock-two-step-run"),
        )

        assertIs<ExecutionOutcome.Succeeded>(result.outcome)
        assertEquals(operations, calls)
        assertEquals(
            Json.parseToJsonElement("""{"inputs":{},"parameters":{"header":{"trace":200}}}"""),
            Json.parseToJsonElement(receivedInputs.last()),
        )
    }
}
