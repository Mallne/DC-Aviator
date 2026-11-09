package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.core.ExtensionLocator
import cloud.mallne.dicentra.aviator.koas.typed.Route
import io.ktor.openapi.GenericElementWrapper
import io.ktor.openapi.Operation
import kotlinx.serialization.serializer

/** OpenAPI extensions owned by Synapse, not by Aviator's generic service contract. */
object SynapseExtensionSpec {
    object AggregateContracts {
        val O = ExtensionLocator("x-dicentra-synapse-aggregateContracts", Operation::extensions)
        val DSL = ExtensionLocator("x-dicentra-synapse-aggregateContracts", Operation.Builder::extensions)
        val R = ExtensionLocator("x-dicentra-synapse-aggregateContracts", Route::extensions)
    }

    val Operation.`x-dicentra-synapse-aggregateContracts`: List<AggregateServiceContract>
        get() = AggregateContracts.O.findComplex(this)?.deserialize(serializer<List<AggregateServiceContract>>())
            ?: emptyList()

    var Operation.Builder.`x-dicentra-synapse-aggregateContracts`: List<AggregateServiceContract>
        get() = AggregateContracts.DSL.findComplex(this)?.deserialize(serializer<List<AggregateServiceContract>>())
            ?: emptyList()
        set(value) {
            extensions[AggregateContracts.DSL.key] = GenericElementWrapper(value, serializer<List<AggregateServiceContract>>())
        }

    val Route.`x-dicentra-synapse-aggregateContracts`: List<AggregateServiceContract>
        get() = AggregateContracts.R.findComplex(this)?.deserialize(serializer<List<AggregateServiceContract>>())
            ?: emptyList()
}
