package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.io.*
import io.ktor.http.*
import io.ktor.util.date.GMTDate

internal fun defaultFetch(): dynamic = js("globalThis.fetch")

class FetchHeaders(override var values: RequestParameters = RequestParameters()) : NetworkHeader

class FetchRequest(
    override var method: HttpMethod,
    override var url: Url,
    override var outgoingContent: NetworkBody = NetworkBody.Empty,
    override var headers: FetchHeaders,
    override var contentType: ContentType? = null,
) : NetworkRequest<FetchHeaders>

class FetchResponse(
    override var status: HttpStatusCode,
    override var content: ByteArray?,
    override var time: GMTDate = GMTDate(),
    override var headers: FetchHeaders = FetchHeaders(),
    override var contentType: ContentType = ContentType.Any,
) : NetworkResponse<FetchHeaders>
