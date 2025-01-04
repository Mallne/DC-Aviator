package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.ServiceMethods
import kotlinx.serialization.Serializable

@Serializable
data class ServiceLocator(
    val locator: String,
    val flavour: ServiceMethods,
) {
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
