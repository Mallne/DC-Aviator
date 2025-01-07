package cloud.mallne.dicentra.aviator.core.execution

import kotlinx.serialization.Serializable

interface StagedExecutor<CTX : AviatorExecutionContext<in O, in B>, O : @Serializable Any, B : @Serializable Any> {
    suspend fun onInvocation(context: CTX): Unit = Unit
    suspend fun onConstraintValidation(context: CTX): Unit = Unit
    suspend fun onPathMatching(context: CTX): Unit = Unit
    suspend fun onFormingRequest(context: CTX): Unit = Unit
    suspend fun onRequesting(context: CTX): Unit = Unit
    suspend fun onPaintingResponse(context: CTX): Unit = Unit
    suspend fun onFinished(context: CTX): Unit = Unit
}