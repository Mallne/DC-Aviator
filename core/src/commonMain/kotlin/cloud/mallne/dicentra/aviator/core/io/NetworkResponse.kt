package cloud.mallne.dicentra.aviator.core.io

import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

interface NetworkResponse : NetworkMessage {
    val status: HttpStatusCode
    val content: JsonElement?
    val time: GMTDate

    fun <T : @Serializable Any> parseBody(serializer: KSerializer<T>, json: Json = Json): T? {
        return content?.let { json.decodeFromJsonElement(serializer, it) }
    }
}

