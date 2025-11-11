package cloud.mallne.dicentra.aviator.core.io

import io.ktor.http.*
import io.ktor.util.date.*

interface NetworkResponse<H : NetworkHeader> : NetworkMessage<H> {
    var status: HttpStatusCode
    var content: ByteArray?
    var time: GMTDate
    var contentType: ContentType
}

