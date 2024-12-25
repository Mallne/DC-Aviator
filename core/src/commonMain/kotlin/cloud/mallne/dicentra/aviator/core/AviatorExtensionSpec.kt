package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.Operation

object AviatorExtensionSpec {
    private const val prefix = "x-dicentra-aviator"
    val SpecVersion = "1.0.0"
    val Version = ExtensionLocator(prefix, OpenAPI::extensions)
    val ServiceLocator = ExtensionLocator("$prefix-serviceDelegateCall", Operation::extensions)
    val ServiceOptions = ExtensionLocator("$prefix-serviceOptions", Operation::extensions)
}