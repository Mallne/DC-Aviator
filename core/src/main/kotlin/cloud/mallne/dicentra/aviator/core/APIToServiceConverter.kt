package cloud.mallne.dicentra.aviator.core

interface APIToServiceConverter {
    fun build(api: IAviatorApi): IAviatorService
}