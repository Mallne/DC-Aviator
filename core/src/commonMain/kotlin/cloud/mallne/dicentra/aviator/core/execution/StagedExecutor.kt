package cloud.mallne.dicentra.aviator.core.execution

interface StagedExecutor {
    suspend fun onInvocation(context: AviatorExecutionContext): Unit = Unit
    suspend fun onConstraintValidation(context: AviatorExecutionContext): Unit = Unit
    suspend fun onPathMatching(context: AviatorExecutionContext): Unit = Unit
    suspend fun onFormingRequest(context: AviatorExecutionContext): Unit = Unit
    suspend fun onRequesting(context: AviatorExecutionContext): Unit = Unit
    suspend fun onPaintingResponse(context: AviatorExecutionContext): Unit = Unit
    suspend fun onFinished(context: AviatorExecutionContext): Unit = Unit
}