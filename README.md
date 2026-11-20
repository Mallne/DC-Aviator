# Aviator

**OpenAPI clients and workflow execution for Kotlin Multiplatform.**

Aviator turns OpenAPI operations into executable client definitions and composes them into workflows.
Use the included `AviatorEngine` to resolve service targets, apply host policies, select plugins, and
dispatch work through Ktor, Fetch, Mock, or orchestration templates.

Normally, an application owns **one engine** and reuses its transports. You configure the engine;
you do not implement it. The engine interface is an escape hatch for highly specialized integrations.

## Contents

- [Migrating from Aviator 1.x](MIGRATION-1-TO-2.md)
- [Modules and dependencies](#modules-and-dependencies)
- [Call an OpenAPI operation](#call-an-openapi-operation)
- [Invocation modes and authorization](#invocation-modes-and-authorization)
- [Plugins](#plugins)
- [Workflows](#workflows)
- [JavaScript and TypeScript](#javascript-and-typescript)
- [Security and platform boundaries](#security-and-platform-boundaries)
- [Build and test](#build-and-test)

## Modules and dependencies

| Module | Purpose |
| --- | --- |
| `core` | Included engine, definition coordinator, typed templates, client pipeline, plugin contracts |
| `koas` | OpenAPI models and operation utilities |
| `clients:ktor` | HTTP execution using Ktor |
| `clients:fetch` | Fetch transport and JavaScript façade |
| `clients:mock` | Deterministic client execution without network calls |
| `orchestration:core` | Workflow delegation, execution policies, lifecycle hooks |
| `orchestration:arazzo` | Supported sequential Arazzo workflow execution |
| `plugins:*` | Optional adapters, authentication, interception, telemetry, Synapse, and Weaver |

For a Kotlin client, add the Ktor client and the adapters you need to `commonMain`. The serialization
compiler plugin must be enabled in your application. Use one matching Aviator version for all modules;
the engine API described here is the current source API, not a promise about older published packages.

```kotlin
repositories {
    maven("https://registry.mallne.cloud/repository/DiCentraArtefacts/")
    mavenCentral()
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation("cloud.mallne.dicentra.aviator:core:$aviatorVersion")
        implementation("cloud.mallne.dicentra.aviator.client:ktor:$aviatorVersion")
        implementation("cloud.mallne.dicentra.aviator.plugin.adapter:adapter-json:$aviatorVersion")
    }
}
```

Supply a Ktor `HttpClient` with an engine appropriate to your platform. Keep registry credentials
outside source control.

## Call an OpenAPI operation

Convert your OpenAPI document and give the resulting definitions to the included engine. You do
**not** need to select a route or supply a locator during conversion, or write a custom template
for normal HTTP calls.

Examples below use application-supplied definitions, policies, and models. Configure the engine
once at application startup:

```kotlin
val definitions = KtorEngineDefinitionConverter(converter).convert(api)
val engine = AviatorEngine(
    coordinator = InMemoryDefinitionCoordinator(definitions),
    authorization = authorization,
    outboundTargetPolicy = outboundPolicy,
    plugins = plugins,
)
```

For a bodyless request, import the Ktor `invoke` extension. It supplies the standard template
for your response type:

```kotlin
import cloud.mallne.dicentra.aviator.client.ktor.invoke

// In a suspend caller:
val outcomes = engine.invoke<Response>(locator)
```

`Response` is your serializable response model. Fetch offers the same extension in the
`cloud.mallne.dicentra.aviator.client.fetch` package. Use explicit templates when you need to
choose the transport at the call site or attach template-specific policy. For a request body, use
`ktorEngineTemplateWithBody<Response, Body>()` with `OpenApiClientInput(body)`.
The bodyless overload uses empty input when you omit it.
Pass path, query, and header values through `OpenApiClientInput.parameters`, and service options
through `OpenApiClientInput.options`. The OpenAPI operation still defines how parameters reach the wire.

Reuse converters and transports. Close the converter's configured `httpClient` and the original
client at application shutdown; the Aviator engine does not own or close them.

Whole-document conversion uses the `x-dicentra-aviator` version and reads each operation's locator
from `x-dicentra-aviator-serviceDelegateCall`. Operations without that locator are skipped. For a
workflow that deliberately binds a plain OpenAPI operation, the separate explicit operation
converter can use the workflow's locator instead.

## Invocation modes and authorization

A `ServiceLocator` identifies an operation or workflow target, including its flavour. Multiple
definitions may share that locator. The coordinator supplies candidates in stable order; the
selected template's identity determines which delegation can execute them.

| Mode | Behavior |
| --- | --- |
| `ALL` | Execute every matching candidate and retain each outcome |
| `FIRST` (default) | Execute only the first matching candidate, even if it fails |
| `FIRST_NOT_FAILING` | Try candidates in order until one succeeds |

Each outcome retains its definition and typed `Result<O>`. No match is an error; cancellation
propagates rather than becoming a fallback failure. Ktor templates classify unsuccessful HTTP
responses and failed decoding as failures.

Host authorization defaults to **deny all**. Supply `EngineDefinitionAuthorization` for shared
definition-level policy. For request-specific checks, attach `EngineAuthorization<I>` using
`template.withAuthorization(...)`; it receives the actual typed input. Additional typed authorization
plugins can veto through `withAuthorizationPlugin(...)`. Decisions are sealed `Allowed` / `Denied`
values. Coordinator selection and plugin filtering never grant authorization.

## Plugins

Install plugin code in the host. Definitions may activate and reconfigure installed plugins; they
cannot install arbitrary code.

| Role | Responsibility |
| --- | --- |
| Engine | Narrow coordinator candidates, without altering workflow steps |
| Delegation | Middleware around an authorized typed template invocation |
| Client | Request/response pipeline stages |
| Orchestration | Workflow, step, and attempt lifecycle hooks |
| Uniform | One configured instance providing engine, delegation, client, and scope-provider roles |

Use `plugin` to register a uniform plugin in all its roles. Use `enginePlugin`,
`delegationPlugin`, or `clientPlugin` for a specific role. Pass a trailing configuration block
when needed:

```kotlin
val plugins = aviatorPluginRegistry {
    plugin(JsonAdapter)
    enginePlugin(filterPlugin) { /* host configuration */ }
    plugin(sharedPlugin) { /* host configuration */ }
}
```

### Activation

- **`DEFINITION` (default for non-adapters):** active only when `ExecutableDefinition.pluginConfiguration` contains
  its identity. The associated `JsonElement` is passed to `requestReconfigure`.
- **`PASSIVE`:** active without a definition entry whenever its locator scope matches.
- **Body adapters:** `JsonAdapter` and `XmlAdapter` are uniform plugins and are always passive.
  Use `plugin(JsonAdapter)` to register all roles, or `clientPlugin(JsonAdapter)` for
  only the client role. Request/response representation selects the appropriate adapter.

An operation requests an installed plugin by identity:

```json
{
  "x-dicentra-aviator-pluginMaterialization": {
    "plugin-identity": {}
  }
}
```

This map is activation/reconfiguration data, not the plugin's install-time Kotlin configuration.
JSON and XML adapters handle body serialization; HTTP authentication, interception, telemetry,
Synapse forwarding, and Weaver transformations are separate optional modules.

### Different configurations for different services

`serviceFilter` is a list of matching `ServiceLocator`s. An empty list matches all locators.
You may install the same plugin identity more than once if its locator scopes do not overlap.
Each registration can cover several locators:

```kotlin
val plugins = aviatorPluginRegistry {
    enginePlugin(plugin) { serviceFilter = firstGroup.toMutableList() }
    enginePlugin(plugin) { serviceFilter = secondGroup.toMutableList() }
}
```

The groups must be disjoint. Each installation receives its own configuration.

Locator scopes choose configured instances. They are distinct from **invocation data scopes**:
a `PluginScopeProvider` publishes a typed snapshot, read through `EngineInvocationContext.scope(key)`.
Uniform plugins can publish these snapshots; orchestration uses this mechanism to receive its
invocation-local plugin registry. Keep request state out of shared installed instances.

## Workflows

Workflows use the same engine through `WorkflowDefinition`, `OrchestrationEngineTemplate`, and
`WorkflowInput`. Given an engine whose coordinator contains a configured workflow definition:

```kotlin
val outcomes = engine.invoke(
    locator = workflowLocator,
    template = OrchestrationEngineTemplate,
    input = WorkflowInput(inputs = inputsJson),
    mode = InvocationMode.FIRST,
)
```

The workflow definition owns its orchestrator and workflow identity. Arazzo's
`ArazzoEngineStepExecutor` resolves actions and invokes client operations through the same engine,
including operations without locator extensions. `resolveArazzoEngineAction` resolves
source-qualified operation identities; the host supplies typed input/result mapping and an exact
association to each executable definition. An `operationId` is not implicitly a locator.

Workflow execution retains its validation, authorization, budgets, lifecycle hooks, success criteria,
and source-response metadata. Engine invocation modes select definitions; they do not replace
workflow ordering or step failure semantics. Arazzo supports a limited sequential profile, not full
specification conformance or BPMN execution.

## JavaScript and TypeScript

The Fetch façade provides a smaller operation-ID API, separate from Kotlin's typed engine API.
Packages are available from `https://registry.mallne.cloud/repository/NodeRegistry/`; configure
the `@dicentra` scope and authenticate without committing tokens.

```sh
npm install @dicentra/aviator-client-fetch
```

```ts
import { createFetchAviatorJsClient } from '@dicentra/aviator-client-fetch';

const client = createFetchAviatorJsClient(openApiJson, globalThis.fetch);
const response = JSON.parse(await client.request(operationId, JSON.stringify(parameters)));
```

The façade returns raw response text, not a deserialized model. Optional plugins are separate
`@dicentra/aviator-plugin-*` packages and are not pulled into Fetch automatically. Keep all Aviator
packages on a matching version. See [npm packaging and verification](npm-distribution/README.md).

## Security and platform boundaries

- The host must authorize definitions and provide `OutboundTargetPolicy`. That policy sees declared
  OpenAPI server URLs—not DNS answers or connected-peer addresses. Use resolver/transport or deployment
  egress controls where address-level restrictions are required.
- Ktor disables automatic redirects; Fetch rejects redirects. Do not weaken these controls or add
  transport retries that bypass actual-attempt authorization and budgets.
- Apply request deadlines, workflow budgets, response-size limits, and secret-safe logging.
  Installed plugins are trusted code, not a security sandbox.
- JVM, JS Node, and selected Native paths have test coverage; Android/iOS device execution,
  live-provider behavior, and performance are not fully qualified. Orchestration has no Wasm target.
- Adoption by the AreaAssist production host remains deferred. The examples describe library
  capabilities, not completed application integration.

## Build and test

From the monorepo root, use the pinned Nix development shell (Java 25) and the Gradle wrapper:

```sh
nix develop --command ./aviator/gradlew --no-daemon -p aviator build
```

Focused engine/client/workflow verification:

```sh
nix develop --command ./aviator/gradlew --no-daemon -p aviator \
  :clients:mock:jvmTest :clients:ktor:jvmTest :clients:fetch:jsNodeTest \
  :orchestration:arazzo:jvmTest --max-workers=1
```

Android compilation additionally requires a locally configured Android SDK. npm package verification
runs with `npm run verify:fetch` in `aviator/npm-distribution`.

## License

[Apache License 2.0](LICENSE).

---

<p align="center">
  Built with ❤️ by Mallne under the DiCentra umbrella
</p>
