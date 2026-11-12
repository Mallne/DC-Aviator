package cloud.mallne.dicentra.aviator.client.mock

import io.ktor.openapi.*
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockClientTest {
    @Test
    fun converterCreatesWorkingMockService() = runTest {
        val api = OpenApiDoc(info = OpenApiInfo(title = "mock", version = "1"),
            servers = listOf(Server(url = "https://mock.test")),
            paths = mapOf("/weather" to ReferenceOr.Value(PathItem(get = Operation(
                operationId = "weather", responses = Responses(), extensions = mapOf(
                    "x-dicentra-aviator-serviceDelegateCall" to GenericElement("mock-weather"),
                    "x-dicentra-aviator-serviceOptions" to GenericElement("{}"),
                ),
            )))), extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")))
        val service = MockConverter().build(api).single()
        assertEquals(ServiceLocator("mock-weather"), service.serviceLocator)
        assertTrue(service.requestContextful().networkChain.any { it.response != null })
    }
}
