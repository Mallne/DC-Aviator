# Orchestration Core Implementation Plan

## Goal and boundaries

Introduce orchestration as a first-class optional Aviator feature. Arazzo is the first implementation, but core must not encode Arazzo's document structure or control-flow semantics.

Consumers include both client applications such as AreaAssist and servers such as Synapse. Neither application is a dependency of this module. Existing single-operation clients must remain usable without orchestration dependencies.

## Native execution identity: ServiceLocator

Aviator orchestration targets a `ServiceLocator` (locator plus flavour), not an OpenAPI `operationId`. Different operations and providers sharing that locator can contribute to an aggregated result. This is a native Aviator capability in embedded and hosted environments, not a Synapse-only feature or a deferred enhancement.

```text
Workflow step
  -> ServiceLocator + inputs + explicit selection/execution policy
  -> eligible constituent operations
  -> existing Aviator request pipelines
  -> capability result with constituent outcomes and provenance
```

Application bindings supply eligibility, selection, aggregation and fallback policy, including explicit constituent restrictions where required. Source document/revision, operation ID and operation path identify format-specific references and constituent provenance; they are not mandatory core invocation keys. The core contract must not become an exact-one-operation abstraction to accommodate Arazzo.

AV-5 delivered contracts and deterministic common tests for lifecycle, ServiceLocator bindings, explicit invocation policy, aggregate/constituent outcomes, provenance, events and budget/authorization interfaces. AV-6 has registered both optional KMP modules. AV-7/AV-8 bindings and policy enforcement are in-flight; source presence is not qualification. The current simplification removes retained-byte accounting from core. See historical verification below; unchecked later phases remain open.

## Phase 1 — Define the smallest useful contracts

- [x] Define workflow identity with format, format version, document identity and revision, separate from per-run identity.
- [x] Define execution inputs, terminal outcomes and structured failures, including partial completion without implying rollback.
- [x] Design an orchestrator contract for execution, cancellation and execution events. Keep caller-owned structured concurrency the baseline; persistent run lookup/resume is an optional capability, not an in-process requirement.
- [x] Define capability reporting for supported formats/features and execution modes. Provide pure unsupported-capability preflight; runtime implementations must call it before execution.
- [x] Separate format-specific reference resolution from ServiceLocator invocation. Application bindings determine eligible constituent operations and enforce explicit execution policy.
- [x] Define capability results containing aggregate values and constituent outcomes/provenance: provider/source revision, operation identity, status, multi-valued headers, content type, body availability and transport failures where available. Do not fabricate one HTTP status/header set for an in-process aggregate. Distinguish empty selection, empty bodies, JSON null, decode failures and absent results.
- [x] Specify selection, aggregation and fallback policy, including deterministic ordering, partial failures, empty results and usable-output criteria. Do not silently select the first match or assume every constituent must succeed.
- [x] Define namespaced execution metadata and snapshot/isolation ownership rules for run/step tracing without sharing mutable request contexts between steps.
- [x] Specify invocation-scope obligations and count actual constituent invocations/retries rather than workflow steps. Current AV-8 scope retains deadline, call, transition, retry, nesting and concurrency policies; retained-byte accounting is removed. AV-21 will extract installable policy implementations behind optional plugin hooks.

Contract names and signatures should be finalized through the first executable vertical slice, not a speculative public DSL.

## Phase 2 — Bind existing Aviator capabilities

- [ ] Bind execution natively by ServiceLocator, including flavour. Allow multiple matching operations to contribute under explicit policy; retain source document/revision and operation ID/path as constituent identity, adapter targeting information and provenance rather than primary core invocation keys.
- [ ] Support explicit constituent restrictions for adapters requiring exact-operation semantics without restricting every capability invocation to one constituent.
- [ ] Design bindings for already materialized Aviator services, avoiding mandatory discovery or network fetching of definitions.
- [ ] Decide whether client-specific bindings live in existing client modules or optional integration modules. Prevent any dependency cycle between core, clients and orchestration.
- [ ] Invoke the existing request pipeline with its auth, content adapters, Weaver and telemetry. Do not add a second HTTP stack.
- [ ] Address parameter identity by name and location; the current name-keyed `RequestParameters` cannot silently represent every same-name header/query combination.
- [ ] Specify the response representation exposed by a binding. Preserve the distinction between source HTTP data and plugin-transformed values; neither may silently impersonate a different contract.
- [ ] Support ordinary non-2xx and empty-body operation outcomes for workflow evaluation without depending on successful typed deserialization.
- [ ] Authorize each selected constituent and use an isolated request context for each invocation. Preserve per-constituent limits when one workflow step fans out.

## Foundational staged plugin architecture (AV-21)

