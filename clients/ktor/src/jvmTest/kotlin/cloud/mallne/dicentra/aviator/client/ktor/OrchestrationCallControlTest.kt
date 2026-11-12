package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.EnforcedInvocationScope
import cloud.mallne.dicentra.aviator.orchestration.execution.OperationBinding
import cloud.mallne.dicentra.aviator.orchestration.execution.ResolvedOperation
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoBindingStepExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoRunExecutor
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoWorkflow
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationCallControl
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationRejectedException
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionMetadata
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionFailure
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityInvocation
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult
import cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.BodyValue
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentResult
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.ExecutionOutcome
import cloud.mallne.dicentra.aviator.orchestration.model.RunId
import cloud.mallne.dicentra.aviator.orchestration.model.WorkflowIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ConstituentOrdering
import cloud.mallne.dicentra.aviator.orchestration.policy.EmptySelectionPolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionStrategy
import cloud.mallne.dicentra.aviator.orchestration.policy.PartialFailurePolicy
import cloud.mallne.dicentra.aviator.orchestration.policy.ExecutionBudget
import cloud.mallne.dicentra.aviator.plugin.adapter.json.JsonAdapter
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationPolicy
import cloud.mallne.dicentra.aviator.orchestration.execution.InvocationPolicyPlugin
import cloud.mallne.dicentra.aviator.orchestration.plugin.OrchestrationPluginRegistry
import cloud.mallne.dicentra.aviator.orchestration.plugin.orchestrationPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.openapi.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertIs

