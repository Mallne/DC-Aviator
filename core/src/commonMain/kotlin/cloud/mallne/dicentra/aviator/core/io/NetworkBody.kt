package cloud.mallne.dicentra.aviator.core.io

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import kotlinx.serialization.Serializable

interface NetworkBody {
    object Empty : NetworkBody
    data class Text(var string: String) : NetworkBody
    data class Form(var formData: List<Pair<String, String>>) : NetworkBody

    fun <O : @Serializable Any, B : @Serializable Any> fetchRepresentation(context: AviatorExecutionContext<O, B>): String? {
        return context.adapterFor(this)?.getRepresentation(this, context)
    }
}