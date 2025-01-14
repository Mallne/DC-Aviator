package cloud.mallne.dicentra.aviator.ktor.io

import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.NetworkRequest
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

class AvKtorRequest(
    override var method: HttpMethod,
    override var url: Url,
    override var outgoingContent: JsonElement?,
    override var headers: NetworkHeader
) : NetworkRequest<NetworkHeader>