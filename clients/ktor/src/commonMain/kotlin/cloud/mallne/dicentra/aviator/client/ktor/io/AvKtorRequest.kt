package cloud.mallne.dicentra.aviator.client.ktor.io

import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.NetworkRequest
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

class AvKtorRequest(
    override var method: HttpMethod,
    override var url: Url,
    override var outgoingContent: NetworkBody = NetworkBody.Empty,
    override var headers: NetworkHeader,
    override var contentType: ContentType
) : NetworkRequest<NetworkHeader>