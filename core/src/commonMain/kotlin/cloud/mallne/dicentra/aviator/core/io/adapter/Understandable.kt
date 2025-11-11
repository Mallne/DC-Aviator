package cloud.mallne.dicentra.aviator.core.io.adapter

import io.ktor.http.*

interface Understandable {
    fun understands(contentType: ContentType?): Boolean
}