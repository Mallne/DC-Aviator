package cloud.mallne.dicentra.api.settings

import cloud.mallne.dicentra.api.roles.Roles
import cloud.mallne.dicentra.api.settings.modules.vn.Instance

object Settings {
    object DC {
        object Dashboard {
            object OmitMenu {
                val AA =
                    SettingsDefinition.from(
                        "dc.dashboard.omitmenu.aa",
                        false,
                        requiresRole = setOf(Roles.AreaAssist.ACCESS)
                    )
                val AV =
                    SettingsDefinition.from(
                        "dc.dashboard.omitmenu.av",
                        false,
                        requiresRole = setOf(Roles.Aviator.ACCESS)
                    )
                val HL =
                    SettingsDefinition.from(
                        "dc.dashboard.omitmenu.hl",
                        false,
                        requiresRole = setOf(Roles.Herald.ACCESS)
                    )
                val WD =
                    SettingsDefinition.from(
                        "dc.dashboard.omitmenu.wd",
                        false,
                        requiresRole = setOf(Roles.Warden.ACCESS)
                    )
                val AT =
                    SettingsDefinition.from(
                        "dc.dashboard.omitmenu.at",
                        false,
                        requiresRole = setOf(Roles.Architect.ACCESS)
                    )
                val VN =
                    SettingsDefinition.from(
                        "dc.dashboard.omitmenu.vn",
                        false,
                        requiresRole = setOf(Roles.Venatio.ACCESS)
                    )
            }
        }

        object AA {
            val AutoSync =
                SettingsDefinition.from("dc.aa.autosync", false, requiresRole = setOf(Roles.AreaAssist.App.ACCESS))
        }

        object AV {
            object Display {
                val All =
                    SettingsDefinition.from("dc.av.display.all", false, requiresRole = setOf(Roles.Aviator.ACCESS))
            }
        }

        object VN {
            val Instance =
                SettingsDefinition.from(
                    "dc.vn.instance",
                    emptyList<Instance>(),
                    requiresRole = setOf(Roles.Venatio.ACCESS)
                )
        }
    }

    val allSettings: Set<SettingsDefinition> = setOf(
        DC.Dashboard.OmitMenu.AA,
        DC.Dashboard.OmitMenu.AV,
        DC.Dashboard.OmitMenu.HL,
        DC.Dashboard.OmitMenu.WD,
        DC.Dashboard.OmitMenu.AT,
        DC.Dashboard.OmitMenu.VN,
        DC.AA.AutoSync,
        DC.AV.Display.All,
        DC.VN.Instance
    )
}