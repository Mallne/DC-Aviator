package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.IServiceLocator
import cloud.mallne.dicentra.aviator.core.ServiceMethods
import kotlinx.serialization.Serializable

@Serializable
data class ServiceLocator(
    override val locator: String,
    override val flavour: ServiceMethods,
) : IServiceLocator {
    constructor(locatorString: String) : this(
        locator = locatorString.substringBefore(">"),
        flavour = ServiceMethods.entries.find {
            it.name.equals(locatorString.substringAfter(">"), ignoreCase = true)
        } ?: ServiceMethods.GATHER,
    )

    override fun toString(): String {
        return "$locator>${flavour.serviceFlavour}"
    }
}
