package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import kotlinx.serialization.Serializable

interface PluginStagedExecutor<CTX : AviatorExecutionContext<in O, in B>, O : @Serializable Any, B : @Serializable Any> {
    suspend fun beforeInvocation(context: CTX): Unit = Unit
    suspend fun beforeConstraintValidation(context: CTX): Unit = Unit
    suspend fun beforePathMatching(context: CTX): Unit = Unit
    suspend fun beforeFormingRequest(context: CTX): Unit = Unit
    suspend fun beforeRequesting(context: CTX): Unit = Unit
    suspend fun beforePaintingResponse(context: CTX): Unit = Unit
    suspend fun beforeFinished(context: CTX): Unit = Unit
    suspend fun afterInvocation(context: CTX): Unit = Unit
    suspend fun afterConstraintValidation(context: CTX): Unit = Unit
    suspend fun afterPathMatching(context: CTX): Unit = Unit
    suspend fun afterFormingRequest(context: CTX): Unit = Unit
    suspend fun afterRequesting(context: CTX): Unit = Unit
    suspend fun afterPaintingResponse(context: CTX): Unit = Unit
    suspend fun afterFinished(context: CTX): Unit = Unit
    fun preExecution(context: CTX): Unit = Unit
}