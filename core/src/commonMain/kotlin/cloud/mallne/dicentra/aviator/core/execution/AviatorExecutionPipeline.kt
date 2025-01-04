package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance


class AviatorExecutionPipeline<C : AviatorExecutionContext>(
    val context: C,
    val plugins: List<AviatorPluginInstance>,
    val executor: StagedExecutor
) {
    suspend fun run(): C {
        escalate()
        return context
    }

    private tailrec suspend fun escalate() {
        when (context.stage) {
            AviatorExecutionStages.Unstarted -> {
                context.stage = AviatorExecutionStages.Invocation
                executor.onInvocation(context)
                plugins.forEach { value -> value.onInvocation(context) }
                escalate()
            }

            AviatorExecutionStages.Invocation -> {
                context.stage = AviatorExecutionStages.ConstraintValidation
                executor.onConstraintValidation(context)
                plugins.forEach { value -> value.onConstraintValidation(context) }
                escalate()
            }

            AviatorExecutionStages.ConstraintValidation -> {
                context.stage = AviatorExecutionStages.PathMatching
                executor.onPathMatching(context)
                plugins.forEach { value -> value.onPathMatching(context) }
                escalate()
            }

            AviatorExecutionStages.PathMatching -> {
                context.stage = AviatorExecutionStages.FormingRequest
                executor.onFormingRequest(context)
                plugins.forEach { value -> value.onFormingRequest(context) }
                escalate()
            }

            AviatorExecutionStages.FormingRequest -> {
                context.stage = AviatorExecutionStages.Requesting
                executor.onRequesting(context)
                plugins.forEach { value -> value.onRequesting(context) }
                escalate()
            }

            AviatorExecutionStages.Requesting -> {
                context.stage = AviatorExecutionStages.PaintingResponse
                executor.onPaintingResponse(context)
                plugins.forEach { value -> value.onPaintingResponse(context) }
                escalate()
            }

            AviatorExecutionStages.PaintingResponse -> {
                context.stage = AviatorExecutionStages.Finished
                executor.onFinished(context)
                plugins.forEach { value -> value.onFinished(context) }
                escalate()
            }

            AviatorExecutionStages.Finished -> {

            }
        }
    }
}