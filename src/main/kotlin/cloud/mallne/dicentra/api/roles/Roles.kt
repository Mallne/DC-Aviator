package cloud.mallne.dicentra.api.roles

object Roles {
    fun defaultRoleSet(): Set<String> {
        return setOf()
    }

    fun adminRoleSet(): Set<String> {
        return setOf(
            ADMIN,
            Warden.User.QUERY,
            Warden.User.PURGE,
            Warden.User.EDIT,
            Warden.Settings.QUERY,
            Warden.License.ADD,
            Warden.License.QUERY,
            Warden.License.PURGE,
            Warden.License.EDIT,
            Warden.License.DELETE,
        )
    }

    const val ADMIN = "admin"

    object Warden {
        const val ACCESS = "warden.access"

        object User {
            const val QUERY = "warden.user.query"
            const val PURGE = "warden.user.purge"
            const val EDIT = "warden.user.edit"
        }

        object Settings {
            const val QUERY = "warden.settings.query"
        }

        object License {
            const val ADD = "warden.license.add"
            const val QUERY = "warden.license.query"
            const val PURGE = "warden.license.purge"
            const val EDIT = "warden.license.edit"
            const val DELETE = "warden.license.delete"
        }
    }

    object AreaAssist {
        const val ACCESS = "areaassist.access"

        object Management

        object App {
            const val ACCESS = "areaassist.app.access"
        }
    }

    object Aviator {
        const val ACCESS = "aviator.access"
    }

    object Herald {
        const val ACCESS = "herald.access"
    }

    object Architect {
        const val ACCESS = "architect.access"
    }

    object Venatio {
        const val ACCESS = "venatio.access"
    }
}