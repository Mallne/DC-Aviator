package cloud.mallne.dicentra.aviator.core.io

import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

interface NetworkBody {
    object Empty : NetworkBody
    data class Text(var string: String) : NetworkBody
    data class Custom(var custom: Any) : NetworkBody
    data class Json(var json: JsonElement) : NetworkBody
    data class Form(var formData: List<Pair<String, String>>) : NetworkBody
}