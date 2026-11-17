package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.weaver.core.model.WeaverObjectNotationInvocation
import cloud.mallne.dicentra.weaver.core.specification.compute.expression.resolve
import cloud.mallne.dicentra.weaver.language.ast.expressions.WeaverContent

/** Resolves analysis references with the caller's lexical argument bindings intact. */
internal fun WeaverContent.resolveBound(parent: WeaverObjectNotationInvocation): WeaverObjectNotationInvocation {
    fun bind(invocation: WeaverObjectNotationInvocation): WeaverObjectNotationInvocation {
        if (invocation.metaAccess == WeaverObjectNotationInvocation.MetaAccess.Parameter &&
            invocation.schemaRoot === parent.schemaRoot && invocation.limboObject.key == parent.limboObject.key
        ) {
            parent.definedParameters[invocation.parameter]?.let { source ->
                return source.copy(coercion = invocation.coercion ?: source.coercion)
            }
        }
        // Calls can forward a parent parameter under a different name, including into a
        // nested schema. Capture the caller's source before entering the callee's scope.
        return invocation.copy(definedParameters = invocation.definedParameters.mapValues { (_, argument) -> bind(argument) })
    }
    return bind(resolve(parent.schemaRoot, parent.limboObject))
}
