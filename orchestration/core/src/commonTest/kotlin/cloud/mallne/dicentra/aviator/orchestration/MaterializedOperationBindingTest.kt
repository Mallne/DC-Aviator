package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.core.execution.controlledAviatorCall
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.*
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MaterializedOperationBindingTest {
    @Test
    fun resultRepresentationSurvivesSerializationWithoutClaimingUnknownOrigin() {
        val transformed: ConstituentOutcome = ConstituentOutcome.Succeeded(
            BodyValue.JsonValue(JsonPrimitive("transformed")), ResultRepresentation.TRANSFORMED,
        )
        assertEquals(transformed, Json.decodeFromString<ConstituentOutcome>(Json.encodeToString(transformed)))
        assertEquals(ResultRepresentation.UNKNOWN, ConstituentOutcome.Succeeded(BodyValue.Empty).representation)
    }

    @Test
    fun aggregateNeedsNoSizeEstimateOrReservation() = runTest {
        val aggregate = BodyValue.JsonValue(JsonPrimitive("result".repeat(1024)))
        var aggregateCalls = 0
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<Unit>(first) {
                controlledAviatorCall(call = {
                    ConstituentExecution(ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive("ok"))))
                })
            }),
            isUsable = { true },
            aggregate = { aggregateCalls++; aggregate },
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        )
        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)
        assertEquals(aggregate, assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
        assertEquals(1, aggregateCalls)
    }

    @Test
    fun nullableJsonClassifierDistinguishesJsonNullAndAbsentResponse() {
        assertEquals(ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonNull)),
            ConstituentExecution.decodedJson(JsonNull).classifiedOutcome(null))
        assertEquals(ConstituentOutcome.Failed(ExecutionFailure("absent_result", "No decoded response available")),
            ConstituentExecution.decodedJson(null).classifiedOutcome(null))
    }

    private val locator = ServiceLocator("weather>gather")
    private val first = ConstituentIdentity(locator, "a", "source-a", "rev-a", "forecast")
    private val second = ConstituentIdentity(locator, "b", "source-b", "rev-b", "weather")
    private val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
        PartialFailurePolicy.ALLOW_PARTIAL, EmptySelectionPolicy.FAIL)

    @Test
    fun actualControlledCallsAggregateAndKeepProvenanceUnderPartialDenial() = runTest {
        val calls = mutableListOf<ConstituentIdentity>()
        val authorized = mutableListOf<ConstituentIdentity>()
        val binding = binding(calls)
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "source", "revision")),
            ExecutionBudget(), authorizeCall = { identity ->
                authorized += identity
                if (identity == second) ExecutionFailure("denied", "Denied") else null
            },
        )
        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)

        assertEquals(listOf(first, second), authorized)
        assertEquals(listOf(first), calls)
        assertEquals(listOf(first, second), result.constituents.map { it.identity })
        assertEquals("denied", assertIs<ConstituentOutcome.Failed>(result.constituents[1].outcome).failure.code)
        val value = assertIs<BodyValue.JsonValue>(assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
        assertEquals(JsonArray(listOf(JsonPrimitive("forecast"))), value.value)
    }

    @Test
    fun exactRestrictionInvokesOnlyRequestedOperation() = runTest {
        val calls = mutableListOf<ConstituentIdentity>()
        val binding = binding(calls)
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        )
        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy, setOf(second))), Unit, scope)
        assertIs<CapabilityOutcome.Succeeded>(result.outcome)
        assertEquals(listOf(second), calls)
    }

    private fun binding(calls: MutableList<ConstituentIdentity>) = MaterializedOperationBinding(
        listOf(first, second).map { identity ->
            MaterializedConstituent<Unit>(identity) {
                controlledAviatorCall(call = {
                    calls += identity
                    ConstituentExecution(ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive(identity.operationId))))
                })
            }
        },
        isUsable = { it is BodyValue.JsonValue },
        aggregate = { values -> BodyValue.JsonValue(JsonArray(values.map { (it as BodyValue.JsonValue).value })) },
    )
}
