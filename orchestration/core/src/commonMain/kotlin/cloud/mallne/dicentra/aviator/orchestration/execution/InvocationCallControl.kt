package cloud.mallne.dicentra.aviator.orchestration.execution

import cloud.mallne.dicentra.aviator.core.execution.AviatorCallControl
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure

/** Admission failure at a transport boundary; contains no provider exception or request data. */
class InvocationRejectedException(val failure: ExecutionFailure) : Exception(failure.message)

/** Bind an isolated constituent identity and run scope to calls made by a client pipeline. */
class InvocationCallControl(
    private val scope: InvocationScope,
    private val identity: ConstituentIdentity,
) : AviatorCallControl() {
    override suspend fun <T> execute(retry: Boolean, call: suspend () -> T): T =
        when (val result = scope.callConstituent(identity, retry, call)) {
            is ConstituentCallResult.Admitted -> result.value
            is ConstituentCallResult.Rejected -> throw InvocationRejectedException(result.failure)
        }
}
