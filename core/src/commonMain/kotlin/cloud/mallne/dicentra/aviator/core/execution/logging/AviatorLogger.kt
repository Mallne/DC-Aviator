package cloud.mallne.dicentra.aviator.core.execution.logging

interface AviatorLogger {
    fun error(message: String)
    fun error(message: String, cause: Throwable)
    fun warn(message: String)
    fun warn(message: String, cause: Throwable)
    fun info(message: String)
    fun info(message: String, cause: Throwable)
    fun debug(message: String)
    fun debug(message: String, cause: Throwable)
    fun trace(message: String)
    fun trace(message: String, cause: Throwable)
}
