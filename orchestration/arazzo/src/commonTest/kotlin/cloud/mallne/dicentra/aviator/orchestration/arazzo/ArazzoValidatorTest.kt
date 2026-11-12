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

    private val document = """{"arazzo":"1.1.0","info":{"title":"test","version":"1.0"},"sourceDescriptions":[{"name":"api","url":"openapi.json","type":"openapi"}],"workflows":[{"workflowId":"wf","steps":[]}],"x-note":"kept"}"""
}
