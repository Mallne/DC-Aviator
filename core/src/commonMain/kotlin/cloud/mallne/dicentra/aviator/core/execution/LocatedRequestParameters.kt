package cloud.mallne.dicentra.aviator.core.execution

import io.ktor.openapi.ParameterType
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** OpenAPI parameter identity includes both its name and its location. */
data class LocatedParameterKey(val name: String, val location: ParameterType)

/** Optional request-scoped values; legacy name-keyed request parameters remain supported. */
class LocatedRequestParameters(
    values: Map<LocatedParameterKey, RequestParameter>,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<LocatedRequestParameters>

    val values: Map<LocatedParameterKey, RequestParameter> = values.toMap()
}
