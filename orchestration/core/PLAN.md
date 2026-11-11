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

AV-5 contracts and deterministic common tests now cover lifecycle, ServiceLocator bindings, explicit invocation policy, aggregate/constituent outcomes, provenance, events and budget/authorization interfaces. Phase 1 completion is contract-level, not implementation of production binding or enforcement. No repository Gradle project is registered. See verification below for the isolated JVM scope; unchecked later phases remain open.

## Phase 1 — Define the smallest useful contracts

- [x] Define workflow identity with format, format version, document identity and revision, separate from per-run identity.
- [x] Define execution inputs, terminal outcomes and structured failures, including partial completion without implying rollback.
- [x] Design an orchestrator contract for execution, cancellation and execution events. Keep caller-owned structured concurrency the baseline; persistent run lookup/resume is an optional capability, not an in-process requirement.
- [x] Define capability reporting for supported formats/features and execution modes. Provide pure unsupported-capability preflight; runtime implementations must call it before execution.
- [x] Separate format-specific reference resolution from ServiceLocator invocation. Application bindings determine eligible constituent operations and enforce explicit execution policy.
- [x] Define capability results containing aggregate values and constituent outcomes/provenance: provider/source revision, operation identity, status, multi-valued headers, content type, body availability and transport failures where available. Do not fabricate one HTTP status/header set for an in-process aggregate. Distinguish empty selection, empty bodies, JSON null, decode failures and absent results.
- [x] Specify selection, aggregation and fallback policy, including deterministic ordering, partial failures, empty results and usable-output criteria. Do not silently select the first match or assume every constituent must succeed.
- [x] Define namespaced execution metadata and snapshot/isolation ownership rules for run/step tracing without sharing mutable request contexts between steps.
- [x] Specify execution budgets and invocation-scope obligations: deadline, calls, transitions, retries, nesting, concurrency and retained bytes. Count actual constituent invocations/retries and aggregate plus constituent retention, not only workflow steps. Production enforcement remains AV-8.

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

- [ ] Add `:orchestration:core` to Aviator settings only when executable sources and a coherent dependency direction exist.
- [ ] Follow existing KMP target/toolchain conventions. Add only used dependencies; choose `api` versus `implementation` from actual public type exposure.
- [ ] Use Kotlin serialization for data models and keep common contracts platform-agnostic.
- [ ] Add deterministic fake-executor tests for outcomes, cancellation, deadlines, authorization rejection, call budgets, result limits, event ordering and isolated concurrent runs.
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

The isolated validation project uses repository orchestration sources plus the actual Aviator `ServiceLocator.kt` and `ServiceMethods.kt` files, without copies or replacement stubs. It uses the repository's Kotlin/serialization versions and treats compiler warnings as errors. Its build files are outside the repository at `/tmp/opencode/av5-locator-validation`.

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir /tmp/opencode/av5-locator-validation jvmTest
```

Latest run (2026-11-11): pinned Java 25.0.4.1, `BUILD SUCCESSFUL in 10s`; XML reports **22 tests, zero failures/errors/skips**. `git diff --check` also passed. Kotlin compilation was checked with warnings treated as errors.

Contract coverage includes different operation IDs sharing a locator, flavour exclusion, exact constituent restrictions, revision/provenance, real gateway response metadata, serialization/body states, partial/empty/fallback policies, per-constituent authorization and isolated metadata, shared call/retry/concurrency/retention/transition/nesting/deadline budgets, cancellation propagation and suspending event backpressure. Binding and budget behavior is exercised by deterministic fakes, not real providers, concurrent limiter implementations, AreaAssist or Synapse integrations. JVM-only verification does not close AV-6 module registration, AV-7 bindings, AV-8 production enforcement or later multiplatform qualification.

## Initial acceptance criteria

- A format-specific implementation executes a two-step workflow through Aviator bindings using only portable orchestration contracts.
- The same implementation works with an embedded application binding and a policy-enforcing server binding.
- Native ServiceLocator execution aggregates matching operations locally or hosted under explicit policy, retaining constituent provenance without requiring operation IDs as core invocation keys.
- Cancellation stops further scheduling; unauthorized or unsupported targets cannot execute.
- Aggregate values, constituent metadata and contract identity remain available for workflow evaluation without invented aggregate HTTP metadata.
- No reverse dependency from Aviator's existing core to orchestration, and no dependency on Synapse or AreaAssist.
