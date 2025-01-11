package cloud.mallne.dicentra.aviator.ktor.io

import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.NetworkResponse
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.serialization.json.JsonElement

class AvKtorResponse(
    ktorPrimitive: HttpResponse,
    override val status: HttpStatusCode = ktorPrimitive.status,
    override val content: JsonElement?,
    override val time: GMTDate = ktorPrimitive.responseTime,
    override val headers: NetworkHeader = object : NetworkHeader {
        override val values: Map<String, List<String>> = ktorPrimitive.headers.entries().convertToMap()
    },
) : NetworkResponse