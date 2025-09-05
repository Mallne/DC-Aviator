package cloud.mallne.dicentra.aviator.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

interface InflatedServiceOptions {
    fun usable(): ServiceOptions


    companion object {
        inline fun <reified T : InflatedServiceOptions> inflate(options: ServiceOptions, json: Json = Json): T {
            return json.decodeFromJsonElement(options)
        }

        @OptIn(InternalAviatorAPI::class)
        val empty = EmptyServiceOpts()

        @Serializable
        class EmptyServiceOpts @InternalAviatorAPI constructor() : InflatedServiceOptions {
            override fun usable(): ServiceOptions {
                return Json.parseToJsonElement("{}")
            }
        }
    }
}

typealias ServiceOptions = JsonElement