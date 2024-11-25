package cloud.mallne.dicentra.api.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


interface KeyValueSetting {
    @Serializable
    val key: String

    @Serializable
    val value: JsonElement

    @Serializable
    val forcedByLicense: Boolean
}


