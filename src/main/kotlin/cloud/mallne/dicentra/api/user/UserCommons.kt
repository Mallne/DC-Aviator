package cloud.mallne.dicentra.api.user

import cloud.mallne.dicentra.api.Principal
import cloud.mallne.dicentra.api.settings.KeyValueSetting

interface UserCommons: Principal {
    val identifier: String
    val settings: Set<KeyValueSetting>
}