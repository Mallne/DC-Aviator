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
- [x] Specify invocation-scope obligations and count actual constituent invocations/retries rather than workflow steps. Deadline, call, transition, retry, nesting and concurrency enforcement is now supplied by the explicitly installed AV-21 budget policy plugin; without it, budget values are descriptive only. Retained-byte accounting is removed.

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

Plugins are a first-class architecture requirement, not a later policy callback layer. Design [AV-21](https://dicentra.youtrack.cloud/issue/AV-21)'s staged contracts alongside AV-7/AV-8, before their public APIs stabilize. Its implementation integrates those binding/attempt boundaries before AV-11's scheduler, then application proofs, qualification and release. The existing completion prerequisites do not mean freezing a non-extensible baseline first. AV-9/AV-10/AV-12 format work can proceed in parallel according to its ticket dependencies.

### Implementation progress (2026-11-12; not a completion claim)

The optional plugin SPI is implemented in `orchestration/core` (analogous to Aviator's core plugin SPI) with installation/configuration, duplicate identity rejection and lookup, stage/scope/service filtering, immutable reconfiguration snapshots, per-run namespaced mutex-protected state, metadata namespacing/redaction, and lifecycle cleanup. Concrete budget and constituent-authorization implementations are separate optional artifacts at `plugins/orchestration/budget` and `plugins/orchestration/authorization`; core has no runtime or test dependency on these plugin artifacts. Arazzo preparation/validation/scheduling/input mapping, source-qualified resolution and narrowing-only selection are wired with explicit phase dispatch; `MaterializedOperationBinding` wires result/aggregation/output hooks; `InvocationCallControl` dispatches per-transport attempt/retry/failure/cleanup hooks, including from the Arazzo binding adapter. Authorization still runs before behavior-changing plugins; invalid stage replacements and unsupported short-circuit use fail before they can widen a target or fabricate source evidence. `OrchestrationEngineStageCapabilities` provides explicit stage-surface validation.

Deterministic tests cover install/lookup, duplicate IDs, stage/scope/target filters, reconfiguration, namespaced concurrent state, metadata redaction/precedence, input/result/aggregate/output/completion transformations, Arazzo stage rejection, exact-target protection, host authorization against cached output, real attempt/retry hooks, cancellation cleanup, and explicit opt-in budget enforcement (including no-plugin behavior). The budget policy creates independent per-run accounting and is shared across step views. The earlier in-core suites passed JVM and Node at 51 core + 17 Arazzo tests per target. After moving implementations to separate plugin modules and expanding stage coverage, the final targeted run passed **73 JVM + 73 Node tests**, zero failures/errors/skips: 46 core, 21 Arazzo, 5 budget-plugin, 1 authorization-plugin per target. Pinned Temurin 25.0.4.1; JS lock verification excluded because `kotlinStorePackageLock` reports the repository lock mismatch, without upgrading the lock.

**AV-21 implementation and public SPI review:** the core plugin API includes context logging modeled on base Aviator (`context.log(tag)`, per-plugin metadata providers and silent tags), and core emits automatic lifecycle logs through the host logger configured once on the registry. Only budget, authorization and OTEL remain optional artifacts. Host authorization remains an independent required dispatch check. The acceptance matrix and public API/onboarding examples are recorded below. Verification covers JVM, JS Node, Linux x64 Native, iOS arm64 test-source compilation, and Android plugin main-source compilation. Android/iOS runtime execution is not available in this Linux environment; Wasm was removed from the orchestration target contract with user approval because base Aviator core cannot compile its JS exports for Wasm. Do not claim those unsupported runtime/target paths as passes.

- [x] Remove retained-byte budgets, size estimators, transport copy reservation callbacks and aggregate reservations from core and client integration code. Preserve source-response provenance and ordinary serialization.
- [x] Design a staged orchestration plugin API with expressive parity to base Aviator's `AviatorPlugin`, `AviatorPluginInstance`, configuration scopes and `PluginStagedExecutor`. Document the parity matrix below; do not copy HTTP-specific execution contexts into workflow core.
- [x] Expose before/after hooks across preparation/validation, reference resolution, constituent selection, step scheduling, input mapping, actual invocation attempts/retries, response/result handling, aggregation, output mapping and terminal handling. Engines expose format-specific stages through adapters/capabilities rather than encoding Arazzo semantics in core.
- [x] Give plugins controlled access to inspect and transform stage inputs/results, reject or short-circuit stages, and contribute execution behavior. Define how a supplied result flows through later stages and terminal hooks; hooks are not read-only observers.
- [x] Supply isolated run/step/attempt state and deterministic hook ordering, including failure unwind and concurrent-run ownership. Observe retries/fan-out without double invocation or detached work; constituent dispatch remains sequential.
- [x] Preserve cancellation propagation and safe cleanup on success, failure, rejection and cancellation; test concurrent-run isolation and plugin failure semantics.
- [x] Supply core context-based logging with per-plugin metadata providers, suppression tags and automatic lifecycle logs. OpenTelemetry remains an optional convenience. Protect credentials/payloads and avoid raw exception text in automatic logs.
- [x] Extract non-byte execution limits into the explicitly installed `executionBudgetPolicyPlugin()` as a demonstration of the general extension system, not its entire purpose; expose optional constituent authorization as a staged extension. Execution without installed plugins has no optional policy defaults. Mandatory host authorization remains independent of plugins; cancellation propagation, explicit targeting restrictions and truthful provenance cannot be bypassed by hook transformations or short-circuiting. This extraction is separate from byte-accounting removal.
- [x] Keep wire/serialized payload limits optional and out of the AV-21 core contract. This implementation does not claim payload-size or heap-memory limits and requires no generic allocation reservations.

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

- [x] Test behavior-changing plugins and an explicit cached result. Verify selection restrictions, mandatory host authorization, ordinary operation-plugin composition and truthful source/derived-result provenance (see ownership/composition verification below).
- [x] Test installation/filtering, hook order, short-circuit flow, reconfiguration isolation, lifecycle-stage throws/rejection, cleanup and concurrent runs on qualified test targets. Arazzo uses the staged path. AV-11 must use the same staged execution path when that separate scheduler is implemented; AV-21 does not claim AV-11 delivery.

Post-extraction verification (2026-11-12), from the monorepo root under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:arazzo:jvmTest \
  :plugins:orchestration:budget:jvmTest :plugins:orchestration:authorization:jvmTest \
  :orchestration:core:jsNodeTest :orchestration:arazzo:jsNodeTest \
  :plugins:orchestration:budget:jsNodeTest :plugins:orchestration:authorization:jsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL`; XML reports: **73 JVM + 73 Node tests**, zero failures/errors/skips (core 46, Arazzo 21, budget plugin 5, authorization plugin 1 for each target). Core has no plugin-artifact runtime or test dependency; Arazzo uses only a test dependency for budget-policy integration. The lock task was excluded without modifying/upgrading the lock.

Follow-up ownership/composition verification (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest :clients:ktor:jvmTest \
  -x :kotlinStorePackageLock --max-workers=1 --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL`; XML reports: **48 core JVM + 48 core Node + 17 Ktor JVM tests**, zero failures/errors/skips. `fanOutKeepsEachAttemptOutcomeOwnedByItsConstituent` verifies per-constituent attempt/failure/result ownership and aggregate membership. With the deterministic first-failure/second-success aggregate assertion, it documents sequential resolved-order behavior; `FIRST_USABLE` short-circuits and `AGGREGATE` applies the caller's partial-failure policy before aggregation. Parallel constituent dispatch is not implemented. `orchestrationComposesWithInstalledAviatorInterceptionPlugin` exercises the real installed operation plugin over Ktor and verifies the transformed output remains distinct from the actual source response. The Ktor tests explicitly install the optional budget policy where budget enforcement is asserted. The broader failure/partial-initialization matrix and public API review remain open. Concurrent-run verification is added below; it exercises two runs over one binding, not parallel constituent dispatch. Native qualification and live-provider acceptance are outside this focused verification.

Additional binding failure-unwind verification (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL`; **49 core JVM + 49 core Node tests**, zero failures/errors/skips. `failuresAtResultAggregateAndOutputHooksUnwindAndCleanup` injects executor exceptions at `HANDLE_RESULT`, `AGGREGATE`, `MAP_OUTPUT` and `COMPLETE`; every case verifies one constituent invocation, propagation of the original failure, and execution of both FAILURE and CLEANUP hooks. This adds representative binding lifecycle coverage, not a complete adapter/stage partial-initialization matrix.

Concurrent-run isolation verification (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL`; **50 core JVM + 50 core Node tests**, zero failures/errors/skips. `concurrentRunsOnOneBindingKeepPluginStateAndResultsIsolated` overlaps two runs through the same prepared materialized binding, releases their constituent calls in reverse order, and asserts each completion hook observes only its own run state. It does not claim parallel fan-out dispatch; the binding's constituent traversal is sequential.

Optional observability plugin verification (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :plugins:orchestration:logging:jvmTest :plugins:orchestration:logging:jsNodeTest \
  :plugins:orchestration:otel:jvmTest :plugins:orchestration:otel:jsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL`; **1 logging JVM + 1 logging Node + 1 OTEL JVM + 1 OTEL Node test**, zero failures/errors/skips. Logging assertions cover stage/correlation fields and ensure input payload and raw failure messages are absent. OTEL assertions cover its run/binding stage and lifecycle registration; both optional modules compile and test on JVM/JS. This is initial plugin evidence, not full supported-target or failure/partial-initialization qualification.

Transport attempt failure-matrix extension (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest \
  :orchestration:arazzo:jvmTest :orchestration:arazzo:jsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

`BUILD SUCCESSFUL`; **51 core JVM + 51 core Node + 24 Arazzo JVM + 24 Arazzo Node tests**, zero failures/errors/skips. New `thrownTransportHooksNotifyFailureReleaseAdmissionAndRunAttemptCleanup` coverage injects exceptions at `BEFORE_ATTEMPT` and `AFTER_ATTEMPT`, verifies the attempt failure and cleanup hooks still execute, asserts no transport call occurs before-attempt failure, and checks admission release occurs exactly once only after successful admission. Existing Arazzo stage throws and binding stage throws/cancellation cases also passed in this run. This does not yet constitute a complete partial-initialization matrix or public API review.

Partial-run initialization and binding-resolution failure extension (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:arazzo:jvmTest :orchestration:arazzo:jsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

`BUILD SUCCESSFUL`; **26 Arazzo JVM + 26 Arazzo Node tests**, zero failures/errors/skips. `runStartedSinkFailureStillNotifiesFailureAndCleansUpPartialRun` injects an event-sink exception on `RUN_STARTED`, before the first plugin preparation hook; it verifies the exception propagates, no step dispatch occurs, and run `FAILURE` and `CLEANUP` hooks each execute once. `thrownResolutionAndSelectionHooksBecomeSanitizedRunFailuresWithCleanup` injects exceptions at both Arazzo binding `RESOLVE` and `SELECT` stages via the real run/binding adapter composition; each becomes a sanitized `operation_failed` result, skips operation dispatch, and runs failure and cleanup hooks. Public SPI/onboarding review and the remaining stage/target matrix are still open.
`BUILD SUCCESSFUL`; **27 Arazzo JVM + 27 Arazzo Node tests**, zero failures/errors/skips (refreshed after adding success/rejection cleanup coverage). `runCleanupOccursOnceOnSuccessAndPluginRejection` additionally proves success invokes cleanup once without failure notification, and preparation rejection invokes both failure and cleanup once.

Linux Native target verification (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:linuxX64Test :orchestration:arazzo:linuxX64Test \
  :plugins:orchestration:budget:linuxX64Test \
  :plugins:orchestration:authorization:linuxX64Test \
  :plugins:orchestration:logging:linuxX64Test \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

`BUILD SUCCESSFUL`; XML results from the cross-target run show **52 core + 27 Arazzo + 5 budget + 1 authorization + 1 logging tests**, zero failures/errors/skips. The subsequent core reconfiguration/filter regression also passed on Linux x64, bringing the focused core total to 53 there. This executes lifecycle regressions on Linux Native for core/Arazzo and verifies the listed optional plugins' Linux target tests. It does not cover OTEL on Linux (that plugin does not declare that target), Wasm, iOS runtime or Android runtime.

Wasm target qualification attempt (2026-11-12):

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:wasmJsNodeTest :orchestration:arazzo:wasmJsNodeTest \
  :plugins:orchestration:budget:wasmJsNodeTest \
  :plugins:orchestration:authorization:wasmJsNodeTest \
  :plugins:orchestration:logging:wasmJsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

**Disposition (user-approved 2026-11-12): Wasm is excluded from the orchestration target contract**, rather than treated as qualified or silently waived. The attempted build failed before orchestration sources, in dependency task `:core:compileKotlinWasmJs`: existing Aviator core JS exports (`@JsExport` on `AviatorPlugin`, `AviatorPluginActivationScope`, `AviatorPluginConfigScope`, and `AviatorPluginInstance`) are reported as applicable only to functions. No Wasm orchestration test ran, and this work does not alter base Aviator's public JS export ABI. Wasm targets were removed from orchestration core, Arazzo, budget, authorization, and logging; OTEL never declared Wasm. The attempt generated an untracked `kotlin-js-store/wasm/package-lock.json`; that generated artifact was removed without modifying tracked lockfiles.

iOS/Android cross-target compilation (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:compileKotlinIosArm64 :orchestration:arazzo:compileKotlinIosArm64 \
  :plugins:orchestration:budget:compileKotlinIosArm64 \
  :plugins:orchestration:authorization:compileKotlinIosArm64 \
  :plugins:orchestration:logging:compileKotlinIosArm64 \
  :plugins:orchestration:otel:compileKotlinIosArm64 \
  :plugins:orchestration:budget:compileAndroidMain \
  :plugins:orchestration:authorization:compileAndroidMain \
  :plugins:orchestration:logging:compileAndroidMain \
  :plugins:orchestration:otel:compileAndroidMain \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

`BUILD SUCCESSFUL`; iOS arm64 main sources compiled for core/Arazzo and all plugins, and Android main sources compiled for all four optional plugins. A separate `compileTestKotlinIosArm64` run also succeeded for core, Arazzo and all four optional plugins. This compiles iOS test sources but does not execute iOS tests; Android host tests are not enabled by these modules. Android runtime and iOS runtime/simulator execution remain unqualified.

Public SPI reconfiguration/filter regression (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest \
  :orchestration:core:linuxX64Test \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

`BUILD SUCCESSFUL`; **53 core JVM + 53 core Node + 53 core Linux x64 tests**, zero failures/errors/skips. `reconfigurationKeepsInstalledRegistrySnapshotAndUpdatesTargetFilter` verifies that a registry retains its original installed plugin after reconfiguration and that the new instance applies the new locator filter. The test uses actual valid `ServiceLocator` flavours (`gather`/`update`) so target identity is distinct. This adds direct evidence for API snapshot/filter semantics.

Post-Wasm-disposition verification (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest :orchestration:core:linuxX64Test \
  :orchestration:arazzo:jvmTest :orchestration:arazzo:jsNodeTest :orchestration:arazzo:linuxX64Test \
  :plugins:orchestration:budget:linuxX64Test \
  :plugins:orchestration:authorization:linuxX64Test \
  :plugins:orchestration:logging:linuxX64Test \
  :orchestration:core:compileTestKotlinIosArm64 \
  :orchestration:arazzo:compileTestKotlinIosArm64 \
  :plugins:orchestration:budget:compileTestKotlinIosArm64 \
  :plugins:orchestration:authorization:compileTestKotlinIosArm64 \
  :plugins:orchestration:logging:compileTestKotlinIosArm64 \
  :plugins:orchestration:otel:compileTestKotlinIosArm64 \
  :plugins:orchestration:budget:compileAndroidMain \
  :plugins:orchestration:authorization:compileAndroidMain \
  :plugins:orchestration:logging:compileAndroidMain \
  :plugins:orchestration:otel:compileAndroidMain \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

`BUILD SUCCESSFUL`. XML counts: **53 core JVM + 53 core Node + 53 core Linux x64; 27 Arazzo JVM + 27 Arazzo Node + 27 Arazzo Linux x64; 5 budget + 1 authorization + 1 logging Linux x64 tests**, zero failures/errors/skips. iOS arm64 test sources compiled for core/Arazzo/all four plugins; Android main sources compiled for all four plugins. `git diff --check` is clean. Gradle task listings for the orchestration modules returned no Wasm tasks after the approved target removal. This does not claim iOS/Android runtime execution; neither `adb` nor Apple `xcrun`/`xcodebuild` is installed in this Linux environment. Android host tests are not enabled in these plugin modules.

Core logger parity correction (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest :orchestration:core:linuxX64Test \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

`BUILD SUCCESSFUL`; **55 core JVM + 55 core Node + 55 core Linux x64 tests**, zero failures/errors/skips. `pluginLogUsesHostLoggerAndAggregatesMetadataWithSuppression` verifies that plugins log through `context.log(tag)`, using the `AviatorLogger` injected once at registry construction; registry context aggregates per-plugin metadata, applies call metadata precedence, redacts sensitive keys and suppresses tags silenced by any plugin. `lifecycleLoggingIsProvidedByCoreAndHonorsSilentTagSuppression` verifies built-in lifecycle info/warn/debug behavior, failure-code-only metadata and suppression through `silentLoggingTags`. Follow-up parity verification also passed **27 Arazzo JVM + 27 Node + 27 Linux x64 tests**; iOS arm64 test-source compilation passed. Automatic lifecycle logging is implemented directly by the core registry, not through a separate logging plugin artifact. `git diff --check` is clean. AV-21's Fixed state is unchanged. These are source/target verification results, not iOS runtime qualification.

Unwind-hook failure preservation extension (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --console=plain
```

`BUILD SUCCESSFUL`; **52 core JVM + 52 core Node tests**, zero failures/errors/skips at that checkpoint. `failureAndCleanupHookExceptionsAreSuppressedWithoutReplacingPrimaryFailure` injects a primary aggregation-stage exception and further exceptions from both `FAILURE` and `CLEANUP`; it verifies both unwind phases run, the original exception remains primary, and both hook errors are retained as suppressed exceptions. The later post-disposition verification below includes the completed regression set.

Arazzo adapter exception-unwind verification (2026-11-12), under pinned Temurin 25:

```sh
nix develop --command ./aviator/gradlew --no-daemon --project-dir aviator \
  :orchestration:arazzo:jvmTest :orchestration:arazzo:jsNodeTest \
  :orchestration:core:jvmTest :orchestration:core:jsNodeTest \
  -x :kotlinStorePackageLock --max-workers=1 --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL`; **24 Arazzo JVM + 24 Arazzo Node + 50 core JVM + 50 core Node tests** at that checkpoint, zero failures/errors/skips. `thrownAdapterStageFailuresRunFailureAndCleanupWithoutDispatch` injects exceptions during Arazzo PREPARE, VALIDATE, SCHEDULE and MAP_INPUT; each verifies no step dispatch, original exception propagation, sanitized failure-hook notification and cleanup. This test exposed and fixed a missing ordinary-exception FAILURE dispatch in `ArazzoRunExecutor`; notification is bounded/non-cancellable and notification errors are suppressed onto the original exception. The later binding-resolution, partial-run, attempt, unwind-hook and cross-target extensions complete the additional AV-21 evidence recorded above.

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
