package cloud.mallne.dicentra.aviator.core

import io.ktor.openapi.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

interface InflatedServiceOptions {
    fun usable(): ServiceOptions = GenericElement(this)


    companion object {
        inline fun <reified T : InflatedServiceOptions> inflate(options: ServiceOptions, json: Json = Json): T {
            return options.deserialize(serializer(typeOf<T>())) as T
        }

        @OptIn(InternalAviatorAPI::class)
        val empty = EmptyServiceOpts()

        @Serializable
        class EmptyServiceOpts @InternalAviatorAPI constructor() : InflatedServiceOptions {
            override fun usable(): ServiceOptions {
                return GenericElement.EmptyObject
            }
        }
    }
}

typealias ServiceOptions = GenericElement