# Aviator Orchestration Core

Status: AV-5 portable contracts and AV-6 optional KMP registration exist. AV-7/AV-8 binding and execution-policy implementations are in progress, alongside a narrow Arazzo runner seam; full runtime qualification remains open. Verification scope is recorded in [PLAN.md](PLAN.md).

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

The sources cover execution bindings, workflow/run models, capabilities, explicit invocation policies, aggregate/constituent results, budget/authorization boundaries, events and contract tests. Public signatures remain provisional until the first runtime vertical slice. Existing authorization and non-byte budget implementations remain for now; their extraction into optional orchestration plugins is tracked by [AV-21](https://dicentra.youtrack.cloud/issue/AV-21), not implemented by the byte-accounting removal.

## Contract decisions (AV-5)

- `CapabilityInvocation` targets a ServiceLocator with an explicit `CapabilityPolicy`. Null constituent restrictions permit eligible matches; an empty set permits none. `accepts` checks targeting only, never authorization.
- `CapabilityPolicy` describes aggregation or ordered first-usable fallback, ordering, partial failures and empty-selection behavior. The application binding supplies the aggregation function and usable-output predicate. No universal weather/provider logic is built into core.
- `OperationBinding<I>` resolves a target/policy snapshot and returns `CapabilityResult` on invocation. It is not keyed by operation ID and does not introduce a second HTTP client.
- `CapabilityResult` separates aggregate outcome from ordered constituent outcomes/provenance. `ResponseSnapshot` retains real source response data separately from transformed output. HTTP metadata exists only on real constituent/gateway responses.
- `BodyValue` distinguishes unavailable, empty, JSON null, raw bytes and decoding failure. Workflow inputs/outputs remain opaque JSON strings for now; bindings can accept typed inputs and preserve non-JSON operation data without inventing a workflow schema language.
- `InvocationScope` currently carries per-run metadata and a shared budget/authorization boundary. Configured limits count actual constituent calls/retries and concurrency, check deadlines and release active-call slots in `finally`. The contract-test scope is intentionally not a production concurrent limiter. Retained-byte counters, size estimators and aggregate/copy reservations are removed; core does not promise a heap-memory or payload-size limit.
- Prepared handles belong to their producing binding, contain no per-run authorization grant and cannot widen constituent restrictions. Concurrent reuse requires explicit support; otherwise resolve separately per invocation.
- Metadata is immutable by convention; bindings must snapshot caller-owned collections before storing per-invocation state. No mutable request context is shared between constituents or runs.
- `ExecutionEventSink` suspends for backpressure. Events contain correlation fields, not payloads or raw exceptions. Delivery failures/cancellation propagate; callbacks must not launch detached work. Concurrent constituent events may interleave; persisted snapshots/recovery are not implied.
- Caller-owned suspension is the baseline lifecycle. Persistence, lookup/resume, remote engines and a common workflow graph remain optional future work rather than promises of this contract.

See [PLAN.md](PLAN.md) for delivery phases, design decisions and acceptance criteria.

## First-class orchestration plugins (AV-21)

Plugins must be as expressive as base Aviator's staged plugin system, not merely policy callbacks or event observers. AV-21's contracts must be designed alongside AV-7/AV-8 before API stabilization. Planned capabilities include an `install { ... }` configuration model, plugin identity/lookup, applicability filtering, scoped extension state, metadata enrichment and documented reconfiguration semantics.

Typed before/after stages cover workflow preparation/validation, resolution, selection, scheduling, input mapping, actual attempts/retries, response/result handling, aggregation, output mapping and completion/failure/cleanup. Plugins can inspect and transform defined stage data and reject or short-circuit execution with explicit semantics. Workflow engines supply format-specific stage adapters without turning core into an Arazzo language. Deterministic ordering, concurrent-state ownership, cancellation and cleanup are part of the contract.

Keep the execution lifecycle, cancellation propagation, explicit targeting and truthful provenance in core. Optional policies use this general plugin system; security-critical hosts must still enforce required authorization, including short-circuit paths. Existing Aviator operation-pipeline plugins remain independent and composable rather than being replaced. The plugin architecture and policy extraction are planned, not implemented by the byte-accounting removal. See the [base Aviator parity requirements](PLAN.md#base-aviator-parity-requirements).

Payload limits, if needed, belong to optional plugins with explicit wire/serialized-size semantics. A transport read limit must run while reading, not after buffering the body. No generic heap estimator or reservation protocol is required from application aggregators.
