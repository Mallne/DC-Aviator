package cloud.mallne.dicentra.aviator.orchestration.arazzo.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Parsed Arazzo document data. Runtime/run state is deliberately kept elsewhere. */
@Serializable
data class ArazzoDocument(
    val arazzo: String,
    val info: ArazzoInfo,
    val sourceDescriptions: List<SourceDescription>,
    val workflows: List<ArazzoWorkflow>,
    val components: ArazzoComponents? = null,
    @SerialName("$" + "self") val self: String? = null,
    /** Caller-supplied document location; never used to fetch a source description. */
    @Transient var location: ArazzoDocumentLocation? = null,
    /** Specification extensions are inert data and are never interpreted as execution directives. */
    @Transient var extensions: Map<String, JsonElement> = emptyMap(),
)

/** Keeps retrieval, effective base and `$self` identity separate from provider endpoints. */
data class ArazzoDocumentLocation(
    val retrievalUri: String?,
    val baseUri: String?,
    val selfUri: String?,
)

@Serializable
data class ArazzoInfo(
    val title: String,
    val version: String,
    val summary: String? = null,
    val description: String? = null,
    @Transient var extensions: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class SourceDescription(
    val name: String,
    val url: String,
    val type: String = "openapi",
    @Transient var extensions: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ArazzoWorkflow(
    val workflowId: String,
    val steps: List<ArazzoStep>,
    val inputs: JsonObject? = null,
    val outputs: Map<String, String>? = null,
    val dependsOn: List<String> = emptyList(),
    val summary: String? = null,
    val description: String? = null,
    @Transient var documentLocation: ArazzoDocumentLocation? = null,
    @Transient var extensions: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ArazzoStep(
    val stepId: String,
    val operationId: String? = null,
    val operationPath: String? = null,
    val workflowId: String? = null,
    val parameters: List<ArazzoParameter> = emptyList(),
    val requestBody: JsonElement? = null,
    val successCriteria: List<ArazzoCriterion>? = null,
    val outputs: Map<String, String>? = null,
    val dependsOn: List<String> = emptyList(),
    val description: String? = null,
    @Transient var extensions: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ArazzoParameter(
    val name: String,
    val `in`: String? = null,
    val value: JsonElement,
    @Transient var extensions: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ArazzoCriterion(
    val condition: String,
    val context: String? = null,
    val type: String? = null,
    @Transient var extensions: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ArazzoComponents(
    val inputs: Map<String, JsonObject> = emptyMap(),
    val parameters: Map<String, ArazzoParameter> = emptyMap(),
    @Transient var extensions: Map<String, JsonElement> = emptyMap(),
)
