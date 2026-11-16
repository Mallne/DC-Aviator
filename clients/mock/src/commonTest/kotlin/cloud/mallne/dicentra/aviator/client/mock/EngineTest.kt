package cloud.mallne.dicentra.aviator.client.mock

import cloud.mallne.dicentra.aviator.core.engine.*
import cloud.mallne.dicentra.aviator.core.plugins.*
import cloud.mallne.dicentra.aviator.core.engine.OutboundTargetPolicy
import cloud.mallne.dicentra.aviator.core.NoBody
import kotlinx.serialization.Serializable
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.koas.typed.routes
import io.ktor.openapi.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EngineTest {
    private val locator = ServiceLocator("mock-weather")

    @Serializable
    private data class AuthorizationBody(val account: String)

    @Test
    fun mockOperationRunsThroughEngine() = runTest {
        val api = OpenApiDoc(info = OpenApiInfo(title = "mock", version = "1"),
            servers = listOf(Server(url = "https://mock.test")),
            paths = mapOf("/weather" to ReferenceOr.Value(PathItem(get = Operation(
                operationId = "weather", responses = Responses(), extensions = mapOf(
                    "x-dicentra-aviator-serviceDelegateCall" to GenericElement("mock-weather"),
                    "x-dicentra-aviator-serviceOptions" to GenericElement("{}"),
                ),
            )))), extensions = mapOf("x-dicentra-aviator" to GenericElement("1.0.0")))
        val service = MockConverter().build(api).single()
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(MockClientDefinition(service))),
            EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
            OutboundTargetPolicy { _, _, _ -> null },
        )

        val context = engine.invoke(
            locator, MockEngineTemplate, OpenApiClientInput(), InvocationMode.FIRST,
        ).single().result.getOrThrow()
        assertTrue(context.networkChain.any { it.response != null })
    }

    @Test
    fun actionLocatorCanSelectPlainOpenApiOperation() = runTest {
        val api = OpenApiDoc(
            info = OpenApiInfo(title = "plain", version = "1"),
            servers = listOf(Server(url = "https://mock.test")),
            paths = mapOf("/weather" to ReferenceOr.Value(PathItem(get = Operation(
                operationId = "weather", responses = Responses(),
            )))),
        )
        val definition = MockEngineDefinitionConverter(MockConverter()).convert(
            OpenApiOperationDefinitionRequest(api, api.routes().single(), locator),
        )
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(definition)),
            EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
            OutboundTargetPolicy { _, _, _ -> null },
        )

        val context = engine.invoke(
            locator, MockEngineTemplate, OpenApiClientInput(), InvocationMode.FIRST,
        ).single().result.getOrThrow()
        assertEquals(locator, context.dataHolder.serviceLocator)
        assertTrue(context.networkChain.any { it.response != null })
    }

    @Test
    fun operationConfigurationActivatesAndReconfiguresEngineRegisteredPlugin() = runTest {
        val configuration = kotlinx.serialization.json.JsonPrimitive(true)
        val api = OpenApiDoc(
            info = OpenApiInfo(title = "configured", version = "1"),
            servers = listOf(Server(url = "https://mock.test")),
            paths = mapOf("/weather" to ReferenceOr.Value(PathItem(get = Operation(
                operationId = "weather", responses = Responses(),
                extensions = mapOf(
                    "x-dicentra-aviator-pluginMaterialization" to GenericElement(mapOf("configured" to configuration)),
                ),
            )))),
        )
        val definition = MockEngineDefinitionConverter(MockConverter()).convert(
            OpenApiOperationDefinitionRequest(api, api.routes().single(), locator),
        )
        var reconfigurations = 0
        var invocations = 0
        val plugin = object : EngineAviatorPluginInstance {
            override val identity = "configured"
            override val configurationBundle = TestPluginConfig()
            override fun requestReconfigure(oasConfig: kotlinx.serialization.json.JsonElement): EngineAviatorPluginInstance {
                assertEquals(configuration, oasConfig)
                reconfigurations++
                return this
            }
            override suspend fun accepts(locator: ServiceLocator, definition: ExecutableDefinition): Boolean {
                invocations++
                return true
            }
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(definition)),
            EngineDefinitionAuthorization.AllowAll,
            OutboundTargetPolicy { _, _, _ -> null },
            DefaultAviatorPluginRegistry(engine = listOf(EnginePluginRegistration(plugin))),
        )

        engine.invoke(locator, MockEngineTemplate, OpenApiClientInput(), InvocationMode.FIRST).single().result.getOrThrow()

        assertEquals(mapOf("configured" to configuration), definition.pluginConfiguration)
        assertEquals(1, reconfigurations)
        assertEquals(1, invocations)
    }

    @Test
    fun firstModesDoNotConfigureUnvisitedCandidates() = runTest {
        val configured = mutableListOf<String>()
        val definitions = listOf(TestDefinition(locator, "good"), TestDefinition(locator, "broken"))
        val registry = object : AviatorPluginRegistry {
            override suspend fun invocationPlugins(
                locator: ServiceLocator,
                requested: Map<String, kotlinx.serialization.json.JsonElement>,
            ): AviatorPluginSnapshot {
                configured += "configured"
                check(configured.size == 1) { "Invalid later plugin configuration" }
                return AviatorPluginSnapshot()
            }
        }
        val template = object : EngineTemplate<Unit, Unit> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition, input: Unit, context: EngineInvocationContext,
            ) = Result.success(Unit)
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(definitions), EngineDefinitionAuthorization.AllowAll,
            OutboundTargetPolicy { _, _, _ -> null }, registry,
        )
        for (mode in listOf(InvocationMode.FIRST, InvocationMode.FIRST_NOT_FAILING)) {
            configured.clear()
            assertTrue(engine.invoke(locator, template, Unit, mode).single().result.isSuccess)
            assertEquals(1, configured.size)
        }
        configured.clear()
        val all = engine.invoke(locator, template, Unit, InvocationMode.ALL)
        assertTrue(all.first().result.isSuccess)
        assertIs<IllegalStateException>(all.last().result.exceptionOrNull())
    }

    @Test
    fun configurationFailureIsRetainedAndFallbackContinues() = runTest {
        val broken = TestDefinition(locator, "broken", mapOf("broken" to kotlinx.serialization.json.JsonNull))
        val good = TestDefinition(locator, "good")
        val template = object : EngineTemplate<Unit, Unit> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition, input: Unit, context: EngineInvocationContext,
            ) = Result.success(Unit)
        }
        val registry = object : AviatorPluginRegistry {
            override suspend fun invocationPlugins(
                locator: ServiceLocator,
                requested: Map<String, kotlinx.serialization.json.JsonElement>,
            ): AviatorPluginSnapshot {
                check("broken" !in requested) { "Invalid configuration" }
                return AviatorPluginSnapshot()
            }
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(broken, good)), EngineDefinitionAuthorization.AllowAll,
            OutboundTargetPolicy { _, _, _ -> null }, registry,
        )
        val outcomes = engine.invoke(locator, template, Unit, InvocationMode.FIRST_NOT_FAILING)
        assertEquals(2, outcomes.size)
        assertIs<IllegalStateException>(outcomes.first().result.exceptionOrNull())
        assertTrue(outcomes.last().result.isSuccess)
    }

    @Test
    fun modesPreserveOrderAndStopAtExpectedPoint() = runTest {
        val definitions = listOf(TestDefinition(locator, "bad"), TestDefinition(locator, "good"), TestDefinition(locator, "later"))
        val template = object : EngineTemplate<Unit, TextEngineResult> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition,
                input: Unit,
                context: EngineInvocationContext,
            ): Result<TextEngineResult> {
                val id = (definition as TestDefinition).id
                return if (id == "bad") Result.failure(IllegalStateException("failed"))
                else Result.success(TextEngineResult(id))
            }
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(definitions),
            EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed }, OutboundTargetPolicy { _, _, _ -> null },
        )
        assertEquals(3, engine.invoke(locator, template, Unit, InvocationMode.ALL).size)
        assertEquals(1, engine.invoke(locator, template, Unit, InvocationMode.FIRST).size)
        val firstSuccess = engine.invoke(locator, template, Unit, InvocationMode.FIRST_NOT_FAILING)
        assertEquals(2, firstSuccess.size)
        assertTrue(firstSuccess.first().result.isFailure)
        assertEquals(TextEngineResult("good"), firstSuccess.last().result.getOrThrow())
    }

    @Test
    fun definitionSelectionRestrictsFallbackBeforePluginsAndAuthorization() = runTest {
        val selected = TestDefinition(locator, "selected")
        val sibling = TestDefinition(locator, "sibling")
        val filtered = mutableListOf<ExecutableDefinition>()
        val authorized = mutableListOf<ExecutableDefinition>()
        val executed = mutableListOf<ExecutableDefinition>()
        val filter = object : EngineAviatorPluginInstance {
            override val identity = "observe-filter"
            override val configurationBundle = TestPluginConfig()
            override suspend fun accepts(locator: ServiceLocator, definition: ExecutableDefinition): Boolean {
                filtered += definition
                return true
            }
        }
        val template = object : EngineTemplate<Unit, Unit> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition,
                input: Unit,
                context: EngineInvocationContext,
            ): Result<Unit> {
                executed += definition
                return Result.failure(IllegalStateException("Selected source failed"))
            }
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(sibling, selected)),
            EngineDefinitionAuthorization { context ->
                authorized += context.definition
                EngineAuthorizationDecision.Allowed
            },
            OutboundTargetPolicy { _, _, _ -> null },
            DefaultAviatorPluginRegistry(engine = listOf(EnginePluginRegistration(filter, PluginActivation.PASSIVE))),
        )
        val results = engine.invoke(
            locator, template, Unit, InvocationMode.FIRST_NOT_FAILING,
            selectDefinition = { it === selected },
        )

        assertTrue(results.single().result.isFailure)
        assertEquals(listOf<ExecutableDefinition>(selected), filtered)
        assertEquals(listOf<ExecutableDefinition>(selected), authorized)
        assertEquals(listOf<ExecutableDefinition>(selected), executed)
        assertFailsWith<IllegalArgumentException> {
            engine.invoke(locator, template, Unit, InvocationMode.FIRST_NOT_FAILING, selectDefinition = { false })
        }
        assertEquals(1, executed.size)
    }

    @Test
    fun cancellationIsNeverTreatedAsFailure() = runTest {
        val template = object : EngineTemplate<Unit, Unit> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition,
                input: Unit,
                context: EngineInvocationContext,
            ): Result<Unit> =
                Result.failure(CancellationException("cancelled"))
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(TestDefinition(locator, "test"))),
            EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
            OutboundTargetPolicy { _, _, _ -> null },
        )
        assertFailsWith<CancellationException> {
            engine.invoke(locator, template, Unit, InvocationMode.FIRST_NOT_FAILING)
        }
    }

    @Test
    fun enginePluginFiltersCandidatesBeforeDispatch() = runTest {
        val config = mapOf("definition-filter" to kotlinx.serialization.json.JsonPrimitive(true))
        val definitions = listOf(
            TestDefinition(locator, "skip", config),
            TestDefinition(locator, "keep", config),
        )
        val called = mutableListOf<String>()
        val template = object : EngineTemplate<Unit, TextEngineResult> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition,
                input: Unit,
                context: EngineInvocationContext,
            ): Result<TextEngineResult> {
                called += (definition as TestDefinition).id
                return Result.success(TextEngineResult(definition.id))
            }
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(definitions),
            EngineDefinitionAuthorization { EngineAuthorizationDecision.Allowed },
            OutboundTargetPolicy { _, _, _ -> null },
            aviatorPluginRegistry {
                val pluginIdentity = "definition-filter"
                enginePlugin(object : EngineAviatorPlugin<TestPluginConfig> {
                    override val identity = pluginIdentity
                    override fun install(config: TestPluginConfig.() -> Unit): EngineAviatorPluginInstance {
                        val configuration = TestPluginConfig().apply(config)
                        return object : EngineAviatorPluginInstance {
                            override val identity = pluginIdentity
                            override val configurationBundle = configuration
                            override suspend fun accepts(
                                locator: ServiceLocator,
                                definition: ExecutableDefinition,
                            ): Boolean {
                                assertEquals(listOf(locator), configuration.serviceFilter)
                                return (definition as TestDefinition).id != "skip"
                            }
                        }
                    }
                }) {
                    serviceFilter += locator
                }
            },
        )

        assertEquals(
            TextEngineResult("keep"),
            engine.invoke(locator, template, Unit, InvocationMode.FIRST).single().result.getOrThrow(),
        )
        assertEquals(listOf("keep"), called)
    }

    @Test
    fun pluginRegistryFreezesInstalledCollectionsAtConstruction() = runTest {
        val plugin = object : EngineAviatorPluginInstance {
            override val identity = "frozen-engine-plugin"
            override val configurationBundle = TestPluginConfig()
            override suspend fun accepts(locator: ServiceLocator, definition: ExecutableDefinition) = true
        }
        val suppliedPlugins = mutableListOf(EnginePluginRegistration(plugin, PluginActivation.PASSIVE))
        val registry = DefaultAviatorPluginRegistry(engine = suppliedPlugins)

        suppliedPlugins.clear()

        assertEquals(listOf(plugin), registry.invocationPlugins(locator, emptyMap()).enginePlugins)
        assertEquals(listOf(plugin), registry.invocationPlugins(locator, emptyMap()).enginePlugins.toList())
    }

    @Test
    fun samePluginCanHaveDifferentDisjointLocatorScopes() = runTest {
        val otherLocator = ServiceLocator("mock-weather-other")
        fun instance(scope: ServiceLocator) = object : EngineAviatorPluginInstance {
            override val identity = "scoped-engine-plugin"
            override val configurationBundle = TestPluginConfig().apply { serviceFilter += scope }
            override suspend fun accepts(locator: ServiceLocator, definition: ExecutableDefinition) = true
        }
        val first = instance(locator)
        val second = instance(otherLocator)
        val registry = DefaultAviatorPluginRegistry(
            engine = listOf(
                EnginePluginRegistration(first, PluginActivation.PASSIVE),
                EnginePluginRegistration(second, PluginActivation.PASSIVE),
            ),
        )

        assertSame(first, registry.invocationPlugins(locator, emptyMap()).enginePlugins.single())
        assertSame(second, registry.invocationPlugins(otherLocator, emptyMap()).enginePlugins.single())
    }

    @Test
    fun engineAuthorizationDenialPreventsDelegation() = runTest {
        var dispatched = false
        val template = object : EngineTemplate<Unit, Unit> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition,
                input: Unit,
                context: EngineInvocationContext,
            ): Result<Unit> {
                dispatched = true
                return Result.success(Unit)
            }
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(TestDefinition(locator, "target"))),
            EngineDefinitionAuthorization { EngineAuthorizationDecision.Denied("denied by host") },
            OutboundTargetPolicy { _, _, _ -> null },
        )

        val outcome = engine.invoke(locator, template, Unit, InvocationMode.FIRST).single()

        assertIs<EngineAuthorizationException>(outcome.result.exceptionOrNull())
        assertFalse(dispatched)
    }

    @Test
    fun authorizationPluginsCanVetoUsingTypedRequestBody() = runTest {
        var dispatched = false
        val template = object : EngineTemplate<OpenApiClientInput<AuthorizationBody>, Unit> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition,
                input: OpenApiClientInput<AuthorizationBody>,
                context: EngineInvocationContext,
            ): Result<Unit> {
                dispatched = true
                return Result.success(Unit)
            }
        }.withAuthorizationPlugin(EngineAuthorizationPlugin<OpenApiClientInput<AuthorizationBody>> { context ->
            if (context.input.body?.account == "restricted") {
                EngineAuthorizationDecision.Denied("Restricted account")
            } else {
                EngineAuthorizationDecision.Allowed
            }
        })
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(TestDefinition(locator, "test"))),
            EngineDefinitionAuthorization.AllowAll,
            OutboundTargetPolicy { _, _, _ -> null },
        )
        val input = OpenApiClientInput(AuthorizationBody(account = "restricted"))

        val outcome = engine.invoke(locator, template, input, InvocationMode.FIRST).single()

        assertIs<EngineAuthorizationException>(outcome.result.exceptionOrNull())
        assertEquals("Restricted account", outcome.result.exceptionOrNull()?.message)
        assertFalse(dispatched)
    }

    @Test
    fun uniformPluginIsExposedThroughEveryRegistryRole() = runTest {
        val events = mutableListOf<String>()
        val scopeKey = PluginScopeKey<String>("uniform:test")
        val uniform = object : UniformAviatorPluginInstance {
            override val identity = "uniform:test"
            override val configurationBundle = object : AviatorPluginConfigScope {
                override var serviceFilter = mutableListOf(locator)
            }
            override val x = object : PluginStagedExecutor<
                cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext<Any, Any>,
                Any,
                Any,
            > {}
            override val key = scopeKey
            override suspend fun accepts(locator: ServiceLocator, definition: ExecutableDefinition) = true
            override suspend fun <I : Any, O : Any> execute(
                context: DelegationInvocationContext<I>,
                proceed: suspend () -> Result<O>,
            ): Result<O> {
                events += "before-delegation"
                val result = proceed()
                events += "after-delegation"
                return result
            }
            override suspend fun provide(
                locator: ServiceLocator,
                requested: Map<String, kotlinx.serialization.json.JsonElement>,
            ): PluginScopeSnapshot = object : PluginScopeSnapshot {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> get(key: PluginScopeKey<T>): T? =
                    if (key === scopeKey) "scope-value" as? T else null
            }
        }
        val registry = DefaultAviatorPluginRegistry(
            uniform = listOf(UniformPluginRegistration(uniform, PluginActivation.PASSIVE)),
        )

        val snapshot = registry.invocationPlugins(locator, emptyMap())
        assertSame(uniform, snapshot.enginePlugins.single())
        assertSame(uniform, snapshot.delegationPlugins.single())
        assertSame(uniform, snapshot.clientPlugins.single())
        assertEquals(
            "scope-value",
            snapshot.scopes.single().get(uniform.key),
        )
        val definition = TestDefinition(locator, "test")
        val template = object : EngineTemplate<Unit, TextEngineResult> {
            override val identity = "test"
            override suspend fun execute(
                definition: ExecutableDefinition,
                input: Unit,
                context: EngineInvocationContext,
            ): Result<TextEngineResult> {
                events += "template"
                return Result.success(TextEngineResult("ok"))
            }
        }
        val engine = AviatorEngine(
            InMemoryDefinitionCoordinator(listOf(definition)),
            EngineDefinitionAuthorization.AllowAll,
            OutboundTargetPolicy { _, _, _ -> null },
            registry,
        )

        assertEquals(
            TextEngineResult("ok"),
            engine.invoke(locator, template, Unit, InvocationMode.FIRST).single().result.getOrThrow(),
        )
        assertEquals(listOf("before-delegation", "template", "after-delegation"), events)
    }

    private class TestDefinition(
        override val locator: ServiceLocator,
        val id: String,
        override val pluginConfiguration: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ) : ExecutableDefinition {
        override val delegation = "test"
    }

    private class TestPluginConfig : AviatorPluginConfigScope {
        override var serviceFilter = mutableListOf<ServiceLocator>()
    }

    private data class TextEngineResult(val value: String)
}
