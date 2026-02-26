package cloud.mallne.dicentra.aviator.koas.info

import kotlinx.serialization.Serializable

@Serializable
@Suppress("EnumEntryName")
enum class TagKind {
    audience, badge, nav
}