Plugins are a first-class architecture requirement, not a later policy callback layer. Design [AV-21](https://dicentra.youtrack.cloud/issue/AV-21)'s staged contracts alongside AV-7/AV-8, before their public APIs stabilize. Its completed implementation integrates those binding/attempt boundaries before AV-11's scheduler, then application proofs, qualification and release. The existing completion prerequisites do not mean freezing a non-extensible baseline first. AV-9/AV-10/AV-12 format work can proceed in parallel according to its ticket dependencies.

- [x] Remove retained-byte budgets, size estimators, transport copy reservation callbacks and aggregate reservations from core and client integration code. Preserve source-response provenance and ordinary serialization.
- [ ] Design a staged orchestration plugin API with expressive parity to base Aviator's `AviatorPlugin`, `AviatorPluginInstance`, configuration scopes and `PluginStagedExecutor`. Document the parity matrix below; do not copy HTTP-specific execution contexts into workflow core.
- [ ] Expose typed before/after hooks across preparation/validation, reference resolution, constituent selection, step scheduling, input mapping, actual invocation attempts/retries, response/result handling, aggregation, output mapping and terminal handling. Engines expose format-specific stages through explicit adapters/capabilities rather than encoding Arazzo semantics in core.
- [ ] Give plugins controlled access to inspect and transform stage inputs/results, reject or short-circuit stages, and contribute execution behavior. Define how a supplied result flows through later stages and terminal hooks; hooks must not be limited to read-only event observation.
- [ ] Supply isolated run/step/attempt state and deterministic hook ordering, including failure unwind and concurrent fan-out ownership. Observe real retries/fan-out without double invocation or detached work.
- [ ] Preserve cancellation propagation and safe cleanup on success, failure, rejection and cancellation; test concurrent-run isolation and plugin failure semantics.
- [ ] Extract authorization and non-byte execution limits into explicitly installed policy plugins as a demonstration of the general extension system, not its entire purpose. Execution without installed plugins has no optional policy defaults. Mandatory host authorization, cancellation propagation, explicit targeting restrictions and truthful provenance must not be bypassed by hook transformations or short-circuiting. This extraction is not part of the immediate byte-removal change.
- [ ] If required, add optional wire/serialized payload limits with explicit semantics, not heap-memory claims. Transport limits must reject during body reading; result-size checks must state whether construction has already happened. Do not require aggregate size estimates or generic allocation reservations.

### Base Aviator parity requirements

| Base Aviator capability | Orchestration requirement |
| --- | --- |
| Plugin identity and `install { ... }` | Identified plugin definitions, configured instances and an installation DSL; define duplicate-identity behavior and lookup |
| Configuration scope and service filtering | Typed configuration and explicit workflow/step/locator applicability without changing reference or authorization semantics |
| Before/after execution-stage hooks | Full staged lifecycle above, with documented ordering, supported mutation and short-circuit semantics |
| Shared execution context | Typed scoped contexts with extension state and clear run/step/attempt ownership; no mutable cross-run leakage |
| Metadata provider | Metadata enrichment integrated with redaction; no implicit credential or payload logging |
| Reconfiguration | Document supported instance reconfiguration and snapshot isolation for in-flight runs; no arbitrary workflow-extension execution |
| Existing operation plugins | Compose unchanged operation pipelines with orchestration plugins; do not merge their contexts or create a second HTTP stack |

- [ ] Test a behavior-changing plugin (for example input/result transformation or an explicit cached result), not just observer/limiter examples. Verify its interaction with selection restrictions, host authorization, ordinary operation plugins and truthful source/derived-result provenance.
- [ ] Test installation/filtering, hook order, short-circuit flow, reconfiguration isolation, failures at each stage, cleanup and concurrent runs. AV-11 must use the same staged execution path, not a separate plugin-free scheduler path.

## Phase 3 — Validate both application environments

### AreaAssist: embedded execution

- [ ] Demonstrate local execution using application-provided service definitions and configured services, with no Synapse requirement.
- [ ] Demonstrate embedded aggregation of different operations sharing one ServiceLocator, excluding a different flavour and preserving constituent provenance.
- [ ] Use `ApiServiceUseCase.ServiceFor` as an integration reference for existing service/plugin resolution, not a dependency of the library.
- [ ] Preserve application-owned geographic provider eligibility, credentials, cache policy and domain/display transformations.
- [ ] Verify cancellation propagates through multi-step execution and provider fallback. Existing `QueryWeatherService` and `WeatherProviderFallback` are reference behaviors, not automatic migration targets.
- [ ] Demonstrate that a locally executed workflow can invoke Catalyst-backed operations without moving the workflow scheduler to Synapse.

### Synapse: hosted execution

- [ ] Supply registry-backed, caller-visible ServiceLocator bindings with authorization enforced for every selected constituent and nested workflow.
- [ ] Reuse Catalyst's binding, plugin and outbound protections where appropriate. Its aggregated JSON wire envelope is one capability-result representation, not the universal result contract or the origin of aggregation semantics.
- [ ] Keep database transactions outside provider network execution.
- [ ] Make workflow publication, execution endpoints and persistent storage separate application follow-ups.

## Phase 4 — Build, tests and publication

- [x] Register `:orchestration:core` as an optional KMP module (AV-6); keep dependencies directed toward Aviator core.
- [ ] Follow existing KMP target/toolchain conventions. Add only used dependencies; choose `api` versus `implementation` from actual public type exposure.
- [ ] Use Kotlin serialization for data models and keep common contracts platform-agnostic.
- [ ] Add deterministic fake-executor tests for outcomes, cancellation, configured deadlines/authorization/call policies, event ordering and isolated concurrent runs. Optional payload-limit tests belong to the relevant plugin, not mandatory core accounting.
- [ ] Test two different operation IDs sharing one locator aggregate under policy; a different flavour is excluded; authorization, failures, ordering, provenance and actual-call budgets are handled per constituent. Test explicit single-constituent restriction separately.
- [ ] Validate JVM and JS first, then supported Android/Native targets. Use the pinned root Nix devShell with Java 25 and `--no-daemon` for Gradle.
- [ ] Assess shared npm single-link packaging and unique Maven coordinates before publication; nested project name `core` must not collide with Aviator's existing core artifact.

## Contract decisions and deferred work

- Chosen: opaque JSON workflow input/output strings initially; typed binding inputs and `BodyValue` preserve operation data, including raw bytes and real HTTP metadata. No universal schema/codec layer is introduced.
- Chosen: suspending event delivery provides backpressure and propagates callback failure/cancellation. Events contain safe correlation fields; snapshots/persistence need separate optional infrastructure.
- Chosen: prepared handles are binding-owned, contain no run authorization and retain explicit restrictions. Concurrent reuse requires explicit support; otherwise resolve per invocation. Metadata collections must be snapshotted by storing implementations.
- Persistence/recovery, human approvals, compensation and remote engines: no baseline guarantees until implemented and tested.
- A common internal workflow representation: extract only after a second implementation demonstrates equivalent semantics.
- BPMN and n8n: evaluate separately; an external-engine connector need not be a document parser or run on every KMP target.

## AV-5 verification

Historical evidence only: the run below predates AV-6/AV-7/AV-8 and the removal of byte accounting. It does not verify the current working tree or AV-21. The former retained-byte tests have been removed or replaced; no current memory-limit guarantee follows from this run.

The isolated validation project uses repository orchestration sources plus the actual Aviator `ServiceLocator.kt` and `ServiceMethods.kt` files, without copies or replacement stubs. It uses the repository's Kotlin/serialization versions and treats compiler warnings as errors. Its build files are outside the repository at `/tmp/opencode/av5-locator-validation`.

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir /tmp/opencode/av5-locator-validation jvmTest
```

Latest run (2026-11-11): pinned Java 25.0.4.1, `BUILD SUCCESSFUL in 10s`; XML reports **22 tests, zero failures/errors/skips**. `git diff --check` also passed. Kotlin compilation was checked with warnings treated as errors.

That historical contract coverage included different operation IDs sharing a locator, flavour exclusion, exact constituent restrictions, revision/provenance, real gateway response metadata, serialization/body states, partial/empty/fallback policies, per-constituent authorization and isolated metadata, shared budgets (including now-removed retention accounting), cancellation propagation and suspending event backpressure. Binding and budget behavior was exercised by deterministic fakes, not real providers, concurrent limiter implementations, AreaAssist or Synapse integrations. JVM-only verification did not close later implementation or multiplatform qualification.

## Initial acceptance criteria

- A format-specific implementation executes a two-step workflow through Aviator bindings using only portable orchestration contracts.
- The same implementation works with an embedded application binding and a policy-enforcing server binding.
- Native ServiceLocator execution aggregates matching operations locally or hosted under explicit policy, retaining constituent provenance without requiring operation IDs as core invocation keys.
- Cancellation stops further scheduling; unauthorized or unsupported targets cannot execute.
- Aggregate values, constituent metadata and contract identity remain available for workflow evaluation without invented aggregate HTTP metadata.
- No reverse dependency from Aviator's existing core to orchestration, and no dependency on Synapse or AreaAssist.

## Byte-accounting simplification verification (2026-11-11)

Removed `maxRetainedBytes`/per-step retention limits, retain/release methods, size estimators, transport body-copy accounting callbacks and `aggregateReservation`. Ordinary aggregation, source snapshots, cancellation and current non-byte policies remain. AV-21's plugin SPI and policy extraction are still planned.

Run from the monorepo root under pinned Temurin 25.0.4.1:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator :orchestration:core:jvmTest :orchestration:arazzo:jvmTest :clients:ktor:jvmTest :clients:mock:jvmTest :clients:fetch:jsNodeTest -x :kotlinStorePackageLock --console=plain
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator :orchestration:core:jvmTest --console=plain
```

Both succeeded. XML reports show core JVM **38**, Arazzo JVM **14**, Ktor JVM **16**, mock JVM **2**, Fetch Node **12** tests: **82 total, zero failures/errors/skips**. The focused core rerun verifies the final shared-call-limit assertion. The initial combined command without the exclusion failed at `:kotlinStorePackageLock` because generated JS dependency resolution differs from the checked-in lock; no lock upgrade was performed. The successful retry excludes that lock verification only. Existing compiler/Gradle warnings remain. These are deterministic JVM/Node fixtures, not native qualification, live-provider acceptance, plugin SPI implementation or release evidence.
