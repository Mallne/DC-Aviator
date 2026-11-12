package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.selectConstituents
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ConstituentSelectionTest {
    private val locator = ServiceLocator("weather>gather")
    private val first = ConstituentIdentity(locator, "a", "a", "1", "forecast")
    private val second = ConstituentIdentity(locator, "b", "b", "2", "weather")
    private val update = ConstituentIdentity(ServiceLocator("weather>update"), "c", "c", "1", "update")
    private val policy = CapabilityPolicy(
        ExecutionStrategy.AGGREGATE, ConstituentOrdering.IDENTITY,
        PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL,
    )

    @Test
    fun selectionPreservesDistinctOperationsAndExcludesOtherFlavours() {
        val candidates = listOf(second, update, first)
        assertEquals(listOf(first, second), selectConstituents(CapabilityInvocation(locator, policy), candidates) { it })
        assertEquals(listOf(second, first), selectConstituents(
            CapabilityInvocation(locator, policy.copy(ordering = ConstituentOrdering.RESOLUTION)), candidates,
        ) { it })
    }

    @Test
    fun explicitRestrictionsCannotFanOutToSameLocatorSiblings() {
        val invocation = CapabilityInvocation(locator, policy, setOf(second))
        assertEquals(listOf(second), selectConstituents(invocation, listOf(first, second)) { it })
        assertEquals(emptyList(), selectConstituents(invocation.copy(constituents = emptySet()), listOf(first, second)) { it })
    }
}
