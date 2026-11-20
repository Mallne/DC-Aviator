# Migrate from Aviator 1.x to 2.x

This guide describes the intended migration from Aviator 1.x, using `v1.0.5` as the baseline.

## New features and why they matter

- **One entry point: `AviatorEngine`.** Use one shared engine instance per application. The host
  owns its lifecycle, so Android, Koin, Ktor, and Spring can each manage the singleton in their
  usual way. Routing calls through that instance gives them the same selection, authorization,
  and outbound-target policies.
- **Explicit selection and fallback.** Choose the first matching definition, the first that
  succeeds, or all matches. Each outcome identifies the definition that ran, so callers no longer
  need to implement their own selection loop or infer which provider succeeded.
- **Scoped plugin activation.** Hosts install plugins once and choose whether they run for every
  matching locator or only when an operation requests them. This separates trusted plugin
  installation from OpenAPI configuration.
- **Workflow execution through the same engine.** Optional Arazzo support composes OpenAPI
  operations in sequence and reuses the client pipeline rather than introducing a separate HTTP stack.
- **No automatic redirects.** Aviator disables redirects in Ktor and asks Fetch to reject them,
  keeping redirect hops from silently bypassing per-attempt controls.

## 1. Update dependencies and check transport behavior

- Upgrade all Aviator Maven modules and npm packages used by your app together. Keep each dependency
  graph on one matching Aviator version and rebuild consumers against it.
- If you build Aviator from source, update Gradle project references, composite-build substitutions,
  and CI task paths for the moved plugins. Their Maven coordinates, Kotlin imports, and public npm
  package names do not change because of this move.

  | 1.x Gradle project path | 2.x Gradle project path |
  | --- | --- |
  | `:plugins:http-auth` | `:plugins:client:http-auth` |
  | `:plugins:interception` | `:plugins:client:interception` |
  | `:plugins:synapse` | `:plugins:client:synapse` |
  | `:plugins:weaver` | `:plugins:client:weaver` |

- **Check redirects.** `KtorAviatorServiceConverter` derives a
  configured client with automatic redirects disabled and caps Ktor's `HttpSend` at one send per
  Ktor request. Fetch passes `redirect: "error"` and an `AbortSignal` to the supplied fetcher.
  Point OpenAPI at the final provider endpoint if it previously redirected. Do not re-enable
  automatic redirects or transport-managed retries; they can bypass authorization and budgeting
  for individual attempts.
- **Review Ktor client ownership.** `converter.httpClient` is now a configured client, not the
  instance passed into the constructor. Reuse the converter and close both the configured
  client and the original client at shutdown if you own them. Do not close a shared client
  after each invocation; the engine does not close either client for you.

Keep `x-dicentra-aviator: "1.0.0"` and the operation
`x-dicentra-aviator-serviceDelegateCall` entries in your OpenAPI document.
The Aviator OpenAPI extension version is **not** the library version; do not change it to `2.0.0`.

## 2. Move Kotlin calls to `AviatorEngine`

In this example, `api` is your `OpenApiDoc`, `converter` is a reused `KtorAviatorServiceConverter`,
and `Weather` is your `@Serializable` response model. The 1.x operation declares
`x-dicentra-aviator-serviceDelegateCall: "weather"`.

**1.x: materialize the document, then call the selected service directly.**

```kotlin
val locator = ServiceLocator("weather")
val service = converter.build(api) { install(JsonAdapter) }
    .filterIsInstance<KtorAviatorService>()
    .single { it.serviceLocator == locator }

val weather: Weather? = service.request<Weather>()
```

**2.x: give the converter the same OpenAPI document, then call the shared engine.**

Put the setup below in your application's composition root or singleton provider. Inject or pass
the resulting engine to callers rather than constructing it at each call site. Aviator does not
require a global initializer or a specific dependency injection framework.

```kotlin
import cloud.mallne.dicentra.aviator.client.ktor.invoke

val definitions = KtorEngineDefinitionConverter(converter).convert(api)
val engine = AviatorEngine(
    coordinator = InMemoryDefinitionCoordinator(definitions),
    authorization = authorization,
    outboundTargetPolicy = outboundPolicy,
    plugins = aviatorPluginRegistry {
        plugin(JsonAdapter)
    },
)

// In a suspend caller:
val weather: Weather? = engine.invoke<Weather>(ServiceLocator("weather"))
    .single().result.getOrThrow().result
```

