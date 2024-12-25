package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.koas.OpenAPI

interface APIToServiceConverter {
    fun build(api: OpenAPI): IAviatorService
}