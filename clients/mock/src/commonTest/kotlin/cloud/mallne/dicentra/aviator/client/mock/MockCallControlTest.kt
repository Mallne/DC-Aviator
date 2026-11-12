package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import io.ktor.openapi.GenericElement
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.openapi.PathItem
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.Responses
import io.ktor.openapi.Server
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MockCallControlTest {
    @Test
    fun mockPipelineCountsActualInvocations() = runTest {
        val service = MockConverter().build(spec()).single()
        val identity = ConstituentIdentity(
            ServiceLocator("mock-weather"), "mock-provider", "mock-source", "1", "getWeather", "/weather",
        )
        val callLimited = EnforcedInvocationScope(metadata("call"), ExecutionBudget(maxCalls = 1))
        withContext(InvocationCallControl(callLimited, identity)) {
            val first = service.requestContextful()
            assertTrue(first.networkChain.any { it.response != null })
            val rejected = assertFailsWith<InvocationRejectedException> { service.request() }
            assertEquals("calls", rejected.failure.code)
        }
    }

    private fun metadata(id: String) = ExecutionMetadata(
        RunId(id), WorkflowIdentity("test", "1", "mock-spec", "1"),
    )

    private fun spec() = OpenApiDoc(
        info = OpenApiInfo(title = "mock", version = "1"),
        servers = listOf(Server(url = "https://mock.test")),
        paths = mapOf(
            "/weather" to ReferenceOr.Value(
                PathItem(
                    get = Operation(
                        operationId = "getWeather",
                        responses = Responses(),
                        extensions = mapOf(
                            "x-dicentra-aviator-serviceDelegateCall" to GenericElement("mock-weather"),
                            "x-dicentra-aviator-serviceOptions" to GenericElement("{}"),
                        ),
                    ),
                ),
            ),
        ),
        extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
    )
}
