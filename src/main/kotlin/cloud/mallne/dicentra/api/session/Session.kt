package cloud.mallne.dicentra.api.session

import cloud.mallne.dicentra.api.user.BaseUser

interface Session {
    val user: BaseUser
    val refresh: OIDCToken
    val access: OIDCToken
}