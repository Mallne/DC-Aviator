package cloud.mallne.dicentra.aviator.orchestration.policy

import kotlinx.serialization.Serializable

/** Capability required by a definition or requested by a caller. */
@Serializable
data class OrchestrationCapability(
    val name: String,
    val version: String? = null,
)

@Serializable
enum class ExecutionMode { IN_PROCESS, REMOTE }

@Serializable
data class OrchestratorCapabilities(
    val formats: Set<WorkflowFormatCapability>,
    val executionModes: Set<ExecutionMode>,
    val features: Set<OrchestrationCapability> = emptySet(),
    val persistentRunLookup: Boolean = false,
    val resumableRuns: Boolean = false,
)

@Serializable
data class WorkflowFormatCapability(
    val format: String,
    val versions: Set<String>,
    val features: Set<OrchestrationCapability> = emptySet(),
)

@Serializable
data class CapabilityRequirements(
    val format: String,
    val formatVersion: String,
    val features: Set<OrchestrationCapability> = emptySet(),
    val executionMode: ExecutionMode = ExecutionMode.IN_PROCESS,
)

/** Pure preflight result; implementations should reject unsupported requirements before work starts. */
@Serializable
sealed interface CapabilityCheck {
    @Serializable
    data object Supported : CapabilityCheck

    @Serializable
    data class Unsupported(val reasons: List<String>) : CapabilityCheck
}

fun OrchestratorCapabilities.check(requirements: CapabilityRequirements): CapabilityCheck {
    val reasons = buildList {
        if (requirements.executionMode !in executionModes) add("Execution mode '${requirements.executionMode}' is unsupported")
        val format = formats.firstOrNull { it.format == requirements.format }
        if (format == null) {
            add("Workflow format '${requirements.format}' is unsupported")
        } else {
            if (requirements.formatVersion !in format.versions) {
                add("Workflow format version '${requirements.formatVersion}' is unsupported")
            }
            val available = features + format.features
            requirements.features.filterNot(available::contains).forEach {
                add("Workflow feature '${it.name}'${it.version?.let { version -> " version '$version'" } ?: ""} is unsupported")
            }
        }
    }
    return if (reasons.isEmpty()) CapabilityCheck.Supported else CapabilityCheck.Unsupported(reasons)
}
