package cloud.mallne.dicentra.aviator.core.io.adapter.request

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.adapter.Targetable
import cloud.mallne.dicentra.aviator.core.io.adapter.Understandable
import kotlinx.serialization.Serializable

interface RequestBodyAdapter<Body : NetworkBody> : Targetable<Body>, Understandable {
    fun <O : @Serializable Any, B : @Serializable Any> buildBody(
        from: B?,
        context: AviatorExecutionContext<O, B>
    ): Body?

    fun <O : @Serializable Any, B : @Serializable Any> getRepresentation(
        body: Body,
        context: AviatorExecutionContext<O, B>
    ): String?


}