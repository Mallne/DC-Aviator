package cloud.mallne.dicentra.api

import cloud.mallne.dicentra.api.roles.Roles

enum class Modules(val module: String, val accessRole: String) {
    WARDEN("wd", Roles.Warden.ACCESS),
    AREAASSIST("aa", Roles.AreaAssist.ACCESS),
    AREAASSISTAPP("ap", Roles.AreaAssist.App.ACCESS),
    AVIATOR("av", Roles.Aviator.ACCESS),
    HERALD("he", Roles.Herald.ACCESS),
    ARCHITECT("ar", Roles.Architect.ACCESS);

    companion object {
        fun defaultModuleSet(): Set<Modules> {
            return setOf(
                WARDEN,
                AREAASSISTAPP,
            )
        }

        fun adminModuleSet(): Set<Modules> {
            return enumValues<Modules>().toSet()
        }
    }
}