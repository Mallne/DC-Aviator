package cloud.mallne.dicentra.api.license

import cloud.mallne.dicentra.api.Modules
import cloud.mallne.dicentra.api.roles.Roles
import cloud.mallne.dicentra.api.settings.KeyValueSetting
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DefaultLicense : LicenseCommons {
    fun <T : KeyValueSetting> forcedSettingsOfType(): Set<T> {
        return emptySet()
    }

    override val key: String
        get() = LicenseKeyGen.generateKey()
    override val forcedSettings: Set<KeyValueSetting> = forcedSettingsOfType()
    override val boundRoles: Set<String> = Roles.defaultRoleSet()
    override val validUntil: LocalDate
        get() = LocalDate.now().plus(1, ChronoUnit.YEARS)
    override val maxActivations: Int
        get() = 1
    override val licenseType: LicenseType
        get() = LicenseType.SUBSCRIPTION
    override val autoRenewable: Boolean
        get() = true
    override val renewPeriod: String
        get() = "P1Y" // Has always to be a valid ISO 8601 duration
    override val grantsAccessTo: Set<Modules>
        get() = Modules.defaultModuleSet()

    val userLicense: LicenseCommons
        get() = this

}