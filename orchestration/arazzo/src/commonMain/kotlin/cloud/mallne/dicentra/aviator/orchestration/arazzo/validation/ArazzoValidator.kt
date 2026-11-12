package cloud.mallne.dicentra.aviator.orchestration.arazzo.validation

import cloud.mallne.dicentra.aviator.orchestration.arazzo.ArazzoSupport
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoDocument
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoDocumentLocation
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.isSupportedArazzoCriterionSyntax
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.isSupportedArazzoExpressionSyntax
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Machine-readable failure location, using JSON Pointer syntax (empty string is document root). */
data class ArazzoDiagnostic(val code: String, val message: String, val location: String)

sealed interface ArazzoValidationResult {
    data class Valid(val document: ArazzoDocument) : ArazzoValidationResult
    data class Invalid(val diagnostics: List<ArazzoDiagnostic>) : ArazzoValidationResult
}

/**
 * JSON-only initial execution boundary. Unknown fields fail closed; `x-` extensions are retained
 * as inert JSON data. This validates profile semantics, not JSON Schema 2020-12 input schemas.
 */
object ArazzoValidator {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseAndValidate(source: String, retrievalUri: String? = null): ArazzoValidationResult {
        val parsedElement = try {
            json.parseToJsonElement(source)
        } catch (exception: SerializationException) {
            return invalid("invalid-json", exception.message ?: "Invalid JSON", "")
        } catch (exception: IllegalArgumentException) {
            return invalid("invalid-json", exception.message ?: "Invalid JSON", "")
        }
        val objectValue = parsedElement as? JsonObject
            ?: return invalid("invalid-document", "Arazzo document must be a JSON object", "")
        val diagnostics = mutableListOf<ArazzoDiagnostic>()
        inspectUnknown(objectValue, "", ROOT_KEYS, diagnostics)
        val document = try {
            json.decodeFromJsonElement(ArazzoDocument.serializer(), objectValue)
        } catch (exception: SerializationException) {
            return invalid("invalid-model", exception.message ?: "Document does not match the supported model", "")
        }
        retainExtensions(document, objectValue)
        document.location = ArazzoDocumentLocation(
            retrievalUri = retrievalUri,
            baseUri = retrievalUri,
            selfUri = document.self,
        )
        document.workflows.forEach { it.documentLocation = document.location }
        validate(document, diagnostics)
        return if (diagnostics.isEmpty()) ArazzoValidationResult.Valid(document)
        else ArazzoValidationResult.Invalid(diagnostics)
    }

    private fun validate(document: ArazzoDocument, out: MutableList<ArazzoDiagnostic>) {
        if (document.arazzo != ArazzoSupport.SPECIFICATION_VERSION) {
            out += ArazzoDiagnostic("unsupported-version", "Only Arazzo ${ArazzoSupport.SPECIFICATION_VERSION} is supported", "/arazzo")
        }
        if (document.sourceDescriptions.isEmpty()) out += ArazzoDiagnostic("required", "At least one source description is required", "/sourceDescriptions")
        if (document.workflows.isEmpty()) out += ArazzoDiagnostic("required", "At least one workflow is required", "/workflows")
        unique(document.sourceDescriptions.map { it.name }, "/sourceDescriptions", "source-name", out)
        unique(document.workflows.map { it.workflowId }, "/workflows", "workflow-id", out)
        val workflowIds = document.workflows.map { it.workflowId }.toSet()
        val sourceNames = document.sourceDescriptions.map { it.name }.toSet()
        document.sourceDescriptions.forEachIndexed { index, source ->
            if (source.type != "openapi") out += ArazzoDiagnostic("unsupported-source-type", "Only OpenAPI source descriptions are supported", "/sourceDescriptions/$index/type")
        }
        document.workflows.forEachIndexed { wi, workflow ->
            val base = "/workflows/$wi"
            if (workflow.inputs != null) out += ArazzoDiagnostic("unsupported-input-schema", "JSON Schema 2020-12 validation is not implemented; workflow inputs are rejected", "$base/inputs")
            if (workflow.dependsOn.isNotEmpty()) out += ArazzoDiagnostic("unsupported-feature", "Workflow dependencies are outside the sequential HTTP profile", "$base/dependsOn")
            unique(workflow.steps.map { it.stepId }, "$base/steps", "step-id", out)
            val workflowStepIds = workflow.steps.map { it.stepId }.toSet()
            val earlier = mutableSetOf<String>()
            workflow.steps.forEachIndexed { si, step ->
                validateStep(step, workflow, wi, si, earlier, sourceNames, workflowIds, out)
                earlier += step.stepId
            }
            workflow.outputs.orEmpty().forEach { (name, expression) ->
                STEP_OUTPUT_REFERENCE.findAll(expression).map { it.groupValues[1] }
                    .filter { it !in workflowStepIds }
                    .forEach { id -> out += ArazzoDiagnostic("unresolved-step-output", "Workflow output references unknown step '$id'", "$base/outputs/${escape(name)}") }
                if (!isSupportedArazzoExpressionSyntax(expression)) {
                    out += ArazzoDiagnostic("unsupported-expression", "Workflow output uses an unsupported runtime expression", "$base/outputs/${escape(name)}")
                }
            }
        }
    }

