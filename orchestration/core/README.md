# Aviator Orchestration Core

Status: AV-5 portable contracts and AV-6 optional KMP registration exist. AV-7/AV-8 bindings and execution policies, the staged AV-21 plugin SPI, and the Arazzo runner seam are implemented. Lifecycle coverage now exercises JVM, JS Node and Linux x64 Native; iOS test sources and Android plugin main sources compile. Android/iOS runtime execution is not qualified. Wasm is excluded from these orchestration artifacts pending base Aviator compatibility. Verification scope and remaining limits are recorded in [PLAN.md](PLAN.md).

This module will define Aviator's optional, format-independent orchestration contracts. Applications can coordinate multiple Aviator operations without depending on Synapse or a particular workflow specification.

## Architecture

```text
AreaAssist / Synapse / another application
                  |
        Orchestrator contract
                  |
       Format-specific implementation
       (Arazzo first; others optional)
                  |
     Operation resolution and execution bindings
                  |
         Existing Aviator pipeline
```

- Orchestrators own workflow state, sequencing and format-specific control flow.
- Aviator clients execute individual operations with their existing plugins and adapters.
- Applications supply service definitions, credentials, operation bindings and policy.
- Synapse is an optional host, registry and gateway, not a dependency of orchestration.
- Workflow format and execution location are independent choices.

The shared contracts must not assume Ktor, PostgreSQL, Room, Koin, a server endpoint or a durable execution engine. Portable implementation belongs in `commonMain`.

## Responsibilities

- Workflow and run identities, inputs, outputs and execution outcomes.
- Orchestrator lifecycle and capability reporting.
- ServiceLocator-native resolution and invocation, including selection and aggregation across operations sharing a locator/flavour.
- Cancellation propagation and observable execution events; optional execution-policy extension points.
- Capability results with aggregate values and constituent outcomes/provenance, including available status, headers and body information. An in-process aggregate is not a single HTTP response.

OpenAPI operation IDs and source paths are format-adapter reference and provenance information, not mandatory core execution keys. Applications supply selection, aggregation and fallback policy. Aggregation is available embedded as well as hosted, without requiring Synapse.

Expression evaluation, branching, retries and compensation semantics belong to the workflow implementation, not a universal core workflow language. Durable execution, persistence and remote engine control are optional capabilities.

## Intended source layout

```text
src/commonMain/kotlin/cloud/mallne/dicentra/aviator/orchestration/
  model/       # Workflow/run identities, outcomes and events
  execution/   # Orchestrator and operation-binding contracts
  policy/      # Execution budgets and capability descriptions
src/commonTest/kotlin/cloud/mallne/dicentra/aviator/orchestration/
```

The sources cover execution bindings, workflow/run models, capabilities, explicit invocation policies, aggregate/constituent results, budget/authorization boundaries, events and contract tests. Host authorization remains mandatory; non-byte budgets are enforced only when an execution-budget policy plugin is explicitly installed. The AV-21 staged SPI is public; its supported stage semantics and platform qualification limits are documented below.

## Contract decisions (AV-5)

