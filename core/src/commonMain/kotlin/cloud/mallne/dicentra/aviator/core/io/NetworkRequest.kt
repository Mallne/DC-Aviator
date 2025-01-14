package cloud.mallne.dicentra.aviator.core.io

import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

interface NetworkRequest<H : NetworkHeader> : NetworkMessage<H> {
    var method: HttpMethod
    var url: Url
    var outgoingContent: JsonElement?
}