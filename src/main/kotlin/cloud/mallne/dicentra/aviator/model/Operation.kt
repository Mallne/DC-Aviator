package cloud.mallne.dicentra.aviator.model

import com.google.gson.annotations.Expose

/**
 * Describes a single API operation on a path.
 */
data class Operation(
    @Expose
    val `x-serviceDelegateCall`: ServiceLocator? = null,
    @Expose
    val `x-serviceOptions`: ServiceOptions? = null,
    @Expose
    val `x-requestType`: RequestType? = null,
    @Expose
    val summary: String? = null,
    @Expose
    val description: String? = null,
    @Expose
    val parameters: List<Parameter>? = null,
    @Expose
    val deprecated: Boolean = false,
)
