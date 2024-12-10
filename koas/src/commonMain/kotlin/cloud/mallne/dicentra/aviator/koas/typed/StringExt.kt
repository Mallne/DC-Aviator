package cloud.mallne.dicentra.aviator.koas.typed

fun String.segments(): List<String> =
    replace(Regex("\\{.*?\\}"), "").split("/").filter { it.isNotEmpty() }