    private fun validateStep(
        step: ArazzoStep, workflow: ArazzoWorkflow, wi: Int, si: Int, earlier: Set<String>,
        sourceNames: Set<String>, workflowIds: Set<String>, out: MutableList<ArazzoDiagnostic>,
    ) {
        val path = "/workflows/$wi/steps/$si"
        val refs = listOfNotNull(step.operationId, step.operationPath, step.workflowId)
        if (refs.size != 1) out += ArazzoDiagnostic("operation-reference", "Exactly one operationId or operationPath is required; workflowId execution is unsupported", path)
        if (step.operationPath != null) out += ArazzoDiagnostic("unsupported-feature", "operationPath is not supported by the initial profile", "$path/operationPath")
        if (step.workflowId != null) out += ArazzoDiagnostic("unsupported-feature", "Nested workflow execution is not supported", "$path/workflowId")
        if (step.dependsOn.isNotEmpty()) out += ArazzoDiagnostic("unsupported-feature", "Explicit dependencies are not supported by the sequential profile", "$path/dependsOn")
        step.parameters.forEachIndexed { pi, parameter ->
            if (parameter.`in` !in setOf("path", "query", "header", "cookie")) out += ArazzoDiagnostic("unsupported-feature", "Parameter location must be path, query, header, or cookie", "$path/parameters/$pi/in")
            validateExpressionValues(parameter.value, "$path/parameters/$pi/value", out)
        }
        step.requestBody?.let { validateExpressionValues(it, "$path/requestBody", out) }
        step.parameters.withIndex().groupBy { (_, parameter) ->
            parameter.`in` to if (parameter.`in` == "header") parameter.name.lowercase() else parameter.name
        }.values.filter { it.size > 1 }.forEach { duplicates ->
            duplicates.drop(1).forEach { (pi, _) ->
                out += ArazzoDiagnostic("duplicate-parameter", "Duplicate parameter name and location", "$path/parameters/$pi")
            }
        }
        if (step.successCriteria?.isEmpty() == true) {
            out += ArazzoDiagnostic("invalid-success-criteria", "successCriteria must contain at least one criterion", "$path/successCriteria")
        }
        step.successCriteria?.forEachIndexed { ci, criterion ->
            if ((criterion.type != null && criterion.type != "simple") || criterion.context != null) {
                out += ArazzoDiagnostic("unsupported-feature", "Only simple condition criteria are supported", "$path/successCriteria/$ci")
            }
            if (!isSupportedArazzoCriterionSyntax(criterion.condition)) {
                out += ArazzoDiagnostic("unsupported-criterion", "Condition is outside the supported simple comparison grammar", "$path/successCriteria/$ci/condition")
            }
        }
        step.outputs.orEmpty().forEach { (name, expression) ->
            if (!isSupportedArazzoExpressionSyntax(expression)) {
                out += ArazzoDiagnostic("unsupported-expression", "Step output uses an unsupported runtime expression", "$path/outputs/${escape(name)}")
            }
        }
        // Step output expressions may only refer to completed steps in this sequential profile.
        step.outputs.orEmpty().forEach { (name, expression) ->
            val refsInExpression = STEP_OUTPUT_REFERENCE.findAll(expression).map { it.groupValues[1] }
            refsInExpression.filter { it !in earlier }.forEach { id ->
                out += ArazzoDiagnostic("unresolved-step-output", "Step output '$id' is unresolved or forward-referenced", "$path/outputs/${escape(name)}")
            }
        }
        step.operationId?.let { reference ->
            SOURCE_REFERENCE.findAll(reference).forEach { match ->
                val name = match.groupValues[1]
                if (name !in sourceNames) out += ArazzoDiagnostic("unresolved-source", "Unknown source description '$name'", "$path/operationId")
            }
        }
        if (step.workflowId != null && step.workflowId !in workflowIds) out += ArazzoDiagnostic("unresolved-workflow", "Unknown workflow '${step.workflowId}'", "$path/workflowId")
    }

    private fun validateExpressionValues(value: JsonElement, path: String, out: MutableList<ArazzoDiagnostic>) {
        when (value) {
            is JsonObject -> value.forEach { (key, child) -> validateExpressionValues(child, "$path/${escape(key)}", out) }
            is JsonArray -> value.forEachIndexed { index, child -> validateExpressionValues(child, "$path/$index", out) }
            is JsonPrimitive -> if (value.isString && (value.content.startsWith("$") || value.content.contains("{$")) &&
                !isSupportedArazzoExpressionSyntax(value.content)
            ) {
                out += ArazzoDiagnostic("unsupported-expression", "Mapping value uses an unsupported runtime expression", path)
            }
        }
    }