- `CapabilityInvocation` targets a ServiceLocator with an explicit `CapabilityPolicy`. Null constituent restrictions permit eligible matches; an empty set permits none. `accepts` checks targeting only, never authorization.
- `CapabilityPolicy` describes aggregation or ordered first-usable fallback, ordering, partial failures and empty-selection behavior. The application binding supplies the aggregation function and usable-output predicate. No universal weather/provider logic is built into core.
- `OperationBinding<I>` resolves a target/policy snapshot and returns `CapabilityResult` on invocation. It is not keyed by operation ID and does not introduce a second HTTP client.
- `CapabilityResult` separates aggregate outcome from ordered constituent outcomes/provenance. `ResponseSnapshot` retains real source response data separately from transformed output. HTTP metadata exists only on real constituent/gateway responses.
- `BodyValue` distinguishes unavailable, empty, JSON null, raw bytes and decoding failure. Workflow inputs/outputs remain opaque JSON strings for now; bindings can accept typed inputs and preserve non-JSON operation data without inventing a workflow schema language.
- `InvocationScope` carries per-run metadata and the budget value. `executionBudgetPolicyPlugin()` opts into atomic call/retry/deadline/concurrency/transition/nesting enforcement, counting actual constituent transport attempts and releasing active-call slots in `finally`. Without that plugin, optional budget values are descriptive only. Host authorization and cancellation behavior remain mandatory. Retained-byte counters, size estimators and aggregate/copy reservations are removed; core does not promise a heap-memory or payload-size limit.
- Prepared handles belong to their producing binding, contain no per-run authorization grant and cannot widen constituent restrictions. Concurrent reuse requires explicit support; otherwise resolve separately per invocation.
- Metadata is immutable by convention; bindings must snapshot caller-owned collections before storing per-invocation state. No mutable request context is shared between constituents or runs.
- `ExecutionEventSink` suspends for backpressure. Events contain correlation fields, not payloads or raw exceptions. Delivery failures/cancellation propagate; callbacks must not launch detached work. Concurrent constituent events may interleave; persisted snapshots/recovery are not implied.
- Caller-owned suspension is the baseline lifecycle. Persistence, lookup/resume, remote engines and a common workflow graph remain optional future work rather than promises of this contract.

See [PLAN.md](PLAN.md) for delivery phases, design decisions and acceptance criteria.

## First-class orchestration plugins (AV-21)

The portable staged SPI is in the orchestration core artifact, analogous to Aviator's `core.plugins` API. Concrete orchestration plugins are separate optional KMP artifacts under `plugins/`, following the existing Aviator plugin-module layout; core has no runtime dependency on them. A plugin definition creates configured instances; installation snapshots filter/stage sets, duplicate identities are rejected, and registry lookup is by stable identity:

```kotlin
val policy = defineOrchestrationPlugin("example.transform")
val installed = policy.install {
    stages += OrchestrationStage.MAP_INPUT
    executor = OrchestrationPluginExecutor { stage ->
        if (stage.stage == OrchestrationStage.MAP_INPUT) {
            stage.input = transform(stage.input)
        }
    }
}
val registry = OrchestrationPluginRegistry(listOf(installed))
```

Non-byte budget limits are an optional policy plugin, rather than implicit behavior of every scope:

```kotlin
import cloud.mallne.dicentra.aviator.plugin.orchestration.budget.executionBudgetPolicyPlugin
import cloud.mallne.dicentra.aviator.plugin.orchestration.otel.orchestrationOpenTelemetryPlugin

val registry = installOrchestrationPlugins(logger = appLogger) {
    install(executionBudgetPolicyPlugin())
    install("example.transform") { /* staged extension */ }
    install(orchestrationOpenTelemetryPlugin(openTelemetry))
}
val executor = ArazzoRunExecutor(stepExecutor, plugins = registry)
```

Plugin logging follows base Aviator's context API: configure the host logger once on the registry, and any plugin can call `context.log(tag) { ... }`. It does not receive or inject an `AviatorLogger` itself. The registry enriches each log with stage/run metadata and every installed plugin's `logMetadataProvider`; any installed plugin can suppress a stable tag through `silentLoggingTags`.

```kotlin
val registry = installOrchestrationPlugins(logger = appLogger) {
    install("example.transform") {
        stages += OrchestrationStage.MAP_INPUT
        logMetadataProvider = OrchestrationLogMetadataProvider { tag, context ->
            mapOf("plugin.log.tag" to tag, "workflow.id" to context.scope?.metadata?.workflow?.documentId.orEmpty())
        }
        executor = OrchestrationPluginExecutor { context ->
            context.log("example.transform.input") {
                debug("Mapping workflow input")
            }
            // Perform the stage transformation.
        }
    }
}
```

