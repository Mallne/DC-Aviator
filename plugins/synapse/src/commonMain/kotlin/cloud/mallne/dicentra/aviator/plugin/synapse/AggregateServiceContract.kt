package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.plugin.synapse.SynapseExtensionSpec.`x-dicentra-synapse-aggregateContracts`
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.ExternalDocs
import kotlinx.serialization.Serializable

/** A constituent's public contract, not a separately executable service definition. */
@Serializable
data class AggregateServiceContract(
    val identity: String,
    val info: OpenApiInfo,
    val options: ServiceOptions? = null,
    val externalDocs: ExternalDocs? = null,
)

/** Constituents remain metadata; reading them never expands the execution service list. */
val AviatorServiceDataHolder.aggregateContracts: List<AggregateServiceContract>
    get() = route.`x-dicentra-synapse-aggregateContracts`
