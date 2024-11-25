package cloud.mallne.dicentra.api.annotations

import cloud.mallne.dicentra.api.annotations.enums.GenerationType

annotation class GeneratedValue(
    val strategy: GenerationType = GenerationType.AUTO,
)
