package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.client.mock.MockConverter
import cloud.mallne.dicentra.aviator.client.mock.MockedAviatorService
import cloud.mallne.dicentra.aviator.orchestration.execution.ConstituentExecution
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.HttpResponseMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.ResponseSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
}
