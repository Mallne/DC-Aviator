package cloud.mallne.dicentra.aviator.plugin.orchestration.authorization

import cloud.mallne.dicentra.aviator.core.execution.controlledAviatorCall
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.orchestration.policy.ConstituentOrdering
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.EmptySelectionPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionStrategy
import cloud.mallne.dicentra.aviator.orchestration.policy.PartialFailurePolicy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConstituentAuthorizationPluginTest {
    @Test
    fun optionalTargetPolicyRunsAfterMandatoryHostCheckAndBeforeTransport() = runTest {
        val identity = ConstituentIdentity(ServiceLocator("weather>get"), "restricted", "source", "rev")
        val order = mutableListOf<String>()
        var transportCalls = 0
        val invocation = CapabilityInvocation(identity.locator, CapabilityPolicy(
            ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
            PartialFailurePolicy.ALLOW_PARTIAL, EmptySelectionPolicy.FAIL,
        ), constituents = setOf(identity))
        val registry = OrchestrationPluginRegistry(listOf(ConstituentAuthorizationPlugin("tenant") {
            order += "plugin"
            if (it.providerId == "restricted") ExecutionFailure("tenant_denied", "Provider is not allowed") else null
        }.install()))
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("auth"), WorkflowIdentity("test", "1", "doc", "rev")),
            ExecutionBudget(), authorizeCall = { order += "host"; null },
        )

        val failure = withContext(InvocationCallControl(
            scope, identity, invocation, registry, scope.pluginState("orchestration"),
        )) {
            assertFailsWith<InvocationRejectedException> {
                controlledAviatorCall(call = { transportCalls++ })
            }
        }

        assertEquals("tenant_denied", failure.failure.code)
        assertEquals(listOf("host", "plugin"), order)
        assertEquals(0, transportCalls)
    }
}
