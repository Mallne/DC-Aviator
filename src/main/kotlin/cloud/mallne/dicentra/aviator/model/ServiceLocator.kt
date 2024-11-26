package cloud.mallne.dicentra.aviator.model

import com.google.gson.annotations.Expose

data class ServiceLocator(
    @Expose
    val locator: String,
    @Expose
    val flavour: ServiceRegistry.ServiceMethods,
) {
    constructor(locatorString: String) : this(
        locator = locatorString.substringBefore(">"),
        flavour = ServiceRegistry.ServiceMethods.entries.find {
            it.name.equals(locatorString.substringAfter(">"), ignoreCase = true)
        } ?: ServiceRegistry.ServiceMethods.GATHER,
    )

    override fun toString(): String {
        return "$locator>${flavour.serviceFlavour}"
    }
}
