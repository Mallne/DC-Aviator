package cloud.mallne.dicentra.aviator.model

import com.google.gson.annotations.Expose

/**
 * This is the root document object of the OpenAPI document.
 */
data class AviatorAPI(
    @Expose
    val `x-dicentra-aviator`: String = "1.0.0", // Required: Aviator version (e.g., "1.0.1")
    @Expose
    val info: Info, // Required: Metadata about the API
    @Expose
    val server: Server, // server where the API is hosted
    @Expose
    val paths: Map<String, PathItem>, // Available paths and operations
) {
    fun buildServices(): List<AviatorService> {
        val services = mutableListOf<AviatorService>()
        for ((path, pathItem) in paths) {
            for ((method, operation) in pathItem.getOperations()) {
                if (operation != null) {
                    if (
                        operation.`x-serviceDelegateCall` != null &&
                        operation.`x-serviceOptions` != null &&
                        operation.`x-requestType` != null
                    ) {
                        val parameters = mutableListOf<Parameter>()
                        if (pathItem.parameters != null) {
                            parameters.addAll(pathItem.parameters)
                        }
                        if (operation.parameters != null) {
                            parameters.addAll(operation.parameters)
                        }

                        val filtered =
                            parameters.filter { it.`in` == Parameter.Companion.Insides.query || it.`in` == Parameter.Companion.Insides.path }
                        services.add(
                            AviatorService(
                                serviceLocator = operation.`x-serviceDelegateCall`,
                                options = operation.`x-serviceOptions`,
                                requestType = operation.`x-requestType`,
                                server = server,
                                path = path,
                                pathParams = filtered,
                                method = method,
                            )
                        )
                    }
                }
            }
        }

        return services
    }

    fun getAvailableLicenses(): List<License> {
        val licenses = mutableListOf<License>()
        info.license?.let { licenses.add(it) }
        paths.forEach { (key, path) ->
            path.info?.license?.let { licenses.add(it) }
        }
        return licenses
    }
}
