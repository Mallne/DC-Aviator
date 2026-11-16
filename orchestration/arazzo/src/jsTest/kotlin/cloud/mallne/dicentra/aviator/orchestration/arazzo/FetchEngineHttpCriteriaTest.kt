package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.client.fetch.FetchAviatorServiceConverter
import cloud.mallne.dicentra.aviator.client.fetch.FetchClientDefinition
import cloud.mallne.dicentra.aviator.client.fetch.FetchClientInvocationException
import cloud.mallne.dicentra.aviator.client.fetch.fetchEngineTemplate
import cloud.mallne.dicentra.aviator.core.NoBody
import cloud.mallne.dicentra.aviator.core.engine.*
import cloud.mallne.dicentra.aviator.koas.typed.routes
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.*
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.*
import cloud.mallne.dicentra.aviator.orchestration.model.*
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import io.ktor.openapi.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FetchEngineHttpCriteriaTest {
    @Test
    fun workflowCriteriaCanAccept404WithoutChangingDirectClientFailure() = runTest {
        var calls = 0
        val fetcher: dynamic = { _: String, _: dynamic ->
            calls++
            val status = if (calls == 1) 404 else 200
            js("Promise.resolve(new Response('', {status: status}))")
        }
        val locator = ServiceLocator("lookup")
        val api = OpenApiDoc(
            info = OpenApiInfo(title = "lookup", version = "1"),
            servers = listOf(Server("https://example.test")),
            paths = mapOf("/item" to ReferenceOr.Value(PathItem(get = Operation(
                operationId = "lookup", responses = Responses(),
            )))),
        )
        val definition = FetchClientDefinition(
            FetchAviatorServiceConverter(fetcher = fetcher).buildOperation(api, api.routes().single(), locator),
        )
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(definition, definition)), EngineDefinitionAuthorization.AllowAll,
            OutboundTargetPolicy { _, _, _ -> null },
        )
        val identity = ConstituentIdentity(locator, "provider", "source", "rev", "lookup")
        val policy = CapabilityPolicy(
            ExecutionStrategy.AGGREGATE, ConstituentOrdering.RESOLUTION,
            PartialFailurePolicy.REQUIRE_ALL, EmptySelectionPolicy.FAIL,
        )
        val template = fetchEngineTemplate<NoBody>()
        val runner = ArazzoRunExecutor(ArazzoEngineStepExecutor(engine, policy) { step, input ->
            resolveArazzoEngineAction(
                step, input, listOf(identity), policy, template = template,
                matchesDefinition = { target, candidate -> target == identity && candidate === definition },
                inputFor = { _, _, _ -> OpenApiClientInput<NoBody>() },
                mapResult = { execution ->
                    val response = checkNotNull(execution.networkChain.single().response)
                    CapabilityResult(locator, CapabilityOutcome.Succeeded(BodyValue.Empty), listOf(
                        ConstituentResult(identity, ConstituentOutcome.Succeeded(BodyValue.Empty),
                            ResponseSnapshot(BodyValue.Empty, http = HttpResponseMetadata(response.status.value))),
                    ))
                },
            )
        })
        val workflowIdentity = WorkflowIdentity("arazzo", "1.1.0", "doc", "rev")
        val accepted = runner.execute(
            ArazzoWorkflow("workflow", listOf(ArazzoStep("lookup", operationId = "lookup",
                successCriteria = listOf(ArazzoCriterion("\$statusCode == 404"))))),
            workflowIdentity, RunId("accept-404"),
        )
        assertIs<ExecutionOutcome.Succeeded>(accepted.outcome)
        assertEquals(1, calls)
        calls = 0
        val rejected = runner.execute(
            ArazzoWorkflow("workflow", listOf(ArazzoStep("lookup", operationId = "lookup"))),
            workflowIdentity, RunId("reject-404"),
        )
        assertEquals("success_criteria", assertIs<ExecutionOutcome.Failed>(rejected.outcome).failure.code)
        assertEquals(1, calls)
        calls = 0
        val direct = engine.invoke(locator, template, OpenApiClientInput(), InvocationMode.FIRST).single()
        assertIs<FetchClientInvocationException>(direct.result.exceptionOrNull())
        calls = 0
        val fallback = engine.invoke(locator, template, OpenApiClientInput(), InvocationMode.FIRST_NOT_FAILING)
        assertEquals(2, calls)
        assertEquals(true, fallback.last().result.isSuccess)
    }
}
