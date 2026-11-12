package cloud.mallne.dicentra.aviator.orchestration

import cloud.mallne.dicentra.aviator.core.execution.controlledAviatorCall
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.execution.*
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import cloud.mallne.dicentra.aviator.orchestration.plugin.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class MaterializedOperationBindingTest {
    @Test
    fun resultRepresentationSurvivesSerializationWithoutClaimingUnknownOrigin() {
        val transformed: ConstituentOutcome = ConstituentOutcome.Succeeded(
            BodyValue.JsonValue(JsonPrimitive("transformed")), ResultRepresentation.TRANSFORMED,
        )
        assertEquals(transformed, Json.decodeFromString<ConstituentOutcome>(Json.encodeToString(transformed)))
        assertEquals(ResultRepresentation.UNKNOWN, ConstituentOutcome.Succeeded(BodyValue.Empty).representation)
    }

    @Test
    fun aggregateNeedsNoSizeEstimateOrReservation() = runTest {
        val aggregate = BodyValue.JsonValue(JsonPrimitive("result".repeat(1024)))
        var aggregateCalls = 0
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<Unit>(first) {
                controlledAviatorCall(call = {
                    ConstituentExecution(ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive("ok"))))
                })
            }),
            isUsable = { true },
            aggregate = { aggregateCalls++; aggregate },
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        )
        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)
        assertEquals(aggregate, assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
        assertEquals(1, aggregateCalls)
    }

    @Test
    fun nullableJsonClassifierDistinguishesJsonNullAndAbsentResponse() {
        assertEquals(ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonNull)),
            ConstituentExecution.decodedJson(JsonNull).classifiedOutcome(null))
        assertEquals(ConstituentOutcome.Failed(ExecutionFailure("absent_result", "No decoded response available")),
            ConstituentExecution.decodedJson(null).classifiedOutcome(null))
    }

    private val locator = ServiceLocator("weather>gather")
    private val first = ConstituentIdentity(locator, "a", "source-a", "rev-a", "forecast")
    private val second = ConstituentIdentity(locator, "b", "source-b", "rev-b", "weather")
    private val policy = CapabilityPolicy(ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
        PartialFailurePolicy.ALLOW_PARTIAL, EmptySelectionPolicy.FAIL)

    @Test
    fun actualControlledCallsAggregateAndKeepProvenanceUnderPartialDenial() = runTest {
        val calls = mutableListOf<ConstituentIdentity>()
        val authorized = mutableListOf<ConstituentIdentity>()
        val binding = binding(calls)
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "source", "revision")),
            ExecutionBudget(), authorizeCall = { identity ->
                authorized += identity
                if (identity == second) ExecutionFailure("denied", "Denied") else null
            },
        )
        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)

        assertEquals(listOf(first, second), authorized)
        assertEquals(listOf(first), calls)
        assertEquals(listOf(first, second), result.constituents.map { it.identity })
        assertEquals("denied", assertIs<ConstituentOutcome.Failed>(result.constituents[1].outcome).failure.code)
        val value = assertIs<BodyValue.JsonValue>(assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
        assertEquals(JsonArray(listOf(JsonPrimitive("forecast"))), value.value)
    }

    @Test
    fun fanOutKeepsEachAttemptOutcomeOwnedByItsConstituent() = runTest {
        val attempts = mutableListOf<Pair<OrchestrationStage, ConstituentIdentity>>()
        val plugins = installOrchestrationPlugins {
            install("fanout-attempt-owner") {
                stages += OrchestrationStage.AFTER_ATTEMPT
                stages += OrchestrationStage.FAILURE
                lifecycleScopes += OrchestrationLifecycleScope.ATTEMPT
                executor = OrchestrationPluginExecutor { context ->
                    attempts += context.stage to checkNotNull(context.constituent)
                }
            }
        }
        val binding = MaterializedOperationBinding(
            listOf(
                MaterializedConstituent<Unit>(first) {
                    controlledAviatorCall<Unit> { error("first constituent failed") }
                    ConstituentExecution.decodedJson(JsonPrimitive("unreachable"))
                },
                MaterializedConstituent<Unit>(second) {
                    controlledAviatorCall { ConstituentExecution.decodedJson(JsonPrimitive("second-result")) }
                },
            ),
            isUsable = { it is BodyValue.JsonValue },
            aggregate = { values -> BodyValue.JsonValue(JsonArray(values.map { (it as BodyValue.JsonValue).value })) },
            plugins = plugins,
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("fanout-ownership"), WorkflowIdentity("test", "1", "source", "revision")),
            ExecutionBudget(),
        )

        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)

        assertEquals(listOf(OrchestrationStage.FAILURE to first, OrchestrationStage.AFTER_ATTEMPT to second), attempts)
        assertEquals(listOf(first, second), result.constituents.map { it.identity })
        assertEquals("constituent_failed", assertIs<ConstituentOutcome.Failed>(result.constituents[0].outcome).failure.code)
        assertEquals(ConstituentOutcome.Succeeded(
            BodyValue.JsonValue(JsonPrimitive("second-result")), ResultRepresentation.UNKNOWN),
            result.constituents[1].outcome)
        assertEquals(BodyValue.JsonValue(JsonArray(listOf(JsonPrimitive("second-result")))),
            assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
    }

    @Test
    fun failuresAtResultAggregateAndOutputHooksUnwindAndCleanup() = runTest {
        for (failingStage in listOf(
            OrchestrationStage.SCHEDULE,
            OrchestrationStage.MAP_INPUT,
            OrchestrationStage.HANDLE_RESULT,
            OrchestrationStage.AGGREGATE,
            OrchestrationStage.MAP_OUTPUT,
            OrchestrationStage.COMPLETE,
        )) {
            var calls = 0
            val unwound = mutableListOf<OrchestrationStage>()
            val plugins = installOrchestrationPlugins {
                install("stage-failure") {
                    stages += failingStage
                    executor = OrchestrationPluginExecutor { error("failure at $failingStage") }
                }
                install("unwind-observer") {
                    stages += OrchestrationStage.FAILURE
                    stages += OrchestrationStage.CLEANUP
                    executor = OrchestrationPluginExecutor { unwound += it.stage }
                }
            }
            val binding = MaterializedOperationBinding(
                listOf(MaterializedConstituent<Unit>(first) {
                    calls++
                    ConstituentExecution.decodedJson(JsonPrimitive("source"))
                }),
                isUsable = { it is BodyValue.JsonValue }, aggregate = { it.single() }, plugins = plugins,
            )
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("failure-$failingStage"), WorkflowIdentity("test", "1", "source", "revision")),
                ExecutionBudget(),
            )
            val operation = binding.resolve(CapabilityInvocation(locator, policy))

            val failure = assertFailsWith<IllegalStateException> { binding.invoke(operation, Unit, scope) }

            assertEquals("failure at $failingStage", failure.message)
            assertEquals(
                if (failingStage == OrchestrationStage.SCHEDULE || failingStage == OrchestrationStage.MAP_INPUT) 0 else 1,
                calls,
                "$failingStage failure must occur at the expected side of dispatch",
            )
            assertEquals(listOf(OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), unwound)
        }
    }

    @Test
    fun failureAndCleanupHookExceptionsAreSuppressedWithoutReplacingPrimaryFailure() = runTest {
        val observed = mutableListOf<OrchestrationStage>()
        val plugins = installOrchestrationPlugins {
            install("primary") {
                stages += OrchestrationStage.AGGREGATE
                executor = OrchestrationPluginExecutor { error("primary execution failure") }
            }
            install("unwind-failure") {
                stages += OrchestrationStage.FAILURE
                executor = OrchestrationPluginExecutor {
                    observed += it.stage
                    error("failure hook failure")
                }
            }
            install("unwind-cleanup") {
                stages += OrchestrationStage.CLEANUP
                executor = OrchestrationPluginExecutor {
                    observed += it.stage
                    error("cleanup hook failure")
                }
            }
        }
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<Unit>(first) {
                ConstituentExecution.decodedJson(JsonPrimitive("source"))
            }),
            isUsable = { it is BodyValue.JsonValue }, aggregate = { it.single() }, plugins = plugins,
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("suppressed-unwind"), WorkflowIdentity("test", "1", "source", "revision")),
            ExecutionBudget(),
        )

        val primary = assertFailsWith<IllegalStateException> {
            binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)
        }

        assertEquals("primary execution failure", primary.message)
        assertEquals(listOf(OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), observed)
        assertEquals(listOf("failure hook failure", "cleanup hook failure"),
            primary.suppressedExceptions.map { it.message })
    }

    @Test
    fun concurrentRunsOnOneBindingKeepPluginStateAndResultsIsolated() = runTest {
        val gates = mapOf("run-a" to CompletableDeferred<Unit>(), "run-b" to CompletableDeferred<Unit>())
        val completed = mutableListOf<Pair<String, String?>>()
        val plugins = installOrchestrationPlugins {
            install("run-isolation") {
                stages += OrchestrationStage.MAP_INPUT
                stages += OrchestrationStage.COMPLETE
                executor = OrchestrationPluginExecutor { context ->
                    if (context.stage == OrchestrationStage.MAP_INPUT) {
                        context.state.put("owner", context.scope!!.metadata.runId.value)
                    } else {
                        completed += context.scope!!.metadata.runId.value to (context.state.get("owner") as? String)
                    }
                }
            }
        }
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<String>(first) { runId ->
                gates.getValue(runId).await()
                ConstituentExecution.decodedJson(JsonPrimitive(runId))
            }),
            isUsable = { it is BodyValue.JsonValue }, aggregate = { it.single() }, plugins = plugins,
        )
        val operation = binding.resolve(CapabilityInvocation(locator, policy))
        fun scope(runId: String) = EnforcedInvocationScope(
            ExecutionMetadata(RunId(runId), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        )

        val runA = launch(start = CoroutineStart.UNDISPATCHED) { binding.invoke(operation, "run-a", scope("run-a")) }
        val runB = launch(start = CoroutineStart.UNDISPATCHED) { binding.invoke(operation, "run-b", scope("run-b")) }
        gates.getValue("run-b").complete(Unit)
        gates.getValue("run-a").complete(Unit)
        runA.join()
        runB.join()

        assertEquals(listOf("run-a" to "run-a", "run-b" to "run-b"), completed.sortedBy { it.first })
    }

    @Test
    fun exactRestrictionInvokesOnlyRequestedOperation() = runTest {
        val calls = mutableListOf<ConstituentIdentity>()
        val binding = binding(calls)
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        )
        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy, setOf(second))), Unit, scope)
        assertIs<CapabilityOutcome.Succeeded>(result.outcome)
        assertEquals(listOf(second), calls)
    }

    @Test
    fun pluginTransformsResultsAndPreservesRealSourceProvenance() = runTest {
        val plugins = installOrchestrationPlugins {
            install("transform") {
                executor = OrchestrationPluginExecutor { context ->
                    if (context.stage == OrchestrationStage.HANDLE_RESULT) {
                        context.result = context.result!!.copy(
                            outcome = CapabilityOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive("derived"))),
                            constituents = listOf(ConstituentResult(second,
                                ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive("forged"))),
                                ResponseSnapshot(BodyValue.Empty, http = HttpResponseMetadata(599)))),
                        )
                    }
                }
            }
        }
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<Unit>(first) {
                controlledAviatorCall(call = {
                    ConstituentExecution(
                        ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive("source"))),
                        ResponseSnapshot(BodyValue.BytesValue(listOf(1.toByte())), "application/json",
                            HttpResponseMetadata(200, mapOf("x-source" to listOf("real")))),
                    )
                })
            }),
            isUsable = { it is BodyValue.JsonValue },
            aggregate = { it.single() },
            plugins = plugins,
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        )

        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)
        assertEquals(BodyValue.JsonValue(JsonPrimitive("derived")),
            assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
        assertEquals(ResultRepresentation.TRANSFORMED,
            assertIs<ConstituentOutcome.Succeeded>(result.constituents.single().outcome).representation)
        assertEquals(first, result.constituents.single().identity)
        assertEquals(200, result.constituents.single().response?.http?.status)
        assertEquals(listOf("real"), result.constituents.single().response?.http?.headers?.get("x-source"))
    }

    @Test
    fun inputResultAggregationOutputAndCompletionMutationsFlowInOrder() = runTest {
        val seen = mutableListOf<String>()
        val plugins = installOrchestrationPlugins {
            install("pipeline-transform") {
                stages += OrchestrationStage.MAP_INPUT
                stages += OrchestrationStage.HANDLE_RESULT
                stages += OrchestrationStage.AGGREGATE
                stages += OrchestrationStage.MAP_OUTPUT
                stages += OrchestrationStage.COMPLETE
                executor = OrchestrationPluginExecutor { context ->
                    when (context.stage) {
                        OrchestrationStage.MAP_INPUT -> context.input = "mapped-input"
                        OrchestrationStage.HANDLE_RESULT -> {
                            context.result = checkNotNull(context.result).copy(outcome = CapabilityOutcome.Succeeded(
                                BodyValue.JsonValue(JsonPrimitive("handled"))))
                        }
                        OrchestrationStage.AGGREGATE -> {
                            seen += "aggregate:${(context.input as BodyValue.JsonValue).value}"
                            context.input = BodyValue.JsonValue(JsonPrimitive("aggregated"))
                        }
                        OrchestrationStage.MAP_OUTPUT -> {
                            seen += "output:${(context.input as BodyValue.JsonValue).value}"
                            context.input = BodyValue.JsonValue(JsonPrimitive("mapped-output"))
                        }
                        OrchestrationStage.COMPLETE -> {
                            val result = checkNotNull(context.result)
                            val terminalInput = (result.outcome as CapabilityOutcome.Succeeded).aggregate as BodyValue.JsonValue
                            seen += "complete:${terminalInput.value}"
                            context.result = result.copy(outcome = CapabilityOutcome.Succeeded(
                                BodyValue.JsonValue(JsonPrimitive("terminal"))))
                        }
                        else -> Unit
                    }
                }
            }
        }
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<String>(first) { input ->
                assertEquals("mapped-input", input)
                controlledAviatorCall(call = { ConstituentExecution.decodedJson(JsonPrimitive("source")) })
            }),
            isUsable = { it is BodyValue.JsonValue },
            aggregate = { it.single() },
            plugins = plugins,
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("stage-flow"), WorkflowIdentity("test", "1", "source", "revision")),
            ExecutionBudget(),
        )

        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), "original", scope)

        assertEquals(listOf("aggregate:\"handled\"", "output:\"aggregated\"", "complete:\"mapped-output\""), seen)
        assertEquals(BodyValue.JsonValue(JsonPrimitive("terminal")), assertIs<CapabilityOutcome.Succeeded>(result.outcome).aggregate)
        assertEquals(first, result.constituents.single().identity)
    }

    @Test
    fun everyMaterializedLifecycleStageRejectsWithoutReturningSuccess() = runTest {
        val rejectingStages = listOf(
            OrchestrationStage.MAP_INPUT,
            OrchestrationStage.SCHEDULE,
            OrchestrationStage.HANDLE_RESULT,
            OrchestrationStage.AGGREGATE,
            OrchestrationStage.MAP_OUTPUT,
            OrchestrationStage.COMPLETE,
        )
        for (stage in rejectingStages) {
            var transportCalls = 0
            val plugins = installOrchestrationPlugins {
                install("reject-$stage") {
                    stages += stage
                    executor = OrchestrationPluginExecutor { it.reject(ExecutionFailure("stage_rejected", "$stage rejected")) }
                }
            }
            val binding = MaterializedOperationBinding(
                listOf(MaterializedConstituent<Unit>(first) {
                    controlledAviatorCall(call = { transportCalls++ })
                    ConstituentExecution.decodedJson(JsonPrimitive("source"))
                }),
                isUsable = { it is BodyValue.JsonValue }, aggregate = { it.single() }, plugins = plugins,
            )
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("reject-$stage"), WorkflowIdentity("test", "1", "source", "revision")),
                ExecutionBudget(),
            )

            val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)

            assertIs<CapabilityOutcome.Failed>(result.outcome, "stage $stage must not report success")
            if (stage == OrchestrationStage.MAP_INPUT || stage == OrchestrationStage.SCHEDULE) {
                assertEquals(0, transportCalls, "stage $stage rejected before dispatch")
            }
        }
    }

    @Test
    fun cachedShortCircuitStillChecksHostAuthorization() = runTest {
        val plugins = installOrchestrationPlugins {
            install("cache") {
                executor = OrchestrationPluginExecutor { context ->
                    if (context.stage == OrchestrationStage.SCHEDULE) {
                        context.stopWith(CapabilityResult(locator,
                            CapabilityOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive("cached"))), emptyList()))
                    }
                }
            }
        }
        var calls = 0
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<Unit>(first) { calls++; ConstituentExecution.decodedJson(JsonPrimitive("network")) }),
            isUsable = { true }, aggregate = { it.single() }, plugins = plugins,
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("run"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
            authorizeCall = { ExecutionFailure("denied", "Denied") },
        )

        val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)
        assertEquals(0, calls)
        assertEquals("constituents_failed", assertIs<CapabilityOutcome.Failed>(result.outcome).failure.code)
        assertEquals(first, result.constituents.single().identity)
        assertEquals("denied", assertIs<ConstituentOutcome.Failed>(result.constituents.single().outcome).failure.code)
    }

    @Test
    fun transportHooksObserveEachActualRetryAndCleanup() = runTest {
        val retries = mutableListOf<Boolean>()
        val calls = mutableListOf<Int>()
        var attemptCleanups = 0
        val plugins = installOrchestrationPlugins {
            install("attempt-observer") {
                stages += OrchestrationStage.AFTER_ATTEMPT
                stages += OrchestrationStage.CLEANUP
                lifecycleScopes += OrchestrationLifecycleScope.ATTEMPT
                executor = OrchestrationPluginExecutor { context ->
                    if (context.stage == OrchestrationStage.AFTER_ATTEMPT) {
                        retries += (context.payload as OrchestrationAttempt).retry
                    }
                    if (context.stage == OrchestrationStage.CLEANUP) attemptCleanups++
                }
            }
        }
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<Unit>(first) {
                controlledAviatorCall(call = { calls += 1 })
                controlledAviatorCall(retry = true, call = { calls += 2 })
                ConstituentExecution.decodedJson(JsonPrimitive("ok"))
            }),
            isUsable = { true }, aggregate = { it.single() }, plugins = plugins,
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("attempts"), WorkflowIdentity("test", "1", "source", "revision")),
            ExecutionBudget(),
        )

        binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)

        assertEquals(listOf(1, 2), calls)
        assertEquals(listOf(false, true), retries)
        assertEquals(2, attemptCleanups)
    }

    @Test
    fun thrownTransportHooksNotifyFailureReleaseAdmissionAndRunAttemptCleanup() = runTest {
        for (failingStage in listOf(OrchestrationStage.BEFORE_ATTEMPT, OrchestrationStage.AFTER_ATTEMPT)) {
            val observed = mutableListOf<OrchestrationStage>()
            var transportCalls = 0
            var acquired = 0
            var released = 0
            val plugins = installOrchestrationPlugins {
                install("throw-$failingStage") {
                    stages += failingStage
                    lifecycleScopes += OrchestrationLifecycleScope.ATTEMPT
                    executor = OrchestrationPluginExecutor { error("private attempt detail") }
                }
                install("observe-$failingStage") {
                    stages += OrchestrationStage.FAILURE
                    stages += OrchestrationStage.CLEANUP
                    lifecycleScopes += OrchestrationLifecycleScope.ATTEMPT
                    executor = OrchestrationPluginExecutor { context -> observed += context.stage }
                }
            }
            val binding = MaterializedOperationBinding(
                listOf(MaterializedConstituent<Unit>(first) {
                    controlledAviatorCall(call = { transportCalls++ })
                    ConstituentExecution.decodedJson(JsonPrimitive("unexpected"))
                }),
                isUsable = { it is BodyValue.JsonValue }, aggregate = { it.single() }, plugins = plugins,
            )
            val scope = EnforcedInvocationScope(
                ExecutionMetadata(RunId("attempt-$failingStage"), WorkflowIdentity("test", "1", "source", "revision")),
                ExecutionBudget(),
                policyPlugins = listOf(InvocationPolicyPlugin { _, _, _ ->
                    object : InvocationPolicy {
                        override suspend fun acquireCall(metadata: ExecutionMetadata, identity: ConstituentIdentity,
                            retry: Boolean): ExecutionFailure? { acquired++; return null }
                        override suspend fun releaseCall(identity: ConstituentIdentity) { released++ }
                        override suspend fun checkpoint(metadata: ExecutionMetadata, transitions: Int,
                            nestingDepth: Int): ExecutionFailure? = null
                    }
                }),
            )

            val result = binding.invoke(binding.resolve(CapabilityInvocation(locator, policy)), Unit, scope)

            assertEquals(if (failingStage == OrchestrationStage.BEFORE_ATTEMPT) 0 else 1, transportCalls)
            assertEquals(listOf(OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), observed)
            assertEquals("constituents_failed", assertIs<CapabilityOutcome.Failed>(result.outcome).failure.code)
            assertEquals(if (failingStage == OrchestrationStage.BEFORE_ATTEMPT) 0 else 1, acquired)
            assertEquals(if (failingStage == OrchestrationStage.BEFORE_ATTEMPT) 0 else 1, released,
                "attempt failure must not leak or double-release admission")
        }
    }

    @Test
    fun cancellationRunsFailureAndBindingCleanupExactlyOnce() = runTest {
        val observedStages = mutableListOf<OrchestrationStage>()
        val plugins = installOrchestrationPlugins {
            install("cleanup") {
                this.stages += OrchestrationStage.FAILURE
                this.stages += OrchestrationStage.CLEANUP
                lifecycleScopes += OrchestrationLifecycleScope.BINDING
                executor = OrchestrationPluginExecutor { observedStages += it.stage }
            }
        }
        val binding = MaterializedOperationBinding(
            listOf(MaterializedConstituent<Unit>(first) {
                controlledAviatorCall { awaitCancellation() }
                ConstituentExecution.decodedJson(JsonPrimitive("unreachable"))
            }),
            isUsable = { true }, aggregate = { it.single() }, plugins = plugins,
        )
        val scope = EnforcedInvocationScope(
            ExecutionMetadata(RunId("cancel"), WorkflowIdentity("test", "1", "source", "revision")), ExecutionBudget(),
        )
        val resolved = binding.resolve(CapabilityInvocation(locator, policy))
        val task = launch(start = CoroutineStart.UNDISPATCHED) { binding.invoke(resolved, Unit, scope) }

        task.cancelAndJoin()

        assertEquals(listOf(OrchestrationStage.FAILURE, OrchestrationStage.CLEANUP), observedStages)
    }

    private fun binding(calls: MutableList<ConstituentIdentity>) = MaterializedOperationBinding(
        listOf(first, second).map { identity ->
            MaterializedConstituent<Unit>(identity) {
                controlledAviatorCall(call = {
                    calls += identity
                    ConstituentExecution(ConstituentOutcome.Succeeded(BodyValue.JsonValue(JsonPrimitive(identity.operationId))))
                })
            }
        },
        isUsable = { it is BodyValue.JsonValue },
        aggregate = { values -> BodyValue.JsonValue(JsonArray(values.map { (it as BodyValue.JsonValue).value })) },
    )
}
