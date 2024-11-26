package cloud.mallne.dicentra.aviator.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement

interface InflatedServiceOptions {
    fun usable(): ServiceOptions {
        return gson.toJsonTree(this)
    }


    companion object {
        @Transient
        val gson: Gson
            get() = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

        inline fun <reified T : InflatedServiceOptions> inflate(options: ServiceOptions): T {
            return gson.fromJson(options, T::class.java)
        }

        val empty = object : InflatedServiceOptions {}
    }
}

typealias ServiceOptions = JsonElement