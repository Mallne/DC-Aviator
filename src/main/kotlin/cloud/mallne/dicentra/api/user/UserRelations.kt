package cloud.mallne.dicentra.api.user

import cloud.mallne.dicentra.api.license.LicenseCommons

interface UserRelations {
    val licenses: Set<LicenseCommons>?
}