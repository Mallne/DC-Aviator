package cloud.mallne.dicentra.api.annotations

import kotlin.reflect.KClass

annotation class ElementCollection(
    val targetClass: KClass<*> = Any::class,
)
