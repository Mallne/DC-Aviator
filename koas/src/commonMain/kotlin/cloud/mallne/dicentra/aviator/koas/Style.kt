package cloud.mallne.dicentra.aviator.koas

import kotlinx.serialization.Serializable

@Serializable
@Suppress("EnumEntryName")
enum class Style {
    simple,
    form,
    matrix,
    label,
    spaceDelimited,
    pipeDelimited,
    deepObject
}
