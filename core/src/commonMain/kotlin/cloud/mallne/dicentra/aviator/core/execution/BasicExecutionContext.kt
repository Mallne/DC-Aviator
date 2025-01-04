package cloud.mallne.dicentra.aviator.core.execution

class BasicExecutionContext(
    override var stage: AviatorExecutionStages = AviatorExecutionStages.Unstarted
) : AviatorExecutionContext