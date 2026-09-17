package cloud.mallne.dicentra.aviator.core.execution.logging

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI

@InternalAviatorAPI
internal class DeferredAviatorLogger(private val delegate: AviatorLogger) : AviatorLogger {
    val currentMetadata: MutableMap<String, String> = mutableMapOf()

    override fun error(message: String, metadata: Map<String, String>) {
        delegate.error(message, currentMetadata.plus(metadata))
    }

    override fun error(message: String, cause: Throwable, metadata: Map<String, String>) {
        delegate.error(message, cause, currentMetadata.plus(metadata))
    }

    override fun warn(message: String, metadata: Map<String, String>) {
        delegate.warn(message, currentMetadata.plus(metadata))
    }

    override fun warn(message: String, cause: Throwable, metadata: Map<String, String>) {
        delegate.warn(message, cause, currentMetadata.plus(metadata))
    }

    override fun info(message: String, metadata: Map<String, String>) {
        delegate.info(message, currentMetadata.plus(metadata))
    }

    override fun info(message: String, cause: Throwable, metadata: Map<String, String>) {
        delegate.info(message, cause, currentMetadata.plus(metadata))
    }

    override fun debug(message: String, metadata: Map<String, String>) {
        delegate.debug(message, currentMetadata.plus(metadata))
    }

    override fun debug(message: String, cause: Throwable, metadata: Map<String, String>) {
        delegate.debug(message, cause, currentMetadata.plus(metadata))
    }

    override fun trace(message: String, metadata: Map<String, String>) {
        delegate.trace(message, currentMetadata.plus(metadata))
    }

    override fun trace(message: String, cause: Throwable, metadata: Map<String, String>) {
        delegate.trace(message, cause, currentMetadata.plus(metadata))
    }

}