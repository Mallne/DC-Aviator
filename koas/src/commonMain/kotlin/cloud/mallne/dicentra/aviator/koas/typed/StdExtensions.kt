package cloud.mallne.dicentra.aviator.koas.typed

internal fun String.segments(): List<String> =
    replace(Regex("\\{.*?\\}"), "").split("/").filter { it.isNotEmpty() }