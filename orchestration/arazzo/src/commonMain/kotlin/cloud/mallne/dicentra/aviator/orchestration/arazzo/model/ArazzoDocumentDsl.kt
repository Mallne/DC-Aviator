package cloud.mallne.dicentra.aviator.orchestration.arazzo.model

import cloud.mallne.dicentra.aviator.orchestration.arazzo.ArazzoSupport
import cloud.mallne.dicentra.aviator.orchestration.arazzo.validation.ArazzoValidationResult
import cloud.mallne.dicentra.aviator.orchestration.arazzo.validation.ArazzoValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@DslMarker
annotation class ArazzoDocumentDsl

/**
 * Authors a document for Aviator's supported Arazzo profile. The result is the same model consumed
 * by the Arazzo runner; validation is identical to the JSON ingestion boundary.
 */
fun arazzoDocument(
    title: String,
    version: String,
    configure: ArazzoDocumentBuilder.() -> Unit,
): ArazzoDocument = ArazzoDocumentBuilder(title, version).apply(configure).build()

@ArazzoDocumentDsl
class ArazzoDocumentBuilder internal constructor(private val title: String, private val version: String) {
    var summary: String? = null
    var description: String? = null
    var self: String? = null
    private val sources = mutableListOf<SourceDescription>()
    private val workflows = mutableListOf<ArazzoWorkflow>()

    fun source(name: String, url: String) {
        require(name.isNotBlank() && url.isNotBlank()) { "Source name and URL must not be blank" }
        sources += SourceDescription(name = name, url = url)
    }

    fun workflow(id: String, configure: ArazzoWorkflowBuilder.() -> Unit) {
        require(id.isNotBlank()) { "Workflow id must not be blank" }
        workflows += ArazzoWorkflowBuilder(id).apply(configure).build()
    }

    internal fun build(): ArazzoDocument {
        require(title.isNotBlank() && version.isNotBlank()) { "Arazzo title and version must not be blank" }
        val document = ArazzoDocument(
            arazzo = ArazzoSupport.SPECIFICATION_VERSION,
            info = ArazzoInfo(title = title, version = version, summary = summary, description = description),
            sourceDescriptions = sources.toList(),
            workflows = workflows.toList(),
            self = self,
        )
        return when (val result = ArazzoValidator.parseAndValidate(Json.encodeToString(ArazzoDocument.serializer(), document))) {
            is ArazzoValidationResult.Valid -> result.document
            is ArazzoValidationResult.Invalid -> throw IllegalArgumentException(
                result.diagnostics.joinToString("; ") { "${it.location}: ${it.code}: ${it.message}" },
            )
        }
    }
}

@ArazzoDocumentDsl
class ArazzoWorkflowBuilder internal constructor(private val id: String) {
    var summary: String? = null
    var description: String? = null
    private val steps = mutableListOf<ArazzoStep>()
    private val outputs = linkedMapOf<String, String>()

    fun step(id: String, operationId: String, configure: ArazzoStepBuilder.() -> Unit = {}) {
        require(id.isNotBlank() && operationId.isNotBlank()) { "Step id and operationId must not be blank" }
        steps += ArazzoStepBuilder(id, operationId).apply(configure).build()
    }

    fun output(name: String, expression: String) {
        require(name.isNotBlank()) { "Output name must not be blank" }
        require(name !in outputs) { "Duplicate workflow output '$name'" }
        outputs[name] = expression
    }

    internal fun build(): ArazzoWorkflow {
        require(steps.isNotEmpty()) { "Workflow '$id' must contain a step" }
        return ArazzoWorkflow(
            workflowId = id, steps = steps.toList(), outputs = outputs.takeIf { it.isNotEmpty() }?.toMap(),
            summary = summary, description = description,
        )
    }
}

@ArazzoDocumentDsl
class ArazzoStepBuilder internal constructor(private val id: String, private val operationId: String) {
    var description: String? = null
    var requestBody: JsonElement? = null
    private val parameters = mutableListOf<ArazzoParameter>()
    private val criteria = mutableListOf<ArazzoCriterion>()
    private val outputs = linkedMapOf<String, String>()

    fun parameter(name: String, location: String, value: JsonElement) {
        require(name.isNotBlank()) { "Parameter name must not be blank" }
        parameters += ArazzoParameter(name = name, `in` = location, value = value)
    }

    fun parameter(name: String, location: String, value: String) = parameter(name, location, JsonPrimitive(value))

    fun success(condition: String) {
        criteria += ArazzoCriterion(condition)
    }

    fun output(name: String, expression: String) {
        require(name.isNotBlank()) { "Output name must not be blank" }
        require(name !in outputs) { "Duplicate step output '$name'" }
        outputs[name] = expression
    }

    internal fun build(): ArazzoStep = ArazzoStep(
        stepId = id, operationId = operationId, parameters = parameters.toList(), requestBody = requestBody,
        successCriteria = criteria.takeIf { it.isNotEmpty() }?.toList(),
        outputs = outputs.takeIf { it.isNotEmpty() }?.toMap(), description = description,
    )
}
