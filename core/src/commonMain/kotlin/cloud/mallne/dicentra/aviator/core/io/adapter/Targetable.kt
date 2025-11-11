package cloud.mallne.dicentra.aviator.core.io.adapter

import kotlin.reflect.KClass

interface Targetable<Target : Any> {
    val target: KClass<Target>
}