package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoOperationResolution
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.resolveArazzoOperation
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArazzoOperationResolverTest {
    private val locator = ServiceLocator("weather>gather")
    private val policy = CapabilityPolicy(
        ExecutionStrategy.AGGREGATE,
        ConstituentOrdering.RESOLUTION,
        PartialFailurePolicy.REQUIRE_ALL,
        EmptySelectionPolicy.FAIL,
    )

    @Test
    fun operationIdResolvesToExactSourceQualifiedIdentityAndRestrictsInvocation() {
        val forecast = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast", "/forecast")
        val sibling = ConstituentIdentity(locator, "provider-b", "spec-b", "rev-b", "getWeather", "/weather")

        val resolved = assertIs<ArazzoOperationResolution.Resolved>(
            resolveArazzoOperation(ArazzoStep("forecast", operationId = "getForecast"), listOf(forecast, sibling), policy),
        )

        assertEquals(locator, resolved.invocation.locator)
        assertEquals(setOf(forecast), resolved.invocation.constituents)
        assertEquals(true, resolved.invocation.accepts(forecast))
        assertEquals(false, resolved.invocation.accepts(sibling))
    }

    @Test
    fun duplicateOperationIdAcrossSourcesFailsInsteadOfFanout() {
        val first = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast", "/forecast")
        val second = ConstituentIdentity(locator, "provider-b", "spec-b", "rev-b", "getForecast", "/forecast")

        val failed = assertIs<ArazzoOperationResolution.Failed>(
            resolveArazzoOperation(ArazzoStep("forecast", operationId = "getForecast"), listOf(first, second), policy),
        )
        assertEquals("ambiguous_operation_id", failed.code)
    }

    @Test
    fun sourceQualifiedReferenceSelectsOnlyItsDeclaredSource() {
        val first = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast", "/forecast")
        val second = ConstituentIdentity(locator, "provider-b", "spec-b", "rev-b", "getForecast", "/forecast")

        val resolved = assertIs<ArazzoOperationResolution.Resolved>(
            resolveArazzoOperation(
                ArazzoStep("forecast", operationId = "\$sourceDescriptions.api.getForecast"),
                listOf(first, second), policy, mapOf("api" to "spec-b"),
            ),
        )
        assertEquals(setOf(second), resolved.invocation.constituents)
    }

    @Test
    fun sourceQualifiedReferenceWithoutSuppliedSourceFails() {
        val only = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast", "/forecast")
        val failed = assertIs<ArazzoOperationResolution.Failed>(
            resolveArazzoOperation(
                ArazzoStep("forecast", operationId = "\$sourceDescriptions.missing.getForecast"),
                listOf(only), policy,
            ),
        )
        assertEquals("source_not_found", failed.code)
    }
}