The converter reads locators (including their flavour) from `x-dicentra-aviator-serviceDelegateCall`.
Supply a locator only when invoking the engine, not when converting the document.
The Ktor `invoke` import supplies the template from your response type. Omit `input` for calls
with no body, parameters, or options. `invoke` defaults to `FIRST`.
For a body, use `ktorEngineTemplateWithBody<Response, Body>()` and
`OpenApiClientInput(body = body)`. Move old `requestParams` into `OpenApiClientInput.parameters`
and request `options` into `OpenApiClientInput.options`; keep their declarations in OpenAPI.

Provide a host `EngineDefinitionAuthorization` (`authorization` above); the default denies all
definitions. Add `template.withAuthorization(...)` if the decision needs the typed request input.
You must also provide an `OutboundTargetPolicy` (`outboundPolicy` above): return `null` to permit
the declared server URLs or a reason to deny them. This validates declared URLs, not the resolved
connection addresses. Keep deployment egress controls where address-level restrictions are required.
Replace every direct `request` and `requestContextful` call: those paths bypass engine policies in
the current snapshot.

`invoke` returns outcomes in coordinator order, each containing the definition and a Kotlin
`Result` of the execution context. `FIRST` stops after the first matching candidate even on
failure; `FIRST_NOT_FAILING` returns attempted outcomes through the first success (or all failures);
`ALL` runs all matches. `.single()` in the example is for `FIRST`, not the other modes. No match
throws, and cancellation propagates. A Ktor execution with no successful HTTP response, or a
non-empty successful response that cannot be decoded, is a failure rather than an ordinary
`null` payload. A successful empty response can still have `context.result == null`.

## 3. Carry over your plugin behavior

Move existing request/response plugins into the engine registry with `clientPlugin(MyPlugin) { ... }`
unless you need the converter behavior noted below. The registry installs the plugin for you.

| In 1.x converter installation | In the 2.x engine registry |
| --- | --- |
| Plugin runs for every in-scope operation, even without an OpenAPI entry | Use `PluginActivation.PASSIVE` for ordinary plugins. Register the built-in adapters with `plugin(JsonAdapter)` or `plugin(XmlAdapter)`; they are always passive. |
| OpenAPI selects/configures the plugin by identity | Use `PluginActivation.DEFINITION` (the default) with an entry in the operation's `x-dicentra-aviator-pluginMaterialization` map. The value is passed to `requestReconfigure`. |
| OpenAPI reconfigures an installed plugin, but the plugin also runs when no entry exists | Keep that plugin in the `convert(api) { install(...) }` block if you need both behaviors unchanged. `PASSIVE` does not apply definition-specific reconfiguration. |

Keep `serviceFilter` restrictions when moving registrations. Multiple registrations of the same
identity must have disjoint locator scopes within a role; an empty filter matches every locator.
Avoid registering the same plugin in both places unless you intend the active engine registration
to replace the converter instance.

JSON and XML adapters are uniform plugins. `clientPlugin(JsonAdapter)` also works when you want
only its client role. In either case the request or response representation selects the adapter.

If you implement a custom adapter, `AviatorAdapterPluginInstance` no longer extends
`AviatorPluginInstance`. Implement both interfaces for a client adapter, or implement the adapter
interface alongside `UniformAviatorPluginInstance` for a uniform plugin. The shipped
`OpenTelemetryPlugin` is now uniform: use `plugin(OpenTelemetryPlugin) { ... }`
when you want engine and client instrumentation from the same installation, and keep your
configured `openTelemetry` provider.

## JavaScript/TypeScript and workflows

- The final JavaScript entry point must also route requests through `AviatorEngine`. The snapshot's
  `createFetchAviatorJsClient` façade still executes services directly, so it is not the final 2.x
  migration path. See [npm packaging](npm-distribution/README.md) for snapshot package details.
- Workflows are opt-in. Start with [orchestration core](orchestration/core/README.md) and the
  [Arazzo guide](orchestration/arazzo/README.md) if you want to compose operations. Existing
  OpenAPI calls do not need to be rewritten as workflows. Arazzo support is a sequential subset,
  and orchestration does not target Wasm.

Before shipping, rerun your provider tests with real endpoint URLs. Check redirects and error
responses, empty successes, plugin activation and serialization, and cancellation. Test denied
targets, denied definitions, no-match behavior, and any fallback mode you use. See
[Aviator build and test](README.md#build-and-test) for local test commands.
