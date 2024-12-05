package cloud.mallne.dicentra.aviator.core.helper

inline fun hashAll(vararg vals: Any?): Int {
    var res = 0
    for (v in vals) {
        res += v.hashCode()
        res *= 31
    }
    return res
}