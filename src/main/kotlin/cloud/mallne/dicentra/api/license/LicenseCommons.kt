package cloud.mallne.dicentra.api.license

import cloud.mallne.dicentra.api.Modules
import cloud.mallne.dicentra.api.Principal
import cloud.mallne.dicentra.api.settings.KeyValueSetting
import java.time.LocalDate

interface LicenseCommons: Principal {
    val key: String
    val forcedSettings: Set<KeyValueSetting>
    val boundRoles: Set<String>
    val grantsAccessTo: Set<Modules>
    val validUntil: LocalDate
    val maxActivations: Int
    val licenseType: LicenseType
    val autoRenewable: Boolean
    val renewPeriod: String

    fun isExpired(): Boolean {
        return validUntil.isBefore(LocalDate.now())
    }

    fun isValid(): Boolean {
        return !isExpired()
    }
}