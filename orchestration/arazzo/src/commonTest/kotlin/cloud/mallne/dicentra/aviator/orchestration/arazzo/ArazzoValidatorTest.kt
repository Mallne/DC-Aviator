package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.orchestration.arazzo.validation.ArazzoValidationResult
import cloud.mallne.dicentra.aviator.orchestration.arazzo.validation.ArazzoValidator
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArazzoValidatorTest {
    @Test
    fun acceptsExplicitProfileAndKeepsExtensionsInert() {
        val result = ArazzoValidator.parseAndValidate(document.replace("\"steps\":[]", "\"steps\":[{\"stepId\":\"first\",\"operationId\":\"\$sourceDescriptions.api.getPet\"}]"))
        val valid = assertIs<ArazzoValidationResult.Valid>(result)
        assertEquals("1.0", valid.document.info.version)
        assertEquals(JsonPrimitive("kept"), valid.document.extensions["x-note"])
    }

    @Test
    fun retainsCallerRetrievalUriAndSelfWithoutTreatingEitherAsProviderEndpoint() {
        val json = document.replace("\"arazzo\":", "\"\$self\":\"https://workflow.example/identity\",\"arazzo\":")
        val valid = assertIs<ArazzoValidationResult.Valid>(
            ArazzoValidator.parseAndValidate(json, "https://workflow.example/retrieved/workflow.json"),
        )

        assertEquals("https://workflow.example/retrieved/workflow.json", valid.document.location?.retrievalUri)
        assertEquals("https://workflow.example/retrieved/workflow.json", valid.document.location?.baseUri)
        assertEquals("https://workflow.example/identity", valid.document.location?.selfUri)
        assertEquals("openapi.json", valid.document.sourceDescriptions.single().url)
    }

    @Test
    fun rejectsUnsupportedVersionAndExtensionExecution() {
        val result = ArazzoValidator.parseAndValidate(document.replace("1.1.0", "2.0.0").replace("\"steps\":[]", "\"steps\":[{\"stepId\":\"a\",\"operationId\":\"getPet\",\"x-dicentra-aviator-locator\":true}]"))
        val invalid = assertIs<ArazzoValidationResult.Invalid>(result)
        assertEquals(setOf("unsupported-version", "unsupported-extension"), invalid.diagnostics.map { it.code }.toSet())
    }

    @Test
    fun rejectsDuplicateIdsUnresolvedSourcesAndForwardOutputs() {
        val json = document.replace("\"steps\":[]", "\"steps\":[{\"stepId\":\"same\",\"operationId\":\"\$sourceDescriptions.missing.op\",\"outputs\":{\"x\":\"\$steps.later.outputs.value\"}},{\"stepId\":\"same\",\"operationId\":\"getPet\"}]")
        val invalid = assertIs<ArazzoValidationResult.Invalid>(ArazzoValidator.parseAndValidate(json))
        assertEquals(setOf("duplicate-step-id", "unresolved-source", "unresolved-step-output"), invalid.diagnostics.map { it.code }.toSet())
    }

    @Test
    fun acceptsSimpleSuccessCriteriaAndRejectsEmptyCriteria() {
        val operation = "\"stepId\":\"first\",\"operationId\":\"getPet\""
        val accepted = document.replace("\"steps\":[]", "\"steps\":[{$operation,\"successCriteria\":[{\"type\":\"simple\",\"condition\":\"\$statusCode == 200\"}]}]")
        assertIs<ArazzoValidationResult.Valid>(ArazzoValidator.parseAndValidate(accepted))

        val rejected = document.replace("\"steps\":[]", "\"steps\":[{$operation,\"successCriteria\":[]}]")
        val invalid = assertIs<ArazzoValidationResult.Invalid>(ArazzoValidator.parseAndValidate(rejected))
        assertEquals("invalid-success-criteria", invalid.diagnostics.single().code)
    }

    @Test
    fun preflightRejectsUnsupportedExpressionsWithPreciseLocations() {
        val badParameter = document.replace(
            "\"steps\":[]",
            "\"steps\":[{\"stepId\":\"first\",\"operationId\":\"getPet\",\"parameters\":[{\"name\":\"x\",\"in\":\"query\",\"value\":\"\$env.HOME\"}]}]",
        )
        val invalid = assertIs<ArazzoValidationResult.Invalid>(ArazzoValidator.parseAndValidate(badParameter))
        assertEquals("/workflows/0/steps/0/parameters/0/value", invalid.diagnostics.single().location)

        val badCriterion = document.replace(
            "\"steps\":[]",
            "\"steps\":[{\"stepId\":\"first\",\"operationId\":\"getPet\",\"successCriteria\":[{\"condition\":\"1 + 2 == 3\"}]}]",
        )
        val criterionInvalid = assertIs<ArazzoValidationResult.Invalid>(ArazzoValidator.parseAndValidate(badCriterion))
        assertEquals("/workflows/0/steps/0/successCriteria/0/condition", criterionInvalid.diagnostics.single().location)
    }

    private val document = """{"arazzo":"1.1.0","info":{"title":"test","version":"1.0"},"sourceDescriptions":[{"name":"api","url":"openapi.json","type":"openapi"}],"workflows":[{"workflowId":"wf","steps":[]}],"x-note":"kept"}"""
}
