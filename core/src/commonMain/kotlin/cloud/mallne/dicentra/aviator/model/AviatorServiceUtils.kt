package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.InflatedServiceOptions
import cloud.mallne.dicentra.aviator.core.ServiceOptions
import cloud.mallne.dicentra.aviator.koas.typed.TemplateParser.parsePath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object AviatorServiceUtils {
    inline fun <reified T : InflatedServiceOptions> optionBundle(options: ServiceOptions): T =
        InflatedServiceOptions.inflate<T>(options)

    fun validate(service: AviatorServiceDataHolder) {

    }

    inline fun <reified T> manualPipeline(iterable: Iterable<T>, block: (element: T, next: () -> Unit) -> Unit) {
        var doAnotherOne = false
        val ne = {
            doAnotherOne = true
        }
        iterable.forEach { obj ->
            doAnotherOne = false
            block(obj, ne)
            if (!doAnotherOne) {
                return@forEach
            }
        }
    }

    fun catchPaths(
        dataHolder: AviatorServiceDataHolder,
        requestParams: Map<String, List<String>>
    ): List<String> {
        val pathSlug = dataHolder.route.parsePath(requestParams)
        val serverSlugs = dataHolder.oas.servers.map { "${it.parsePath(requestParams)}$pathSlug" }
        return serverSlugs
    }

    inline fun <reified T : @Serializable Any> makeClazzDefinition(
        clazz: KClass<T> = T::class,
        type: KType = typeOf<T>(),
        ser: KSerializer<T> = serializer<T>()
    ) = Triple(clazz, type, ser)
}
