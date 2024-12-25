package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.IAviatorService
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.ServiceArguments
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.exceptions.ServiceException
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter

object AviatorServiceUtils {
    inline fun <reified T : InflatedServiceOptions> optionBundle(options: ServiceOptions): T =
        InflatedServiceOptions.inflate<T>(options)

    fun getAddress(
        parameters: ServiceArguments,
        serviceURL: String,
        pathParams: List<Parameter>
    ): String {
        val pathItems = serviceURL.split("/")
        val pis = pathItems.map { pi ->
            val template = template(pi)
            if (template != null) {
                val param = pathParams.find { it.name == template }
                if (param != null) {
                    val vl = parameters[param.name]
                    if (vl != null) {
                        pi.replace("{$template}", vl)
                    }
                }
            }
            pi
        }.toMutableList()

        //fabricate the Query String
        val params =
            pathParams.filter { it.input == Parameter.Input.Query }
        val optionsMap = params.mapNotNull {
            val vl = parameters[it.name]
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
        if (!service.pathParams.all { it.input == Parameter.Input.Path || it.input == Parameter.Input.Query }) throw ServiceException(
            "Not all path params are specified as such"
        )

        val pathParms = service.pathParams.filter { it.input == Parameter.Input.Path }
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
