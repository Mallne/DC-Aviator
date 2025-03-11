package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import kotlinx.serialization.Serializable


class AviatorExecutionPipeline<C : AviatorExecutionContext<in O, in B>, O : @Serializable Any, B : @Serializable Any>(
    val context: C,
    val plugins: List<AviatorPluginInstance>,
    val executor: StagedExecutor<C, O, B>
) {
    suspend fun run(): C {
        escalate()
        return context
    }

    @OptIn(InternalAviatorAPI::class)
    @Suppress("UNCHECKED_CAST")
    private tailrec suspend fun escalate() {
        context as AviatorExecutionContext<@Serializable Any, @Serializable Any>
        when (context.stage) {
            AviatorExecutionStages.Unstarted -> {
                context.stage = AviatorExecutionStages.Invocation
                plugins.forEach { value -> value.x.beforeInvocation(context) }
                executor.onInvocation(context)
                plugins.forEach { value -> value.x.afterInvocation(context) }
                escalate()
            }

            AviatorExecutionStages.Invocation -> {
                context.stage = AviatorExecutionStages.ConstraintValidation
                plugins.forEach { value -> value.x.beforeConstraintValidation(context) }
                executor.onConstraintValidation(context)
                plugins.forEach { value -> value.x.afterConstraintValidation(context) }
                escalate()
            }

            AviatorExecutionStages.ConstraintValidation -> {
                context.stage = AviatorExecutionStages.PathMatching
                plugins.forEach { value -> value.x.beforePathMatching(context) }
                executor.onPathMatching(context)
                plugins.forEach { value -> value.x.afterPathMatching(context) }
                escalate()
            }

            AviatorExecutionStages.PathMatching -> {
                context.stage = AviatorExecutionStages.FormingRequest
                plugins.forEach { value -> value.x.beforeFormingRequest(context) }
                executor.onFormingRequest(context)
                plugins.forEach { value -> value.x.afterFormingRequest(context) }
                escalate()
            }

            AviatorExecutionStages.FormingRequest -> {
                context.stage = AviatorExecutionStages.Requesting
                plugins.forEach { value -> value.x.beforeRequesting(context) }
                executor.onRequesting(context)
                plugins.forEach { value -> value.x.afterRequesting(context) }
                escalate()
            }

            AviatorExecutionStages.Requesting -> {
                context.stage = AviatorExecutionStages.PaintingResponse
                plugins.forEach { value -> value.x.beforePaintingResponse(context) }
                executor.onPaintingResponse(context)
                plugins.forEach { value -> value.x.afterPaintingResponse(context) }
                escalate()
            }

            AviatorExecutionStages.PaintingResponse -> {
                context.stage = AviatorExecutionStages.Finished
                plugins.forEach { value -> value.x.beforeFinished(context) }
                executor.onFinished(context)
                plugins.forEach { value -> value.x.afterFinished(context) }
                escalate()
            }

            AviatorExecutionStages.Finished -> {

            }
        }
    }
}