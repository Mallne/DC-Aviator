package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.*
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import io.ktor.openapi.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

/** Application-side composition of the neutral binding with real Aviator mock-client pipelines. */
class MaterializedBindingPipelineTest {
    @Test
    fun bindingInvokesIsolatedPipelinesAndRetainsPartialDenialAndSourceResponse() = runTest {
        val first = MockConverter().build(spec("forecast")).single()
        val second = MockConverter().build(spec("weather")).single()
        val a = ConstituentIdentity(first.serviceLocator, "provider-a", "source-a", "rev-a", "forecast")
        val b = ConstituentIdentity(second.serviceLocator, "provider-b", "source-b", "rev-b", "weather")
        val contexts = mutableListOf<MockExecutionContext>()
        val authorized = mutableListOf<ConstituentIdentity>()
        val constituents = listOf(a to first, b to second).map { (identity, service) ->
            MaterializedConstituent<Unit>(identity) {
                val context = service.requestContextful()
                contexts += context
                val response = context.networkChain.single().response!!
                val source = ResponseSnapshot(
                    body = BodyValue.BytesValue(response.content!!.toList()),
                    contentType = response.contentType.toString(),
                    http = HttpResponseMetadata(response.status.value),
                )
                ConstituentExecution(ConstituentOutcome.Succeeded(source.body), source)
            }
        }
        val binding = MaterializedOperationBinding(
            constituents, isUsable = { it is BodyValue.BytesValue },
            aggregate = { BodyValue.JsonValue(JsonArray(it.map { value -> JsonPrimitive((value as BodyValue.BytesValue).bytes.size) })) },
        )
        val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
            PartialFailurePolicy.ALLOW_PARTIAL, EmptySelectionPolicy.FAIL)
        val invocation = CapabilityInvocation(ServiceLocator("mock-weather"), policy)
        val scope = EnforcedInvocationScope(metadata(), ExecutionBudget(), authorizeCall = {
            authorized += it
            if (it == b) ExecutionFailure("denied", "Denied") else null
        })

        val result = binding.invoke(binding.resolve(invocation), Unit, scope)
        assertEquals(listOf(a, b), authorized)
        assertEquals(1, contexts.size)
        assertEquals(listOf(a, b), result.constituents.map { it.identity })
        assertEquals(200, result.constituents.first().response?.http?.status)
        assertIs<BodyValue.BytesValue>(result.constituents.first().response?.body)
        assertEquals(result.constituents.first().response?.body,
            assertIs<ConstituentOutcome.Succeeded>(result.constituents.first().outcome).value)
        assertEquals("denied", assertIs<ConstituentOutcome.Failed>(result.constituents.last().outcome).failure.code)
        assertIs<CapabilityOutcome.Succeeded>(result.outcome)

        val accepted = binding.invoke(binding.resolve(invocation), Unit, EnforcedInvocationScope(metadata(), ExecutionBudget()))
        assertIs<CapabilityOutcome.Succeeded>(accepted.outcome)
        assertEquals(3, contexts.size)
        assertNotSame(contexts[1], contexts[2])
    }

    private fun metadata() = ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "workflow", "revision"))

    private fun spec(operation: String) = OpenApiDoc(
        info = OpenApiInfo(title = "mock", version = "1"),
        servers = listOf(Server(url = "https://mock.test")),
        paths = mapOf("/weather" to ReferenceOr.Value(PathItem(get = Operation(
            operationId = operation,
            responses = Responses(),
            extensions = mapOf(
                "x-dicentra-aviator-serviceDelegateCall" to GenericElement("mock-weather"),
                "x-dicentra-aviator-serviceOptions" to GenericElement("{}"),
            ),
        )))),
        extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
    )
}
