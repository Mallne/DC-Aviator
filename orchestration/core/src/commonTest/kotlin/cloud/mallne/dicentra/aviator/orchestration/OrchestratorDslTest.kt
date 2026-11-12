package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.core.APIToServiceConverter
import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.execution.logging.LoggingPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginActivationScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.BasicPluginActivationScope
import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.Orchestrator
import cloud.mallne.dicentra.aviator.orchestration.execution.orchestrator
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import kotlinx.serialization.SerialFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class OrchestratorDslTest {
    @Test
    fun delegatesOriginalAviatorPluginScopeAndPassesAllServicesToEngine() {
        val firstApi = OpenApiDoc(info = OpenApiInfo(title = "first", version = "1"))
        val secondApi = OpenApiDoc(info = OpenApiInfo(title = "second", version = "1"))
        val converted = mutableListOf<OpenApiDoc>()
        val installed = mutableListOf<List<AviatorPluginInstance>>()
        val first = TestService(firstApi)
        val second = TestService(secondApi)
        val converter = object : APIToServiceConverter {
            override fun build(api: OpenApiDoc, plugins: AviatorPluginActivationScope.() -> Unit): List<AviatorServiceDataHolder> {
                converted += api
                val scope = BasicPluginActivationScope().apply(plugins)
                installed += scope.registry.toList()
                return listOf(if (api == firstApi) first else second)
            }
        }
        val existing = TestService(firstApi)
        val expected = StubOrchestrator()
        val result = orchestrator {
            aviator(converter, firstApi) {
                install(LoggingPlugin) { silentLoggingTags += "sensitive" }
            }
            aviator(converter, secondApi)
            aviator(listOf(existing))
            plugins { install("first") }
            plugins { install("second") }
            engine { environment ->
                assertEquals(listOf(first, second, existing), environment.services)
                assertEquals("first", environment.plugins.get("first")?.identity)
                assertEquals("second", environment.plugins.get("second")?.identity)
                expected
            }
        }
        assertSame(expected, result)
        assertEquals(listOf(firstApi, secondApi), converted)
        assertEquals(listOf(LoggingPlugin.identity), installed[0].map { it.identity })
        assertEquals(listOf("sensitive"), installed[0].single().configurationBundle.silentLoggingTags)
        assertEquals(emptyList(), installed[1])
    }

    @Test
    fun duplicatePluginIdentitiesAreRejectedBeforeEngineCreation() {
        var engineCreated = false
        assertFailsWith<IllegalArgumentException> {
            orchestrator {
                plugins { install("same") }
                plugins { install("same") }
                engine { engineCreated = true; StubOrchestrator() }
            }
        }
        assertEquals(false, engineCreated)
    }
}

private class TestService(override val oas: OpenApiDoc) : AviatorServiceDataHolder {
    override val serviceLocator = ServiceLocator("test>operation")
    override val plugins = emptyList<AviatorPluginInstance>()
    override val options: ServiceOptions get() = error("unused")
    override val route: Route get() = error("unused")
    override val serializers: List<SerialFormat> = emptyList()
}

private class StubOrchestrator : Orchestrator {
    override val capabilities = OrchestratorCapabilities(emptySet(), emptySet())
    override fun checkCapabilities(requirements: CapabilityRequirements) = capabilities.check(requirements)
    override suspend fun execute(request: ExecutionRequest, budget: ExecutionBudget, events: ExecutionEventSink): ExecutionResult =
        error("unused")
}
