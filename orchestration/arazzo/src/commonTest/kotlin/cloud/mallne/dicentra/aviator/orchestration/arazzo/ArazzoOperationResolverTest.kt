package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoOperationResolution
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.resolveArazzoOperation
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.resolveArazzoEngineAction
import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.ArazzoEngineActionResolution
import cloud.mallne.dicentra.aviator.core.engine.EngineTemplate
import cloud.mallne.dicentra.aviator.core.plugins.EngineInvocationContext
import cloud.mallne.dicentra.aviator.core.engine.ExecutableDefinition
import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoStep
import cloud.mallne.dicentra.aviator.orchestration.model.ConstituentIdentity
import cloud.mallne.dicentra.aviator.orchestration.policy.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArazzoOperationResolverTest {
    private val locator = ServiceLocator("weather>gather")
    private val policy = CapabilityPolicy(
        ExecutionStrategy.AGGREGATE,
        ConstituentOrdering.RESOLUTION,
        PartialFailurePolicy.REQUIRE_ALL,
        EmptySelectionPolicy.FAIL,
    )
    private val template = object : EngineTemplate<SourceInput, Unit> {
        override val identity = "resolver-test"
        override suspend fun execute(
            definition: ExecutableDefinition,
            input: SourceInput,
            context: EngineInvocationContext,
        ): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun operationIdResolvesToExactSourceQualifiedIdentityAndRestrictsInvocation() {
        val forecast = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast", "/forecast")
        val sibling = ConstituentIdentity(locator, "provider-b", "spec-b", "rev-b", "getWeather", "/weather")

        val resolved = assertIs<ArazzoOperationResolution.Resolved>(
            resolveArazzoOperation(ArazzoStep("forecast", operationId = "getForecast"), listOf(forecast, sibling), policy),
        )

        assertEquals(locator, resolved.invocation.locator)
        assertEquals(setOf(forecast), resolved.invocation.constituents)
        assertEquals(true, resolved.invocation.accepts(forecast))
        assertEquals(false, resolved.invocation.accepts(sibling))
    }

    @Test
    fun duplicateOperationIdAcrossSourcesFailsInsteadOfFanout() {
        val first = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast", "/forecast")
        val second = ConstituentIdentity(locator, "provider-b", "spec-b", "rev-b", "getForecast", "/forecast")

        val failed = assertIs<ArazzoOperationResolution.Failed>(
            resolveArazzoOperation(ArazzoStep("forecast", operationId = "getForecast"), listOf(first, second), policy),
        )
        assertEquals("ambiguous_operation_id", failed.code)
    }

    @Test
    fun sourceQualifiedReferenceSelectsOnlyItsDeclaredSource() {
        val first = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast", "/forecast")
        val second = ConstituentIdentity(locator, "provider-b", "spec-b", "rev-b", "getForecast", "/forecast")

        val resolved = assertIs<ArazzoOperationResolution.Resolved>(
            resolveArazzoOperation(
                ArazzoStep("forecast", operationId = "\$sourceDescriptions.api.getForecast"),
                listOf(first, second), policy, mapOf("api" to "spec-b"),
            ),
        )
        assertEquals(setOf(second), resolved.invocation.constituents)
    }

    @Test
    fun sourceQualifiedReferenceWithoutSuppliedSourceFails() {
        val only = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast", "/forecast")
        val failed = assertIs<ArazzoOperationResolution.Failed>(
            resolveArazzoOperation(
                ArazzoStep("forecast", operationId = "\$sourceDescriptions.missing.getForecast"),
                listOf(only), policy,
            ),
        )
        assertEquals("source_not_found", failed.code)
    }

    @Test
    fun engineActionResolverPreservesMissingAndAmbiguousFailures() = kotlinx.coroutines.test.runTest {
        val candidate = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast")
        val missing = resolveArazzoEngineAction(
            ArazzoStep("missing", operationId = "notFound"),
            kotlinx.serialization.json.JsonNull,
            listOf(candidate),
            policy,
            template = template,
            matchesDefinition = { _, _ -> error("No definition matching expected") },
            inputFor = { _, _, identity -> SourceInput(identity.sourceId) },
            mapResult = { error("No execution expected in resolution test") },
        )
        assertEquals("operation_not_found", assertIs<ArazzoEngineActionResolution.Failed>(missing).code)

        val ambiguous = resolveArazzoEngineAction(
            ArazzoStep("ambiguous", operationId = "getForecast"),
            kotlinx.serialization.json.JsonNull,
            listOf(candidate, candidate.copy(sourceId = "spec-b")),
            policy,
            template = template,
            matchesDefinition = { _, _ -> error("No definition matching expected") },
            inputFor = { _, _, identity -> SourceInput(identity.sourceId) },
            mapResult = { error("No execution expected in resolution test") },
        )
        assertEquals("ambiguous_operation_id", assertIs<ArazzoEngineActionResolution.Failed>(ambiguous).code)
    }

    @Test
    fun engineActionResolverUsesExactSourceQualifiedIdentity() = kotlinx.coroutines.test.runTest {
        val first = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast")
        val second = ConstituentIdentity(locator, "provider-b", "spec-b", "rev-b", "getForecast")
        var mappedInput: SourceInput? = null
        val result = resolveArazzoEngineAction(
            ArazzoStep("forecast", operationId = "\$sourceDescriptions.api.getForecast"),
            kotlinx.serialization.json.JsonNull,
            listOf(first, second),
            policy,
            sourceIdsByName = mapOf("api" to "spec-b"),
            template = template,
            matchesDefinition = { _, _ -> error("No dispatch expected in resolution test") },
            inputFor = { _, _, identity -> SourceInput(identity.sourceId).also { mappedInput = it } },
            mapResult = { error("No execution expected in resolution test") },
        )
        val action = assertIs<ArazzoEngineActionResolution.Resolved>(result).action
        assertEquals(second, action.identity)
        assertEquals(SourceInput("spec-b"), mappedInput)
    }

    private data class SourceInput(val sourceId: String)

    @Test
    fun sourceQualifiedEngineActionDoesNotDispatchOtherDefinitionsWithTheSameLocator() = kotlinx.coroutines.test.runTest {
        val first = ConstituentIdentity(locator, "provider-a", "spec-a", "rev-a", "getForecast")
        val second = ConstituentIdentity(locator, "provider-b", "spec-b", "rev-b", "getForecast")
        val firstDefinition = object : ExecutableDefinition {
            override val locator = this@ArazzoOperationResolverTest.locator
            override val delegation = template.identity
        }
        val secondDefinition = object : ExecutableDefinition {
            override val locator = this@ArazzoOperationResolverTest.locator
            override val delegation = template.identity
        }
        val called = mutableListOf<ExecutableDefinition>()
        val dispatchTemplate = object : EngineTemplate<SourceInput, Unit> {
            override val identity = template.identity
            override suspend fun execute(
                definition: ExecutableDefinition,
                input: SourceInput,
                context: EngineInvocationContext,
            ): Result<Unit> {
                called += definition
                return Result.success(Unit)
            }
        }
        val engine = cloud.mallne.dicentra.aviator.core.engine.AviatorEngine(
            cloud.mallne.dicentra.aviator.core.engine.InMemoryDefinitionCoordinator(
                listOf(firstDefinition, secondDefinition),
            ),
            cloud.mallne.dicentra.aviator.core.engine.EngineDefinitionAuthorization.AllowAll,
            cloud.mallne.dicentra.aviator.core.engine.OutboundTargetPolicy { _, _, _ -> null },
        )
        val resolved = resolveArazzoEngineAction(
            ArazzoStep("forecast", operationId = "\$sourceDescriptions.api.getForecast"),
            kotlinx.serialization.json.JsonNull,
            listOf(first, second),
            policy,
            sourceIdsByName = mapOf("api" to "spec-b"),
            template = dispatchTemplate,
            matchesDefinition = { identity, definition ->
                identity == second && definition === secondDefinition
            },
            inputFor = { _, _, identity -> SourceInput(identity.sourceId) },
            mapResult = {
                cloud.mallne.dicentra.aviator.orchestration.model.CapabilityResult(
                    locator,
                    cloud.mallne.dicentra.aviator.orchestration.model.CapabilityOutcome.Succeeded(
                        cloud.mallne.dicentra.aviator.orchestration.model.BodyValue.Empty,
                    ),
                    emptyList(),
                )
            },
        )
        assertIs<ArazzoEngineActionResolution.Resolved>(resolved).action.execute(engine)
        assertEquals(listOf<ExecutableDefinition>(secondDefinition), called)
    }
}
