package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.koas.typed.Route
import cloud.mallne.dicentra.aviator.model.SemVer
import io.ktor.openapi.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

object AviatorExtensionSpec {
    private const val prefix = "x-dicentra-aviator"
    const val SpecVersion = "1.0.0"
    val understandsVersions = listOf(SpecVersion)

    object Version {
        val O = ExtensionLocator(prefix, OpenApiDoc::extensions)
        val DSL = ExtensionLocator(prefix, OpenApiDocDsl::extensions)
    }

    object ServiceLocator {
        val O = ExtensionLocator("$prefix-serviceDelegateCall", Operation::extensions)
        val DSL = ExtensionLocator("$prefix-serviceDelegateCall", Operation.Builder::extensions)
        val R = ExtensionLocator("$prefix-serviceDelegateCall", Route::extensions)
    }

    object ServiceOptions {
        val O = ExtensionLocator("$prefix-serviceOptions", Operation::extensions)
        val DSL = ExtensionLocator("$prefix-serviceOptions", Operation.Builder::extensions)
        val R = ExtensionLocator("$prefix-serviceOptions", Route::extensions)
    }

    object PluginMaterialization {
        val O = ExtensionLocator("$prefix-pluginMaterialization", Operation::extensions)
        val DSL = ExtensionLocator("$prefix-pluginMaterialization", Operation.Builder::extensions)
        val R = ExtensionLocator("$prefix-pluginMaterialization", Route::extensions)
    }

    val OpenApiDoc.`x-dicentra-aviator`: String?
        get() {
            return Version.O.find(this)
        }
    var OpenApiDocDsl.`x-dicentra-aviator`: String?
        get() {
            return Version.DSL.find(this)
        }
        set(value) {
            if (value != null) {
                this.extensions.put(
                    Version.DSL.key, GenericElement(value)
                )
            }
        }

    val Operation.`x-dicentra-aviator-serviceDelegateCall`: String?
        get() {
            return ServiceLocator.O.find(this)
        }

    var Operation.Builder.`x-dicentra-aviator-serviceDelegateCall`: cloud.mallne.dicentra.aviator.model.ServiceLocator?
        get() {
            return ServiceLocator.DSL.find(this)?.let { cloud.mallne.dicentra.aviator.model.ServiceLocator(it) }
        }
        set(value) {
            if (value != null) {
                this.extensions.put(
                    ServiceLocator.DSL.key, GenericElement(value.toString())
                )
            }
        }

    var Route.`x-dicentra-aviator-serviceDelegateCall`: String?
        get() {
            return ServiceLocator.R.find(this)
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    ServiceLocator.R.key to GenericElement(value)
                ).toMap()
            }
        }

    val Operation.`x-dicentra-aviator-serviceOptions`: cloud.mallne.dicentra.aviator.core.ServiceOptions?
        get() {
            return ServiceOptions.O.findComplex(this)
        }

    var Operation.Builder.`x-dicentra-aviator-serviceOptions`: cloud.mallne.dicentra.aviator.core.ServiceOptions?
        get() {
            return ServiceOptions.DSL.findComplex(this)
        }
        set(value) {
            if (value != null) {
                this.extensions.put(
                    ServiceOptions.DSL.key, value
                )
            }
        }

    var Route.`x-dicentra-aviator-serviceOptions`: cloud.mallne.dicentra.aviator.core.ServiceOptions?
        get() {
            return ServiceOptions.R.findComplex(this)
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    ServiceOptions.R.key to value
                ).toMap()
            }
        }

    val Operation.`x-dicentra-aviator-pluginMaterialization`: Map<String, JsonElement>?
        get() {
            val a = PluginMaterialization.O.findComplex(this)?.entries()
            return a?.associate { (k, v) -> k to v.deserialize(serializer<JsonElement>()) }
        }

    var Operation.Builder.`x-dicentra-aviator-pluginMaterialization`: Map<String, JsonElement>?
        get() {
            val a = PluginMaterialization.DSL.findComplex(this)?.entries()
            return a?.associate { (k, v) -> k to v.deserialize(serializer<JsonElement>()) }
        }
        set(value) {
            if (value != null) {
                this.extensions.put(
                    PluginMaterialization.DSL.key, GenericElement(value)
                )
            }
        }

    var Route.`x-dicentra-aviator-pluginMaterialization`: Map<String, JsonElement>?
        get() {
            val a = PluginMaterialization.R.findComplex(this)?.entries()
            return a?.associate { (k, v) -> k to v.deserialize(serializer<JsonElement>()) }
        }
        set(value) {
            if (value != null) {
                this.extensions = this.extensions.toMutableMap().plus(
                    PluginMaterialization.R.key to GenericElement(value)
                )
            }
        }

    fun OpenApiInfo.semver(): SemVer? = SemVer(version)
}