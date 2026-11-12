package cloud.mallne.dicentra.aviator.orchestration.arazzo.execution

import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.OperationBinding
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Client-neutral Arazzo adapter for a native Aviator operation binding. A concrete operationId
 * must resolve uniquely; the exact constituent is restricted before invocation. The control is
 * installed around the binding call so any Aviator client transport in that call inherits the
 * same per-run authorization, actual-call, retry, concurrency and response-byte accounting.
 */
class ArazzoBindingStepExecutor(
    private val binding: OperationBinding<JsonElement>,
    private val operationIndex: List<ConstituentIdentity>,
    private val policy: CapabilityPolicy,
    private val json: Json = Json,
) : ArazzoStepExecutor {
    override suspend fun execute(step: ArazzoStep, inputs: String, scope: InvocationScope): ArazzoStepExecution {
        val resolution = resolveArazzoOperation(step, operationIndex, policy)
        if (resolution is ArazzoOperationResolution.Failed) {
            return ArazzoStepExecution.Failed(ExecutionFailure(resolution.code, resolution.message))
        }
        resolution as ArazzoOperationResolution.Resolved
        val identity = checkNotNull(resolution.invocation.constituents).single()
        val input = try {
            json.parseToJsonElement(inputs)
        } catch (cause: IllegalArgumentException) {
            return ArazzoStepExecution.Failed(ExecutionFailure("invalid_inputs", "Arazzo run inputs are not valid JSON"))
        }
        return try {
            val prepared = binding.resolve(resolution.invocation)
            ArazzoStepExecution.Completed(
                withContext(InvocationCallControl(scope, identity)) {
                    binding.invoke(prepared, input, scope)
                },
            )
        } catch (rejected: InvocationRejectedException) {
            ArazzoStepExecution.Failed(rejected.failure)
        }
    }
}
