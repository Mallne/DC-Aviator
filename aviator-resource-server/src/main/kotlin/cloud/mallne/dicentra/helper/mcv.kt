package cloud.mallne.dicentra.helper

import cloud.mallne.dicentra.aviator.core.execution.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.core.mock.MockConverter
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.info.Info

object mcv {
    fun bui() {
        val oas = OpenAPI(
            info = Info(
                title = "OpenAPI Title",
                version = AviatorExtensionSpec.SpecVersion,
            )
        )

        MockConverter.build(oas) {
            install()
        }
    }
}