package cloud.mallne.dicentra.aviator.core.helper

fun equality(a: Any?, b: Any?): Boolean {
    return (a === b) || (a != null && a == b)
}