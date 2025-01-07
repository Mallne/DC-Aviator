package cloud.mallne.dicentra.aviator.ktor.io

internal inline fun <reified K, reified V> Iterable<Map.Entry<K, V>>.convertToMap(): Map<K, V> {
    return associate { (key, value) ->
        key to value
    }
}

internal inline fun <reified T> Iterable<T>.manualPipeline(block: (element: T, next: () -> Unit) -> Unit) {
    var doAnotherOne = false
    val ne = {
        doAnotherOne = true
    }
    forEach { obj ->
        doAnotherOne = false
        block(obj, ne)
        if (!doAnotherOne) {
            return
        }
    }
}