package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.exceptions.ServiceException
import cloud.mallne.dicentra.polyfill.probe
import kotlinx.serialization.Serializable

object PluginStagedExecutorBuilder {
    @OptIn(InternalAviatorAPI::class)
    inline fun <CTX : AviatorExecutionContext<in O, in B>, O : @Serializable Any, B : @Serializable Any> steps(
        dsl: BuilderDSL<CTX, O, B>.() -> Unit
    ): PluginStagedExecutor<CTX, O, B> {
        val configuration = BuilderDSL<CTX, O, B>()
        dsl.invoke(configuration)
        return object : PluginStagedExecutor<CTX, O, B> {
            override suspend fun beforeInvocation(context: CTX): Unit =
                configuration.befores[AviatorExecutionStages.Invocation]?.invoke(context) ?: Unit

            override suspend fun beforeConstraintValidation(context: CTX) {
                configuration.befores[AviatorExecutionStages.ConstraintValidation]?.invoke(context)
            }

            override suspend fun beforePathMatching(context: CTX) {
                configuration.befores[AviatorExecutionStages.PathMatching]?.invoke(context)
            }

            override suspend fun beforeFormingRequest(context: CTX) {
                configuration.befores[AviatorExecutionStages.FormingRequest]?.invoke(context)
            }

            override suspend fun beforeRequesting(context: CTX) {
                configuration.befores[AviatorExecutionStages.Requesting]?.invoke(context)
            }

            override suspend fun beforePaintingResponse(context: CTX) {
                configuration.befores[AviatorExecutionStages.PaintingResponse]?.invoke(context)
            }

            override suspend fun beforeFinished(context: CTX) {
                configuration.befores[AviatorExecutionStages.Finished]?.invoke(context)
            }

            override suspend fun afterInvocation(context: CTX) {
                configuration.afters[AviatorExecutionStages.Invocation]?.invoke(context)
            }

            override suspend fun afterConstraintValidation(context: CTX) {
                configuration.afters[AviatorExecutionStages.ConstraintValidation]?.invoke(context)
            }

            override suspend fun afterPathMatching(context: CTX) {
                configuration.afters[AviatorExecutionStages.PathMatching]?.invoke(context)
            }

            override suspend fun afterFormingRequest(context: CTX) {
                configuration.afters[AviatorExecutionStages.FormingRequest]?.invoke(context)
            }

            override suspend fun afterRequesting(context: CTX) {
                configuration.afters[AviatorExecutionStages.Requesting]?.invoke(context)
            }

            override suspend fun afterPaintingResponse(context: CTX) {
                configuration.afters[AviatorExecutionStages.PaintingResponse]?.invoke(context)
            }

            override suspend fun afterFinished(context: CTX) {
                configuration.afters[AviatorExecutionStages.Finished]?.invoke(context)
            }
        }
    }

    class BuilderDSL<CTX : AviatorExecutionContext<in O, in B>, O : @Serializable Any, B : @Serializable Any> {
        @InternalAviatorAPI
        var afters: MutableMap<AviatorExecutionStages, suspend (CTX) -> Unit> = mutableMapOf()

        @InternalAviatorAPI
        var befores: MutableMap<AviatorExecutionStages, suspend (CTX) -> Unit> = mutableMapOf()

        @OptIn(InternalAviatorAPI::class)
        fun after(stage: AviatorExecutionStages, action: suspend (CTX) -> Unit) {
            probe(stage != AviatorExecutionStages.Unstarted) { ServiceException("A Stage of $stage cannot be executed. It serves a the Default value, please use the next up in the Lifecycle.") }
            afters[stage] = action
        }

        @OptIn(InternalAviatorAPI::class)
        fun before(stage: AviatorExecutionStages, action: suspend (CTX) -> Unit) {
            probe(stage != AviatorExecutionStages.Unstarted) { ServiceException("A Stage of $stage cannot be executed. It serves a the Default value, please use the next up in the Lifecycle.") }
            befores[stage] = action
        }
    }
}