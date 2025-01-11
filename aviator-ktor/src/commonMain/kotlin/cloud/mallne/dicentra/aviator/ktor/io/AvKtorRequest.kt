package cloud.mallne.dicentra.aviator.ktor.io

import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.NetworkRequest
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

class AvKtorRequest(
    override val method: HttpMethod,
    override val url: Url,
    override val outgoingContent: JsonElement?,
    override val headers: NetworkHeader
) : NetworkRequest