    private fun unique(values: List<String>, path: String, code: String, out: MutableList<ArazzoDiagnostic>) {
        values.withIndex().groupBy { it.value }.filterValues { it.size > 1 }.values.flatten().drop(1).forEach {
            out += ArazzoDiagnostic("duplicate-$code", "Duplicate identifier '${it.value}'", "$path/${it.index}")
        }
    }

    private fun inspectUnknown(value: JsonElement, path: String, allowed: Set<String>, out: MutableList<ArazzoDiagnostic>) {
        val obj = value as? JsonObject ?: return
        obj.keys.filter { it !in allowed && !it.startsWith("x-") }.forEach {
            out += ArazzoDiagnostic("unsupported-feature", "Unrecognized field '$it' is rejected by the execution profile", "$path/${escape(it)}")
        }
        // Known extensions at any parsed object are allowed as inert data; reject executable extensions.
        obj.keys.filter { it.startsWith("x-dicentra-aviator-") }.forEach {
            out += ArazzoDiagnostic("unsupported-extension", "Aviator execution extensions require a separately specified opt-in profile", "$path/${escape(it)}")
        }
        obj.forEach { (key, child) ->
            val childAllowed = when (key) {
                "info" -> setOf("title", "version", "summary", "description")
                "sourceDescriptions" -> SOURCE_KEYS
                "workflows" -> WORKFLOW_KEYS
                "steps" -> STEP_KEYS
                "parameters" -> PARAMETER_KEYS
                "successCriteria" -> CRITERION_KEYS
                "components" -> COMPONENT_KEYS
                "inputs" -> emptySet()
                else -> emptySet()
            }
            if (child is JsonObject && key !in setOf("inputs", "outputs")) inspectUnknown(child, "$path/${escape(key)}", childAllowed, out)
            else if (child is kotlinx.serialization.json.JsonArray) child.forEachIndexed { index, item ->
                if (item is JsonObject) inspectUnknown(item, "$path/${escape(key)}/$index", childAllowed, out)
            }
        }
    }

    private fun retainExtensions(document: ArazzoDocument, root: JsonObject) {
        // Keep extension maps inert and faithfully represent their original JSON values.
        document.extensions = root.extensionValues()
        (root["info"] as? JsonObject)?.let { document.info.extensions = it.extensionValues() }
        val sourceElements = root["sourceDescriptions"] as? kotlinx.serialization.json.JsonArray
        document.sourceDescriptions.forEachIndexed { index, model ->
            (sourceElements?.getOrNull(index) as? JsonObject)?.let { model.extensions = it.extensionValues() }
        }
        val workflowElements = root["workflows"] as? kotlinx.serialization.json.JsonArray
        document.workflows.forEachIndexed { wi, workflow ->
            val workflowObject = workflowElements?.getOrNull(wi) as? JsonObject ?: return@forEachIndexed
            workflow.extensions = workflowObject.extensionValues()
            val steps = workflowObject["steps"] as? kotlinx.serialization.json.JsonArray
            workflow.steps.forEachIndexed { si, step ->
                (steps?.getOrNull(si) as? JsonObject)?.let { step.extensions = it.extensionValues() }
            }
        }
    }

    private fun JsonObject.extensionValues(): Map<String, JsonElement> = filterKeys { it.startsWith("x-") }
    private fun invalid(code: String, message: String, location: String) = ArazzoValidationResult.Invalid(listOf(ArazzoDiagnostic(code, message, location)))
    private fun escape(segment: String) = segment.replace("~", "~0").replace("/", "~1")

    private val ROOT_KEYS = setOf("arazzo", "\$self", "info", "sourceDescriptions", "workflows", "components")
    private val SOURCE_REFERENCE = Regex("\\${'$'}sourceDescriptions\\.([A-Za-z0-9_-]+)")
    private val STEP_OUTPUT_REFERENCE = Regex("\\${'$'}steps\\.([A-Za-z0-9_-]+)\\.outputs")
    private val SOURCE_KEYS = setOf("name", "url", "type")
    private val WORKFLOW_KEYS = setOf("workflowId", "steps", "inputs", "outputs", "dependsOn", "summary", "description")
    private val STEP_KEYS = setOf("stepId", "operationId", "operationPath", "workflowId", "parameters", "requestBody", "successCriteria", "outputs", "dependsOn", "description")
    private val PARAMETER_KEYS = setOf("name", "in", "value")
    private val CRITERION_KEYS = setOf("condition", "context", "type")
    private val COMPONENT_KEYS = setOf("inputs", "parameters")
}
