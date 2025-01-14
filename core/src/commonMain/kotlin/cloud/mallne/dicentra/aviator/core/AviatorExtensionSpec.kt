package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.Operation
import cloud.mallne.dicentra.aviator.koas.typed.Route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

object AviatorExtensionSpec {
    private const val prefix = "x-dicentra-aviator"
    val SpecVersion = "1.0.0"
    val understandsVersions = listOf(SpecVersion)
    val Version = ExtensionLocator(prefix, OpenAPI::extensions)

    object ServiceLocator {
        val O = ExtensionLocator("$prefix-serviceDelegateCall", Operation::extensions)
        val R = ExtensionLocator("$prefix-serviceDelegateCall", Route::extensions)
    }

    object ServiceOptions {
        val O = ExtensionLocator("$prefix-serviceOptions", Operation::extensions)
        val R = ExtensionLocator("$prefix-serviceOptions", Route::extensions)
    }

    object PluginMaterialization {
        val O = ExtensionLocator("$prefix-pluginMaterialization", Operation::extensions)
        val R = ExtensionLocator("$prefix-pluginMaterialization", Route::extensions)
    }

    var OpenAPI.`x-dicentra-aviator`: String?
        get() {
            return Version.find(this)
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    Version.key to Json.encodeToJsonElement(
                        serializer(), value
                    )
                ).toMap()
            }
        }

    var Operation.`x-dicentra-aviator-serviceDelegateCall`: String?
        get() {
            return ServiceLocator.O.find(this)
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    ServiceLocator.O.key to Json.encodeToJsonElement(
                        serializer(), value
                    )
                ).toMap()
            }
        }

    var Route.`x-dicentra-aviator-serviceDelegateCall`: String?
        get() {
            return ServiceLocator.R.find(this)
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    ServiceLocator.R.key to Json.encodeToJsonElement(
                        serializer(), value
                    )
                ).toMap()
            }
        }

    var Operation.`x-dicentra-aviator-serviceOptions`: cloud.mallne.dicentra.aviator.core.ServiceOptions?
        get() {
            return ServiceOptions.O.findComplex(this)?.jsonObject
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    ServiceOptions.O.key to value
                ).toMap()
            }
        }

    var Route.`x-dicentra-aviator-serviceOptions`: cloud.mallne.dicentra.aviator.core.ServiceOptions?
        get() {
            return ServiceOptions.R.findComplex(this)?.jsonObject
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    ServiceOptions.R.key to value
                ).toMap()
            }
        }

    var Operation.`x-dicentra-aviator-pluginMaterialization`: Map<String, JsonElement>?
        get() {
            return PluginMaterialization.O.findComplex(this)
                ?.let { Json.decodeFromJsonElement(it) }
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    PluginMaterialization.O.key to Json.encodeToJsonElement(
                        serializer(), value
                    )
                ).toMap()
            }
        }

    var Route.`x-dicentra-aviator-pluginMaterialization`: Map<String, JsonElement>?
        get() {
            return PluginMaterialization.R.findComplex(this)
                ?.let { Json.decodeFromJsonElement(it) }
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    PluginMaterialization.R.key to Json.encodeToJsonElement(
                        serializer(), value
                    )
                ).toMap()
            }
        }
}