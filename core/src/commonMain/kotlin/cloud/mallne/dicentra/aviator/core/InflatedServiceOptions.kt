package cloud.mallne.dicentra.aviator.core

import kotlinx.serialization.json.*

interface InflatedServiceOptions {
    fun usable(): ServiceOptions {
        return Json.encodeToJsonElement(this).jsonObject
    }


    companion object {
        inline fun <reified T : InflatedServiceOptions> inflate(options: ServiceOptions): T {
            return Json.decodeFromJsonElement(options)
        }

        val empty = object : InflatedServiceOptions {}
    }
}

typealias ServiceOptions = JsonObject