package cloud.mallne.dicentra.api.session

import java.time.LocalDateTime

interface OIDCToken {
    val expires: LocalDateTime
    val data: String
    val expired: Boolean
    var expiresIn: Long
}