The registry's `AviatorLogger` backend is optional; `context.log` and core lifecycle logging are safe when no backend is configured and become no-ops. Lifecycle events are logged by orchestration core itself: normal stages at info, failures at warn, and cleanup at debug. They use the stable `orchestration.lifecycle` tag, include stage/run and installed-plugin metadata, and never log payloads or raw failure details. Installed plugins can suppress this tag through `silentLoggingTags`.

The optional implementations are packaged independently as `cloud.mallne.dicentra.aviator.plugin:orchestration-budget`, `orchestration-authorization`, and `orchestration-otel`; publication is still a separate release step. Core logging uses Aviator's host-supplied `AviatorLogger`; OTEL accepts the host's OpenTelemetry instance. Add only optional policy/tracing artifacts an application uses. Core tests cover logging directly; each optional plugin module owns its tests.

`orchestrationOpenTelemetryPlugin(openTelemetry)` creates internal spans for Arazzo runs and materialized binding executions, tags workflow/run identifiers and failure codes, and ends spans during cleanup. It does not install an SDK/exporter, record payloads or propagate trace context through HTTP; the application supplies its OpenTelemetry SDK and exporters.

At most one atomic invocation-policy plugin may be installed in a registry. It creates fresh shared accounting for each run; `forStep` views share that run's reservations. Required host authorization is checked independently and before behavior-changing attempt hooks.

`installOrchestrationPlugins(logger = appLogger) { install("id") { ... } }` is the convenience DSL. The logger backend is registry-scoped; plugin code logs via `context.log(tag) { ... }`, not by injecting its own logger. Empty stage/scope filters mean all supported stages/scopes; empty service filters mean all targets. `OrchestrationLifecycleScope` distinguishes run, step, binding and actual transport-attempt cleanup ownership. Before hooks run in install order, while result/terminal/unwind hooks run in reverse order. Instance `reconfigure` returns a new snapshot; in-flight executions keep using their existing instance. Registry execution namespaces mutex-protected state by plugin identity, and `InvocationScope.pluginState` shares it among children of one run, not across runs. Plugins must use atomic `get`/`put`/`update` for run-shared state. Event metadata keys are prefixed by plugin identity; logging metadata providers are merged in install order and sensitive-looking metadata keys are redacted. Existing Aviator operation plugins remain separate.

The current executable adapter support is deliberately specific: Arazzo run preparation/validation/scheduling/input mapping/completion; Arazzo binding resolution and exact-operation selection; materialized binding scheduling/input mapping, result/aggregate/output/terminal handling; and per-transport before/after attempt plus failure/cleanup hooks. Unsupported short-circuit uses fail explicitly. Only materialized binding scheduling may provide a cached `CapabilityResult` (after host authorization for the selected constituent); `HANDLE_RESULT` may short-circuit its current result and `COMPLETE` may replace the terminal result. Arazzo run-level short-circuits and transport-level result substitution are rejected because those stages do not own an authorized capability result. Transport attempt hooks run for each actual Aviator call including retry, after host authorization and without double charging. Binding short-circuit results carry no fabricated response provenance; result hooks cannot rewrite source constituent identity/response snapshots. Cancellation propagates and cleanup failures do not replace the original failure.

Materialized fan-out is deterministic and sequential in resolved constituent order: each selected constituent is authorized immediately before its actual attempt; outcomes remain attached to their source identity; `FIRST_USABLE` stops after the first usable result; `AGGREGATE` collects usable successes in order and applies the caller's partial-failure policy before aggregation. The common tests verify failure-then-success ownership and that the aggregate contains only the usable success. Parallel constituent dispatch is not currently provided.

### Declared multiplatform targets

Target availability is artifact-specific; the optional OTEL artifact follows the narrower platform set of the base Aviator OTEL plugin. This is a declaration matrix, not a claim that every target has passed CI qualification.

| Artifact | Declared targets |
| --- | --- |
| `orchestration:core`, `orchestration:arazzo` | JVM, JS, iOS x64/arm64/simulator arm64, Linux x64 |
| `orchestration-budget`, `orchestration-authorization` | JVM, Android, JS, iOS x64/arm64/simulator arm64, Linux x64 |
| `orchestration-otel` | JVM, Android, JS, iOS arm64/simulator arm64 |

