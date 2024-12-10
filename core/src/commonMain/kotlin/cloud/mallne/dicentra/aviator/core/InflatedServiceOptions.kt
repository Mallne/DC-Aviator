package cloud.mallne.dicentra.aviator.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

interface InflatedServiceOptions {
    fun usable(): ServiceOptions {
        return Json.encodeToJsonElement(this)
    }


    companion object {
        inline fun <reified T : InflatedServiceOptions> inflate(options: ServiceOptions): T {
            return Json.decodeFromJsonElement(options)
        }

        val empty = object : InflatedServiceOptions {}
    }
}

typealias ServiceOptions = JsonElement