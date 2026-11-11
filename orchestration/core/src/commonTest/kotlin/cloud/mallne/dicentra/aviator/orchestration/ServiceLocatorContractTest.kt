package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentResult
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ResponseSnapshot
import cloud.mallne.dicentra.aviator.orchestration.model.HttpResponseMetadata
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ConstituentOrdering
import cloud.mallne.dicentra.aviator.orchestration.policy.EmptySelectionPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionStrategy
import cloud.mallne.dicentra.aviator.orchestration.policy.PartialFailurePolicy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServiceLocatorContractTest {
    private val locator = ServiceLocator("weather>gather")
    private val first = identity("provider-a", "currentWeather")
    private val second = identity("provider-b", "getConditions")
    private val otherFlavour = identity("provider-c", "updateWeather", ServiceLocator("weather>update"))

    @Test
    fun nativeInvocationSelectsDifferentOperationIdsButNotAnotherFlavour() {
        val invocation = invocation()

        assertEquals(listOf(first, second), listOf(first, second, otherFlavour).filter(invocation::accepts))
        assertNotEquals(first.operationId, second.operationId)
        assertEquals(locator, invocation.locator)
    }

    @Test
    fun explicitConstituentRestrictionPreservesConcreteOperationTargeting() {
        val invocation = invocation().copy(constituents = setOf(first))

        assertTrue(invocation.accepts(first))
        assertFalse(invocation.accepts(second))
        assertFalse(invocation.accepts(otherFlavour))
        assertFalse(invocation.accepts(first.copy(sourceRevision = "changed")))
        assertFalse(invocation().copy(constituents = emptySet()).accepts(first))
    }

    @Test
    fun aggregationRetainsConstituentProvenanceAndDoesNotInventHttpMetadata() {
        val result = CapabilityResult(
            locator = locator,
            outcome = CapabilityOutcome.Succeeded(BodyValue.JsonValue(JsonArray(listOf(JsonPrimitive(18), JsonPrimitive(20))))),
            constituents = listOf(success(first, 18), success(second, 20)),
        )

        assertEquals(listOf(first, second), result.constituents.map { it.identity })
        assertEquals(listOf(200, 200), result.constituents.map { it.response?.http?.status })
        val encoded = Json.encodeToString(result)
        assertEquals(result, Json.decodeFromString<CapabilityResult>(encoded))
        // HTTP metadata belongs to constituent snapshots, not the capability envelope.
        assertFalse("http" in Json.parseToJsonElement(encoded).jsonObject)
        assertFalse("status" in Json.parseToJsonElement(encoded).jsonObject)
    }

    @Test
    fun partialAndEmptyResultsAreRepresentableWithoutUniversalAllSuccessRule() {
        val failed = ConstituentResult(second, ConstituentOutcome.Failed(ExecutionFailure("transport", "Unavailable")))
        val partial = CapabilityResult(locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive(18))), listOf(success(first, 18), failed))
        val rejected = partial.copy(outcome = CapabilityOutcome.Failed(ExecutionFailure("partial", "All results required")))
        val empty = CapabilityResult(locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(JsonArray(emptyList()))), emptyList())

        assertIs<CapabilityOutcome.Succeeded>(partial.outcome)
        assertIs<CapabilityOutcome.Failed>(rejected.outcome)
        assertTrue(empty.constituents.isEmpty())
        assertEquals(PartialFailurePolicy.ALLOW_PARTIAL, invocation().policy.partialFailure)
        assertEquals(EmptySelectionPolicy.FAIL, invocation().policy.emptySelection)
    }

    @Test
    fun bodyStatesPreserveNullEmptyUnavailableDecodeFailureAndRawBytes() {
        val bodies: List<BodyValue> = listOf(
            BodyValue.JsonValue(JsonNull), BodyValue.Empty, BodyValue.Unavailable,
            BodyValue.DecodeFailure(ExecutionFailure("decode", "Invalid JSON")),
            BodyValue.BytesValue(listOf(60, 120, 47, 62)),
        )

        assertEquals(5, bodies.toSet().size)
        bodies.forEach { assertEquals(it, Json.decodeFromString<BodyValue>(Json.encodeToString(it))) }
        val response = ResponseSnapshot(
            body = BodyValue.Empty,
            contentType = "application/json",
            http = HttpResponseMetadata(503, mapOf("Retry-After" to listOf("1", "2"))),
        )
        assertEquals(response, Json.decodeFromString<ResponseSnapshot>(Json.encodeToString(response)))
    }

    @Test
    fun gatewayAggregateContractRetainsRealHttpMetadataOnlyOnItsResponse() {
        val payload = BodyValue.JsonValue(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))
        val gateway = identity("gateway", "aggregateWeather")
        val response = ResponseSnapshot(payload, "application/json", HttpResponseMetadata(200))
        val result = CapabilityResult(
            locator,
            CapabilityOutcome.Succeeded(payload),
            listOf(ConstituentResult(gateway, ConstituentOutcome.Succeeded(payload), response)),
        )

        assertEquals(200, result.constituents.single().response?.http?.status)
        assertEquals(payload, assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
        assertEquals(result, Json.decodeFromString<CapabilityResult>(Json.encodeToString(result)))
    }

    private fun invocation() = CapabilityInvocation(
        locator,
        CapabilityPolicy(
            ExecutionStrategy.AGGREGATE,
            ConstituentOrdering.IDENTITY,
            PartialFailurePolicy.ALLOW_PARTIAL,
            EmptySelectionPolicy.FAIL,
        ),
    )

    private fun identity(provider: String, operation: String, target: ServiceLocator = locator) = ConstituentIdentity(
        locator = target, providerId = provider, sourceId = "https://example.test/$provider/openapi.json",
        sourceRevision = "revision-1", operationId = operation,
    )

    private fun success(identity: ConstituentIdentity, value: Int) = ConstituentResult(
        identity,
        ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive(value))),
        ResponseSnapshot(BodyValue.JsonValue(JsonPrimitive(value)), "application/json", HttpResponseMetadata(200)),
    )
}
