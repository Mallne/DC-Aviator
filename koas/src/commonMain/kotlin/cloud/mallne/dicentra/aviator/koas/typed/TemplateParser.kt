package cloud.mallne.dicentra.aviator.koas.typed

import cloud.mallne.dicentra.aviator.koas.Style
import cloud.mallne.dicentra.aviator.koas.exceptions.IngestArgumentViolation
import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPIConstraintViolation
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import cloud.mallne.dicentra.aviator.koas.servers.Server
import cloud.mallne.dicentra.polyfill.ensure
import cloud.mallne.dicentra.polyfill.ensureNotNull

object TemplateParser {
    val regex = Regex("\\{\\s*?([;.])?\\s*?(\\S*?)\\s*?([*]?)\\s*?\\}")

    fun Server.parsePath(data: Map<String, List<String>>): String {
        var ppath = url
        val matches = regex.findAll(url)//Groups
        matches.forEach { match ->
            val (prefix, template, postfix) = match.destructured
            val pparam = variables[template]
            ensureNotNull(pparam) {
                OpenAPIConstraintViolation("Parameter $template not found")
            }
            val replace = data[template] ?: listOf(pparam.default)
            val replacement = genReplacement(replace, template)
            ppath = ppath.replace(match.value, replacement)
        }
        return ppath
    }

    fun Route.parsePath(data: Map<String, List<String>>): String {
        var ppath = path
        val matches = regex.findAll(path)//Groups
        matches.forEach { match ->
            val (prefix, template, postfix) = match.destructured
            val pparam = parameter.find { it.name == template }
            ensureNotNull(pparam) {
                OpenAPIConstraintViolation("Parameter $template not found")
            }

            val (style, explode) = styleExplodePath(pparam, prefix, template, postfix)
            val replace = data[template]
            ensureNotNull(replace) {
                IngestArgumentViolation("Make sure to pass an element for template-parameter $template")
            }
            val replacement = genReplacement(replace, template, style, explode)
            ppath = ppath.replace(match.value, replacement)
        }
        val queryString = mutableListOf<String>()
        val queryParams = parameter.filter { it.input == Parameter.Input.Query }
        queryParams.forEach { param ->
            if (param.required) {
                ensure(data.containsKey(param.name)) {
                    IngestArgumentViolation("Parameter ${param.name} passed as a data Parameter. This is required")
                }
            }
            val template = data[param.name]
            if (template != null) {
                val style = param.style ?: Style.form
                val explode = param.explode == true
                queryString += genReplacement(template, param.name, style, explode)
            }
        }
        return if (queryString.isNotEmpty()) {
            "$ppath?${queryString.joinToString("&")}"
        } else {
            ppath
        }
    }

    private fun genReplacement(
        replace: List<String>,
        template: String,
        style: Style = Style.simple,
        explode: Boolean = false
    ): String {
        return when (style) {
            //Path
            Style.simple -> replace.joinToString(",")
            Style.label -> if (explode) {
                replace.joinToString("") { ".$it" }
            } else {
                ".${replace.joinToString(",")}"
            }

            Style.matrix -> if (explode) {
                replace.joinToString("") { ";$template=$it" }
            } else {
                ";$template=${replace.joinToString(",")}"
            }

            Style.form -> if (explode) {
                replace.joinToString("&") { "$template=$it" }
            } else {
                "$template=${replace.joinToString(",")}"
            }

            Style.spaceDelimited -> if (explode) {
                replace.joinToString("&") { "$template=$it" }
            } else {
                "$template=${replace.joinToString("%20")}"
            }

            Style.pipeDelimited -> if (explode) {
                replace.joinToString("&") { "$template=$it" }
            } else {
                "$template=${replace.joinToString("|")}"
            }

            Style.deepObject -> TODO("Aviator does not Support Objects as Parameters yet!")
        }
    }

    private fun styleExplodePath(
        param: Parameter,
        prefix: String,
        template: String,
        postfix: String
    ): Pair<Style, Boolean> {
        if (param.style != null) {
            var isNot = " " // A space will always be true, hence it is not a valid parameter
            if (param.style == Style.label) {
                isNot = ";"
            }
            if (param.style == Style.matrix) {
                isNot = "."
            }
            ensure(prefix != isNot) {
                OpenAPIConstraintViolation("Prefix $prefix for parameter $template is Wrong. Please make sure to use the Right prefix for the ${param.style.name} style.")
            }
        }
        if (param.explode != null) {
            ensure(param.explode == (postfix == "*") || postfix.isBlank()) {
                OpenAPIConstraintViolation("Postfix $postfix is Wrong. Please make sure it is omitted or uses the right Postfix for explode: ${param.explode}!")
            }
        }
        val style: Style = param.style ?: when (prefix) {
            "." -> Style.label
            ";" -> Style.matrix
            else -> Style.simple
        }
        val explode: Boolean = param.explode ?: (postfix == "*")

        return style to explode
    }
}