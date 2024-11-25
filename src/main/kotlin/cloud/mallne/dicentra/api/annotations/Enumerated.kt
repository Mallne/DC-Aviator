package cloud.mallne.dicentra.api.annotations

import cloud.mallne.dicentra.api.annotations.enums.EnumType

annotation class Enumerated (
    val type: EnumType = EnumType.STRING
)