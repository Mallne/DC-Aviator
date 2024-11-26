package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.IAviatorService
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.core.model.IParameter
import cloud.mallne.dicentra.aviator.core.model.Insides
import cloud.mallne.dicentra.aviator.exceptions.ServiceException

object AviatorServiceUtils {
    inline fun <reified T : InflatedServiceOptions> optionBundle(options: ServiceOptions): T =
        InflatedServiceOptions.inflate<T>(options)

    fun <Response> getAddress(
        options: RequestServiceOptions<Response>,
        serviceURL: String,
        pathParams: List<IParameter>
    ): String {
        val pathItems = serviceURL.split("/")
        val pis = pathItems.map { pi ->
            val template = template(pi)
            if (template != null) {
                val param = pathParams.find { it.name == template }
                if (param != null) {
                    val vl = options.parameters[param.name]
                    if (vl != null) {
                        pi.replace("{$template}", vl)
                    }
                }
            }
            pi
        }.toMutableList()

        //fabricate the Query String
        val params =
            pathParams.filter { it.`in` == Insides.query }
        val optionsMap = params.mapNotNull {
            val vl = options.parameters[it.name]
            if (vl != null) {
                it.name to vl
            } else {
                null
            }
        }.toMap()
        val optionsString = optionsMap.map { "${it.key}=${it.value}" }.joinToString("&")

        return if (optionsString.isNotEmpty()) {
            "${pis.joinToString("/")}?${optionsString}"
        } else {
            pis.joinToString("/")
        }
    }

    fun validate(service: IAviatorService) {
        validatePathParams(service)
    }

    fun validatePathParams(service: IAviatorService) {
        if (!service.pathParams.all { it.`in` == Insides.path || it.`in` == Insides.query }) throw ServiceException(
            "Not all path params are specified as such"
        )

        val pathParms = service.pathParams.filter { it.`in` == Insides.path }
        val paths = service.path.split("/")
        val params = paths.mapNotNull { template(it) }
        if (params.size > pathParms.size) throw ServiceException("Not all path params are specified")
        params.forEach { param ->
            if (!pathParms.map { it.name }
                    .contains(param)) throw ServiceException("Path param $param is not specified")
        }
    }

    fun template(string: String): String? {
        return if (string.contains("{") && string.contains("}")) {
            // get the string between the braces
            string.substring(string.indexOf("{") + 1, string.indexOf("}"))
        } else {
            null
        }
    }
}