class OrchestrationCallControlTest {
    @Test
    fun arazzoCancellationPropagatesIntoActiveKtorRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        var requestCancelled = false
        val client = HttpClient(MockEngine {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                requestCancelled = true
            }
        })
        try {
            val operation = Operation(
                operationId = "getForecast",
                responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
                extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather-provider")),
            )
            val spec = OpenApiDoc(
                info = OpenApiInfo(title = "test", version = "1"),
                servers = listOf(Server(url = "https://weather.test")),
                paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = operation))),
                extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
            )
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec) { install(JsonAdapter) }.single()
            val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1", "getForecast", "/forecast")
            val binding = object : OperationBinding<JsonElement> {
                override suspend fun resolve(invocation: CapabilityInvocation) = Prepared(invocation)
                override suspend fun invoke(
                    operation: ResolvedOperation,
                    input: JsonElement,
                    scope: cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope,
                ): CapabilityResult {
                    val invocation = operation.invocation
                    val selected = checkNotNull(invocation.constituents).single()
                    val value = service.request<JsonElement>() ?: JsonNull
                    return CapabilityResult(invocation.locator, CapabilityOutcome.Succeeded(BodyValue.JsonValue(value)),
                        listOf(ConstituentResult(selected, ConstituentOutcome.Succeeded(BodyValue.JsonValue(value)))))
                }
            }
            val runner = ArazzoRunExecutor(ArazzoBindingStepExecutor(binding, listOf(identity), policy()))
            val workflow = ArazzoWorkflow("flow", listOf(ArazzoStep("fetch", operationId = "getForecast")))
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                runner.execute(workflow, workflowIdentity(), RunId("arazzo-ktor-cancel"))
            }
            started.await()
            job.cancelAndJoin()
            assertEquals(true, requestCancelled)
        } finally {
            client.close()
        }
    }

    @Test
    fun arazzoBindingInstallsRunBudgetAroundRealKtorRequest() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(ByteReadChannel("""{"ok":true}"""), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val operation = Operation(
                operationId = "getForecast",
                responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
                extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather-provider")),
            )
            val spec = OpenApiDoc(
                info = OpenApiInfo(title = "test", version = "1"),
                servers = listOf(Server(url = "https://weather.test")),
                paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = operation))),
                extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
            )
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec) { install(JsonAdapter) }.single()
            val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1", "getForecast", "/forecast")
            val binding = object : OperationBinding<kotlinx.serialization.json.JsonElement> {
                override suspend fun resolve(invocation: CapabilityInvocation) = Prepared(invocation)
                override suspend fun invoke(
                    operation: ResolvedOperation,
                    input: kotlinx.serialization.json.JsonElement,
                    scope: cloud.mallne.dicentra.aviator.orchestration.execution.InvocationScope,
                ): CapabilityResult {
                    val invocation = operation.invocation
                    val selected = checkNotNull(invocation.constituents).single()
                    val value = service.request<kotlinx.serialization.json.JsonElement>() ?: JsonNull
                    return CapabilityResult(invocation.locator,
                        CapabilityOutcome.Succeeded(BodyValue.JsonValue(value)),
                        listOf(ConstituentResult(selected, ConstituentOutcome.Succeeded(BodyValue.JsonValue(value)))))
                }
            }
            val runner = ArazzoRunExecutor(ArazzoBindingStepExecutor(binding, listOf(identity), policy()),
                plugins = testBudgetPluginRegistry())
            val workflow = ArazzoWorkflow("flow", listOf(ArazzoStep("fetch", operationId = "getForecast")))
            val success = runner.execute(workflow, workflowIdentity(), RunId("arazzo-ktor-ok"), budget = ExecutionBudget(maxCalls = 1))
            assertEquals(1, calls)
            assertIs<ExecutionOutcome.Succeeded>(success.outcome)

            val rejected = runner.execute(workflow, workflowIdentity(), RunId("arazzo-ktor-budget"), budget = ExecutionBudget(maxCalls = 0))
            assertEquals(1, calls)
            assertEquals("calls", assertIs<ExecutionOutcome.Failed>(rejected.outcome).failure.code)
        } finally {
            client.close()
        }
    }

    @Test
    fun orchestrationBudgetCountsActualKtorCalls() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(ByteReadChannel("""{"ok":true}"""), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val operation = Operation(
                operationId = "getForecast",
                responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
                extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather-provider")),
            )
            val spec = OpenApiDoc(
                info = OpenApiInfo(title = "test", version = "1"),
                servers = listOf(Server(url = "https://weather.test")),
                paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = operation))),
                extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
            )
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec) { install(JsonAdapter) }.single()
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "doc", "1")),
                ExecutionBudget(maxCalls = 1),
                policyPlugins = testBudgetPolicyPlugins(),
            )
            val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1")
            withContext(InvocationCallControl(scope, identity)) {
                assertNotNull(service.request<JsonElement>())
                val rejected = assertFailsWith<InvocationRejectedException> { service.request<JsonElement>() }
                assertEquals("calls", rejected.failure.code)
            }
            assertEquals(1, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun responseIsDecodedWithoutCoreByteAccounting() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(ByteReadChannel("""{"ok":true}"""), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val operation = Operation(
                operationId = "getForecast",
                responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
                extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather-provider")),
            )
            val spec = OpenApiDoc(
                info = OpenApiInfo(title = "test", version = "1"),
                servers = listOf(Server(url = "https://weather.test")),
                paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = operation))),
                extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
            )
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec) { install(JsonAdapter) }.single()
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("bytes"), WorkflowIdentity("test", "1", "doc", "1")),
                ExecutionBudget(),
            )
            val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1")
            val response = withContext(InvocationCallControl(scope, identity)) {
                service.request<JsonElement>()
            }
            assertEquals(kotlinx.serialization.json.Json.parseToJsonElement("""{"ok":true}"""), response)
            assertEquals(1, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun fallbackKtorRequestIsChargedAsRetry() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(ByteReadChannel("{}"), HttpStatusCode.ServiceUnavailable)
        })
        try {
            val operation = Operation(
                operationId = "getForecast",
                responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
                extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather-provider")),
            )
            val spec = OpenApiDoc(
                info = OpenApiInfo(title = "test", version = "1"),
                servers = listOf(Server(url = "https://primary.test"), Server(url = "https://backup.test")),
                paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = operation))),
                extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
            )
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec) { install(JsonAdapter) }.single()
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("retry"), WorkflowIdentity("test", "1", "doc", "1")),
                ExecutionBudget(maxRetries = 0),
                policyPlugins = testBudgetPolicyPlugins(),
            )
            val identity = ConstituentIdentity(ServiceLocator("weather-provider"), "provider", "source", "1")
            val failure = withContext(InvocationCallControl(scope, identity)) {
                assertFailsWith<InvocationRejectedException> { service.request<JsonElement>() }
            }
            assertEquals("retries", failure.failure.code)
            assertEquals(1, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun ktorRedirectDoesNotIssueAnUnbudgetedSecondSend() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(ByteReadChannel(""), HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://redirected.test/forecast"))
        })
        try {
            val operation = Operation(
                operationId = "getForecast",
                responses = Responses(responses = mapOf(200 to ReferenceOr.Value(Response(description = "ok")))),
                extensions = mapOf("x-dicentra-aviator-serviceDelegateCall" to GenericElement("weather-provider")),
            )
            val spec = OpenApiDoc(
                info = OpenApiInfo(title = "test", version = "1"),
                servers = listOf(Server(url = "https://primary.test")),
                paths = mapOf("/forecast" to ReferenceOr.Value(PathItem(get = operation))),
                extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")),
            )
            val service = KtorAviatorServiceConverter(httpClient = client, serializers = listOf(kotlinx.serialization.json.Json))
                .build(spec) { install(JsonAdapter) }.single()
            service.request<JsonElement>()
            assertEquals(1, calls)
        } finally {
            client.close()
        }
    }

    private data class Prepared(override val invocation: CapabilityInvocation) : ResolvedOperation

    private fun policy() = CapabilityPolicy(
        ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
        PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL,
    )

    /** Test double for Ktor's policy-integration boundary; budget plugin behavior is tested separately. */
    private fun testBudgetPluginRegistry() = OrchestrationPluginRegistry(listOf(orchestrationPlugin("test-call-budget") {
        invocationPolicy = testBudgetPolicyPlugins().single()
    }))

    private fun testBudgetPolicyPlugins() = listOf(InvocationPolicyPlugin { _, budget, _ ->
        object : InvocationPolicy {
            private var calls = 0
            private var retries = 0

            override suspend fun acquireCall(
                metadata: ExecutionMetadata,
                identity: ConstituentIdentity,
                retry: Boolean,
            ): ExecutionFailure? {
                if (budget.maxCalls?.let { calls >= it } == true) return ExecutionFailure("calls", "test call limit")
                if (retry && budget.maxRetries?.let { retries >= it } == true) return ExecutionFailure("retries", "test retry limit")
                calls++
                if (retry) retries++
                return null
            }

            override suspend fun releaseCall(identity: ConstituentIdentity) = Unit
            override suspend fun checkpoint(
                metadata: ExecutionMetadata,
                transitions: Int,
                nestingDepth: Int,
            ): ExecutionFailure? = null
        }
    })

    private fun workflowIdentity() = WorkflowIdentity("arazzo", "1.1.0", "workflow:test", "revision-1")
}
