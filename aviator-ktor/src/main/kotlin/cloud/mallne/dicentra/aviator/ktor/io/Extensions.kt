package cloud.mallne.dicentra.aviator.ktor.io

import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils

internal inline fun <reified K, reified V> Iterable<Map.Entry<K, V>>.convertToMap(): Map<K, V> {
    return associate { (key, value) ->
        key to value
    }
}

internal inline fun <reified T> Iterable<T>.manualPipeline(block: (element: T, next: () -> Unit) -> Unit) {
    AviatorServiceUtils.manualPipeline(this, block)
}