Current focused lifecycle tests cover JVM, JS Node and Linux x64 Native for core/Arazzo; Linux x64 Native also covers budget, authorization and logging. iOS arm64 main and common-test sources compile across core, Arazzo and all four plugins; Android main sources compile for the four optional plugins, but Android host tests are not enabled. These are compile checks, not iOS/Android runtime tests. Wasm is excluded from the orchestration target contract until Aviator core's `@JsExport` interface declarations are compatible with Kotlin/Wasm. OTEL has no Linux/Wasm target.

Workflow engines supply format-specific stage adapters without turning core into an Arazzo language. Each adapter declares `OrchestrationEngineStageCapabilities`; composition owners call `registry.requireSupportedBy(...)` before use so registrations for an unimplemented stage fail at configuration time. `OrchestrationStageContext` is an extension context, not Aviator's HTTP execution context: only its documented `input`, `result`, `payload`, and `reject`/`stopWith` paths affect execution. `payload` is format/transport stage data and must be checked against the stage-specific contract. Do not put credentials, raw payloads or unsanitized exception text into plugin metadata.

### Base Aviator parity decisions

| Base Aviator | Orchestration API | Current qualification |
| --- | --- | --- |
| `AviatorPlugin.identity` / `install(config)` | `OrchestrationPlugin`, `defineOrchestrationPlugin`, `installOrchestrationPlugins` | Configured instances are addressable via registry lookup; duplicate identity is a configuration error. |
| `AviatorPluginInstance` configuration + executor | Immutable configuration bundle, executor, stage and target filters | Config/reconfiguration snapshots are tested; stage filters can be empty (all) or explicit. |
| `PluginStagedExecutor` before/after hooks | `OrchestrationStageContext` dispatched by the registry | Explicit lifecycle points, paired phases and ordering exist at Arazzo run/binding and materialized Aviator boundaries. Throw/rejection/cancellation, partial-run initialization and cleanup preservation have JVM/JS Node/Linux Native coverage; see [PLAN.md](PLAN.md). |
| Mutable shared execution context | Typed target/result/scope fields, scoped input/payload and plugin-namespaced `OrchestrationPluginState` | State is per run and mutex-protected; overlapping invocations on one materialized binding have a regression test for run isolation. It is not an HTTP request context. Format-specific payloads remain adapter-defined. |
| `logMetadataProvider` + `context.log(tag) { ... }` | Per-plugin log metadata provider, shared context logger, and `silentLoggingTags` | Host logger is configured once on the registry; metadata from all installed plugins is injected per log, sensitive-looking keys are redacted, explicit call metadata wins collisions, and any plugin can suppress a stable tag. |
| `requestReconfigure` | `reconfigure` returns a new instance | Existing runs keep their captured instance/configuration. |
| Operation pipeline plugin composition | Orchestration stages use Aviator call control and do not replace operation plugins | Ktor JVM integration verifies composition with installed Aviator `InterceptionPlugin`, transformed output and preserved source-response evidence. |

This is an implementation and verification record, not a tracker-state claim. Sequential fan-out ownership, concurrent-run isolation, operation-plugin composition and supported lifecycle failures have focused evidence. Constituent fan-out is sequential, not parallel. Android/iOS runtime execution is not covered; Wasm is excluded from the orchestration target contract.

Keep the execution lifecycle, cancellation propagation, explicit targeting and truthful provenance in core. Security-critical hosts must still enforce required authorization, including short-circuit paths. Existing Aviator operation-pipeline plugins remain independent and are not replaced; their real composition is covered by a focused JVM integration test. Complete multi-target qualification remains separate acceptance work; the byte-accounting removal does not establish it. See the [base Aviator parity requirements](PLAN.md#base-aviator-parity-requirements).

Payload limits, if needed, belong to optional plugins with explicit wire/serialized-size semantics. A transport read limit must run while reading, not after buffering the body. No generic heap estimator or reservation protocol is required from application aggregators.
