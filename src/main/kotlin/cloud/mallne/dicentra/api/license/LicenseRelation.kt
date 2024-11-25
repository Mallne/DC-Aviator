package cloud.mallne.dicentra.api.license

import cloud.mallne.dicentra.api.user.UserCommons

interface LicenseRelation {
    val users: Set<UserCommons>
    val parent: LicenseCommons?
    val children: Set<LicenseCommons>
}