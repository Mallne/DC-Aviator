package cloud.mallne.dicentra.aviator.core.model

import cloud.mallne.dicentra.aviator.model.License

interface IInfo {
    val title: String
    val description: String?
    val license: License?
    val version: String
}