package cloud.mallne.dicentra.aviator.core.io

import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

interface NetworkRequest : NetworkMessage {
    val method: HttpMethod
    val url: Url
    val outgoingContent: JsonElement?
}