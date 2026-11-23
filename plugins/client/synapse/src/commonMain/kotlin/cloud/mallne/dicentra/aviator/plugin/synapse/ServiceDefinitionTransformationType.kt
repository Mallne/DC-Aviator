package cloud.mallne.dicentra.aviator.plugin.synapse

import kotlinx.serialization.Serializable

@Serializable
enum class ServiceDefinitionTransformationType {
    Auto, Native, Catalyst;

    companion object {
        fun fromString(value: String): ServiceDefinitionTransformationType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Auto
    }
}
