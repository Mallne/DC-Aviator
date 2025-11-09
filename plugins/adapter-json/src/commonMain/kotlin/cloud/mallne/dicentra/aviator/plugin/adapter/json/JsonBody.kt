package cloud.mallne.dicentra.aviator.plugin.adapter.json

import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import kotlinx.serialization.json.JsonElement

data class JsonBody(var json: JsonElement) : NetworkBody