package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import cloud.mallne.dicentra.aviator.plugin.orchestration.budget.executionBudgetPolicyPlugin
import cloud.mallne.dicentra.aviator.orchestration.plugin.installOrchestrationPlugins
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoCriterion
import cloud.mallne.dicentra.aviator.core.execution.AviatorCallControl
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.orchestration.execution.OperationBinding
import cloud.mallne.dicentra.aviator.orchestration.execution.ResolvedOperation
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoBindingStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoRunExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentResult
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionOutcome
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ConstituentOrdering
import cloud.mallne.dicentra.aviator.orchestration.policy.EmptySelectionPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionStrategy
import cloud.mallne.dicentra.aviator.orchestration.policy.PartialFailurePolicy
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import io.ktor.openapi.GenericElement
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.openapi.PathItem
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.Response
import io.ktor.openapi.Responses
import io.ktor.openapi.Server
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FetchOrchestrationCallControlTest {
    @Test
    fun arazzoCancellationAbortsActiveFetchAndPropagates() = runTest {
        var signal: dynamic = null
        val started = CompletableDeferred<Unit>()
        val fetcher: dynamic = { _: String, init: dynamic ->
            signal = init.signal
            started.complete(Unit)
            js("new Promise(() => {})")
        }
        val service = service(fetcher)
        val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1", "getForecast", "/forecast")
        val binding = object : OperationBinding<JsonElement> {
            override suspend fun resolve(invocation: CapabilityInvocation) = Prepared(invocation)
            override suspend fun invoke(operation: ResolvedOperation, input: JsonElement, scope: InvocationScope): CapabilityResult {
                val invocation = operation.invocation
                val selected = checkNotNull(invocation.constituents).single()
                val value = service.request<JsonElement>() ?: kotlinx.serialization.json.JsonNull
                return CapabilityResult(invocation.locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(value)),
                    listOf(ConstituentResult(selected, ConstituentOutcome.Succeeded(BodyValue.JsonValue(value)))))
            }
        }
        val runner = ArazzoRunExecutor(ArazzoBindingStepExecutor(binding, listOf(identity), policy()))
        val workflow = ArazzoWorkflow("flow", listOf(ArazzoStep("fetch", operationId = "getForecast")))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runner.execute(workflow, workflowIdentity(), RunId("arazzo-fetch-cancel"), budget = ExecutionBudget(maxConcurrency = 1))
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(signal.aborted as Boolean)
    }

    @Test
    fun arazzoBindingInstallsRunBudgetAroundRealFetchRequest() = runTest {
        var calls = 0
        val fetcher: dynamic = { _: String, _: dynamic ->
            calls++
            responsePromise("""{"temperature":18.5}""")
        }
        val service = service(fetcher)
        val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1", "getForecast", "/forecast")
        val binding = object : OperationBinding<JsonElement> {
            override suspend fun resolve(invocation: CapabilityInvocation) = Prepared(invocation)
            override suspend fun invoke(operation: ResolvedOperation, input: JsonElement, scope: InvocationScope): CapabilityResult {
                val invocation = operation.invocation
                val selected = checkNotNull(invocation.constituents).single()
                val value = service.request<JsonElement>() ?: kotlinx.serialization.json.JsonNull
                return CapabilityResult(invocation.locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(value)),
                    listOf(ConstituentResult(selected, ConstituentOutcome.Succeeded(BodyValue.JsonValue(value)))))
            }
        }
        val runner = ArazzoRunExecutor(ArazzoBindingStepExecutor(binding, listOf(identity), policy()),
            plugins = installOrchestrationPlugins { install(executionBudgetPolicyPlugin()) })
        val workflow = ArazzoWorkflow("flow", listOf(ArazzoStep("fetch", operationId = "getForecast",
            successCriteria = listOf(ArazzoCriterion("true == true")))))
        val success = runner.execute(workflow, workflowIdentity(), RunId("arazzo-fetch-ok"), budget = ExecutionBudget(maxCalls = 1))
        assertEquals(1, calls)
        assertIs<ExecutionOutcome.Succeeded>(success.outcome)

        val rejected = runner.execute(workflow, workflowIdentity(), RunId("arazzo-fetch-budget"), budget = ExecutionBudget(maxCalls = 0))
        assertEquals(1, calls)
        assertEquals("calls", assertIs<ExecutionOutcome.Failed>(rejected.outcome).failure.code)
    }

    @Test
    fun cancellingActiveFetchAbortsSignalAndReleasesRunSlot() = runTest {
        var signal: dynamic = null
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val fetcher: dynamic = { _: String, init: dynamic ->
            calls++
            signal = init.signal
            started.complete(Unit)
            js("new Promise(() => {})")
        }
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("cancel"), WorkflowIdentity("test", "1", "doc", "1")),
            ExecutionBudget(maxConcurrency = 1),
            policyPlugins = installOrchestrationPlugins { install(executionBudgetPolicyPlugin()) }.invocationPolicyPlugins(),
        )
        val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1")
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(InvocationCallControl(scope, identity)) { service(fetcher).request<WeatherResponse>() }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(signal.aborted as Boolean)
        assertEquals(1, calls)
        assertNull(scope.acquireCall(identity))
        scope.releaseCall(identity)
    }

    @Test
    fun orchestrationBudgetCountsActualFetchCalls() = runTest {
        var calls = 0
        val fetcher: dynamic = { _: String, _: dynamic ->
            calls++
            responsePromise("""{"temperature":18.5}""")
        }
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "doc", "1")),
            ExecutionBudget(maxCalls = 1),
            policyPlugins = installOrchestrationPlugins { install(executionBudgetPolicyPlugin()) }.invocationPolicyPlugins(),
        )
        val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1")
        withContext(InvocationCallControl(scope, identity)) {
            assertEquals(18.5, service(fetcher).request<WeatherResponse>()?.temperature)
            val rejected = assertFailsWith<InvocationRejectedException> { service(fetcher).request<WeatherResponse>() }
            assertEquals("calls", rejected.failure.code)
        }
        assertEquals(1, calls)
    }

    @Test
    fun fallbackFetchIsChargedAsRetry() = runTest {
        var calls = 0
        val fetcher: dynamic = { _: String, _: dynamic ->
            calls++
            responsePromise("{}", status = 503)
        }
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("retry"), WorkflowIdentity("test", "1", "doc", "1")),
            ExecutionBudget(maxRetries = 0),
            policyPlugins = installOrchestrationPlugins { install(executionBudgetPolicyPlugin()) }.invocationPolicyPlugins(),
        )
        val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1")
        withContext(InvocationCallControl(scope, identity)) {
            assertFailsWith<InvocationRejectedException> {
                service(fetcher, servers = listOf("https://weather.test", "https://backup.test"))
                    .request<WeatherResponse>()
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun responseIsDecodedWithoutCoreByteAccounting() = runTest {
        var calls = 0
        val fetcher: dynamic = { _: String, _: dynamic ->
            calls++
            responsePromise("""{"temperature":18.5}""")
        }
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("bytes"), WorkflowIdentity("test", "1", "doc", "1")),
            ExecutionBudget(),
        )
        val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1")
        val response = withContext(InvocationCallControl(scope, identity)) {
            service(fetcher).request<WeatherResponse>()
        }
        assertEquals(18.5, response?.temperature)
        assertEquals(1, calls)
    }

    private fun service(
        fetcher: dynamic,
        maxResponseBytes: Int = 16_777_216,
        servers: List<String> = listOf("https://weather.test"),
    ): FetchAviatorService {
        val operation = Operation(
            operationId = "getForecast",
            responses = Responses(
                responses = mapOf(200 to ReferenceOr.Value(Response(description = "Weather response")))
            ),
            extensions = mapOf(
                "x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather-provider")
            ),
        )
        val specification = OpenApiDoc(
            info = OpenApiInfo(title = "Fetch client test", version = "1.0.0"),
            servers = servers.map(::Server),
            paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = operation))),
            extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
        )

        return FetchAviatorServiceConverter(
            fetcher = fetcher,
            maxResponseBytes = maxResponseBytes,
        ).build(specification) {
            install(JsonAdapter)
        }.single() as FetchAviatorService
    }

    private fun responsePromise(body: String, status: Int = 200): dynamic {
        val response: dynamic = js(
            "new Response(body, { status: status, headers: { 'content-type': 'application/json' } })"
        )
        return js("Promise.resolve(response)")
    }

    private data class Prepared(override val invocation: CapabilityInvocation) : ResolvedOperation

    private fun policy() = CapabilityPolicy(
        ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
        PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL,
    )

    private fun workflowIdentity() = WorkflowIdentity("arazzo", "1.1.0", "workflow:test", "revision-1")

    @Serializable
    private data class WeatherResponse(val temperature: Double)
}
