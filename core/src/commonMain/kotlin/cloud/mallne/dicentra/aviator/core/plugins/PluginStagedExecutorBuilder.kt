package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.exceptions.ServiceException
import cloud.mallne.dicentra.polyfill.probe
import kotlinx.serialization.Serializable

object PluginStagedExecutorBuilder {
    inline fun <CTX : AviatorExecutionContext<in O, in B>, O : @Serializable Any, B : @Serializable Any> steps(
        dsl: BuilderDSL<CTX, O, B>.() -> Unit
    ): PluginStagedExecutor<CTX, O, B> {
        val configuration = BuilderDSL<CTX, O, B>()
        dsl.invoke(configuration)
        return object : PluginStagedExecutor<CTX, O, B> {
            override suspend fun beforeInvocation(context: CTX): Unit =
                configuration.befores[AviatorExecutionStages.Invocation]?.invoke(context) ?: Unit

            override suspend fun beforeConstraintValidation(context: CTX): Unit =
                configuration.befores[AviatorExecutionStages.ConstraintValidation]?.invoke(context)
                    ?: Unit

            override suspend fun beforePathMatching(context: CTX): Unit =
                configuration.befores[AviatorExecutionStages.PathMatching]?.invoke(context) ?: Unit

            override suspend fun beforeFormingRequest(context: CTX): Unit =
                configuration.befores[AviatorExecutionStages.FormingRequest]?.invoke(context)
                    ?: Unit

            override suspend fun beforeRequesting(context: CTX): Unit =
                configuration.befores[AviatorExecutionStages.Requesting]?.invoke(context) ?: Unit

            override suspend fun beforePaintingResponse(context: CTX): Unit =
                configuration.befores[AviatorExecutionStages.PaintingResponse]?.invoke(context)
                    ?: Unit

            override suspend fun beforeFinished(context: CTX): Unit =
                configuration.befores[AviatorExecutionStages.Finished]?.invoke(context) ?: Unit

            override suspend fun afterInvocation(context: CTX): Unit =
                configuration.afters[AviatorExecutionStages.Invocation]?.invoke(context) ?: Unit

            override suspend fun afterConstraintValidation(context: CTX): Unit =
                configuration.afters[AviatorExecutionStages.ConstraintValidation]?.invoke(context)
                    ?: Unit

            override suspend fun afterPathMatching(context: CTX): Unit =
                configuration.afters[AviatorExecutionStages.PathMatching]?.invoke(context) ?: Unit

            override suspend fun afterFormingRequest(context: CTX): Unit =
                configuration.afters[AviatorExecutionStages.FormingRequest]?.invoke(context) ?: Unit

            override suspend fun afterRequesting(context: CTX): Unit =
                configuration.afters[AviatorExecutionStages.Requesting]?.invoke(context) ?: Unit

            override suspend fun afterPaintingResponse(context: CTX): Unit =
                configuration.afters[AviatorExecutionStages.PaintingResponse]?.invoke(context)
                    ?: Unit

            override suspend fun afterFinished(context: CTX): Unit =
                configuration.afters[AviatorExecutionStages.Finished]?.invoke(context) ?: Unit
        }
    }

    class BuilderDSL<CTX : AviatorExecutionContext<in O, in B>, O : @Serializable Any, B : @Serializable Any> {
        var afters: MutableMap<AviatorExecutionStages, (CTX) -> Unit> = mutableMapOf()
        var befores: MutableMap<AviatorExecutionStages, (CTX) -> Unit> = mutableMapOf()

        @OptIn(InternalAviatorAPI::class)
        fun after(stage: AviatorExecutionStages, action: (CTX) -> Unit) {
            probe(stage != AviatorExecutionStages.Unstarted) { ServiceException("A Stage of $stage cannot be executed. It serves a the Default value, please use the next up in the Lifecycle.") }
            afters[stage] = action
        }

        @OptIn(InternalAviatorAPI::class)
        fun before(stage: AviatorExecutionStages, action: (CTX) -> Unit) {
            probe(stage != AviatorExecutionStages.Unstarted) { ServiceException("A Stage of $stage cannot be executed. It serves a the Default value, please use the next up in the Lifecycle.") }
            befores[stage] = action
        }
    }
}