package cloud.mallne.dicentra.api.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

open class SettingsDefinition(
    val key: String,
    @Serializable
    val defaultVal: JsonElement,
    val forcedByLicense: Boolean = false,
    val requiresRole: Set<String> = setOf(),
) {
    fun toKV(va: JsonElement? = defaultVal, forced: Boolean = forcedByLicense): KeyValueSetting? {
        if (va == null) return null
        return object : KeyValueSetting {
            override val forcedByLicense: Boolean = forced
            override val value: JsonElement = Json.encodeToJsonElement(va)
            override val key: String = this@SettingsDefinition.key
        }
    }

    companion object {
        inline fun <reified T> from(
            key: String,
            defaultVal: T,
            forcedByLicense: Boolean = false,
            requiresRole: Set<String> = setOf(),
        ): SettingsDefinition = SettingsDefinition(
            key,
            defaultVal = Json.encodeToJsonElement(defaultVal),
            forcedByLicense,
            requiresRole
        )
    }
}