package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoRunExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoStepExecution
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoDocument
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.arazzoDocument
import cloud.mallne.dicentra.aviator.orchestration.arazzo.validation.ArazzoValidationResult
import cloud.mallne.dicentra.aviator.orchestration.arazzo.validation.ArazzoValidator
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentResult
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.HttpResponseMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.ResponseSnapshot
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ArazzoDocumentDslTest {
    @Test
    fun authoredDocumentRunsWithTheActualEngineAndRoundTrips() = runTest {
        val document = arazzoDocument("Weather", "1") {
            source("weather", "urn:weather:openapi")
            workflow("forecast") {
                step("fetch", "\$sourceDescriptions.weather.forecast") {
                    parameter("latitude", "query", "\$inputs.lat")
                    parameter("days", "query", JsonPrimitive(2))
                    output("body", "\$response.body")
                }
                output("forecast", "\$steps.fetch.outputs.body")
            }
        }
        val encoded = Json.encodeToString(ArazzoDocument.serializer(), document)
        assertIs<ArazzoValidationResult.Valid>(ArazzoValidator.parseAndValidate(encoded))
        assertEquals(encoded, Json.encodeToString(ArazzoDocument.serializer(), Json.decodeFromString(ArazzoDocument.serializer(), encoded)))

        val identity = ConstituentIdentity(ServiceLocator("weather"), "provider", "source", "1", "forecast")
        val body = BodyValue.JsonValue(Json.parseToJsonElement("""{"temperature":18}"""))
        var observedInput = ""
        val runner = ArazzoRunExecutor(ArazzoStepExecutor { _, inputs, _ ->
            observedInput = inputs
            ArazzoStepExecution.Completed(CapabilityResult(
                identity.locator, CapabilityOutcome.Succeeded(body),
                listOf(ConstituentResult(identity, ConstituentOutcome.Succeeded(body),
                    ResponseSnapshot(body, http = HttpResponseMetadata(200)))),
            ))
        })
        val execution = runner.execute(
            document.workflows.single(), WorkflowIdentity("arazzo", "1.1.0", "weather", "1"),
            RunId("test"), """{"lat":52.5}""",
        )
        assertEquals(Json.parseToJsonElement("""{"inputs":{"lat":52.5},"parameters":{"query":{"latitude":52.5,"days":2}}}"""),
            Json.parseToJsonElement(observedInput))
        assertEquals("""{"forecast":{"temperature":18}}""", assertIs<ExecutionOutcome.Succeeded>(execution.outcome).outputs)
    }

    @Test
    fun rejectsInvalidAndUnsupportedDefinitionsAtBuildTime() {
        assertFailsWith<IllegalArgumentException> {
            arazzoDocument("Weather", "1") {
                source("weather", "urn:weather")
                workflow("forecast") { step("fetch", "\$sourceDescriptions.missing.forecast") }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            arazzoDocument("Weather", "1") {
                source("weather", "urn:weather")
                workflow("forecast") {
                    step("fetch", "forecast") {
                        parameter("lat", "query", "\$inputs.lat")
                        parameter("lat", "query", "\$inputs.lon")
                    }
                }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            arazzoDocument("Weather", "1") {
                source("weather", "urn:weather")
                workflow("forecast") { }
            }
        }
    }
}
