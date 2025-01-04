package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.ExtensionLocator
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.Operation
import cloud.mallne.dicentra.aviator.koas.typed.Route

object AviatorExtensionSpec {
    private const val prefix = "x-dicentra-aviator"
    val SpecVersion = "1.0.0"
    val understandsVersions = listOf(SpecVersion)
    val Version = ExtensionLocator(prefix, OpenAPI::extensions)

    object ServiceLocator {
        val Operation = ExtensionLocator("$prefix-serviceDelegateCall", Operation::extensions)
        val Route = ExtensionLocator("$prefix-serviceDelegateCall", Route::extensions)
    }

    object ServiceOptions {
        val Operation = ExtensionLocator("$prefix-serviceOptions", Operation::extensions)
        val Route = ExtensionLocator("$prefix-serviceOptions", Route::extensions)
    }
}