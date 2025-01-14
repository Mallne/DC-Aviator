package cloud.mallne.dicentra.aviator.ktor.io

import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.json.JsonElement

class AvKtorResponse(
    ktorPrimitive: HttpResponse,
    override var status: HttpStatusCode = ktorPrimitive.status,
    override var content: JsonElement?,
    override var time: GMTDate = ktorPrimitive.responseTime,
    override var headers: NetworkHeader = object : NetworkHeader {
        override var values: Map<String, List<String>> = ktorPrimitive.headers.entries().convertToMap()
    },
) : NetworkResponse<NetworkHeader>