package cloud.mallne.dicentra.api.license

import java.util.UUID

object LicenseKeyGen {
    fun generateKey(): String {
        val uuid = UUID.randomUUID()
        return uuid.toString()
    }
}