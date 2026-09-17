package cloud.mallne.dicentra.aviator.core.execution.logging

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext

interface AviatorLogger {
    fun error(message: String, metadata: Map<String, String> = emptyMap())
    fun error(message: String, cause: Throwable, metadata: Map<String, String> = emptyMap())
    fun warn(message: String, metadata: Map<String, String> = emptyMap())
    fun warn(message: String, cause: Throwable, metadata: Map<String, String> = emptyMap())
    fun info(message: String, metadata: Map<String, String> = emptyMap())
    fun info(message: String, cause: Throwable, metadata: Map<String, String> = emptyMap())
    fun debug(message: String, metadata: Map<String, String> = emptyMap())
    fun debug(message: String, cause: Throwable, metadata: Map<String, String> = emptyMap())
    fun trace(message: String, metadata: Map<String, String> = emptyMap())
    fun trace(message: String, cause: Throwable, metadata: Map<String, String> = emptyMap())

    companion object {
        typealias MetadataProvider = (loggingTag: String, context: AviatorExecutionContext<*, *>) -> Map<String, String>
    }
}
