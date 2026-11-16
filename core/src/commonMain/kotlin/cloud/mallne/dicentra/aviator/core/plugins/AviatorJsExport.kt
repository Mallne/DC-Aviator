@file:OptIn(kotlin.ExperimentalMultiplatform::class)

package cloud.mallne.dicentra.aviator.core.plugins

import kotlin.OptionalExpectation

/**
 * Exports plugin SPI interfaces from the JavaScript target without requiring the same annotation
 * to be supported by every Kotlin Multiplatform target (notably Wasm).
 */
@OptionalExpectation
@Target(AnnotationTarget.CLASS)
expect annotation class AviatorJsExport()
