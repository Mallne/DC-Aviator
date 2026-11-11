# Arazzo Orchestrator Implementation Plan

## Goal

Deliver the first implementation of Aviator's format-independent orchestrator contract. Execute Arazzo workflows through existing Aviator operations in client or server applications, without coupling the runtime to Synapse.

Depends on the contracts and execution bindings described in [Orchestration Core's plan](../core/PLAN.md). All items below are unimplemented; no runtime, build registration or conformance claim is included in this scaffold.

## Adapter boundary: Arazzo references and Aviator capabilities

Aviator core executes ServiceLocator capabilities and can aggregate different operations sharing a locator/flavour. Arazzo's source-qualified `operationId` and `operationPath` identify declared OpenAPI operations; they are adapter-level references, not replacements for Aviator's native execution identity.

The adapter resolves the declared operation contract, then binds it through ServiceLocator execution with explicit targeting and policy:

- A standard reference to a concrete provider operation restricts invocation to that constituent. Other operations sharing its locator must not be invoked silently.
- A reference to an operation exposing an aggregate capability, such as a generated gateway operation, evaluates that operation's declared aggregate response rather than pretending it is one raw provider response.
- Direct native ServiceLocator targeting requires a separately specified, opt-in Aviator extension/profile. Its syntax is not designed yet; any `x-dicentra-aviator-*` extension must be defined through `AviatorExtensionSpec`. This is not a prerequisite for the initial standard HTTP profile.

Core remains one-to-many even where standard Arazzo references require an exact constituent. In-process aggregate values have no implicit HTTP status/headers: those belong to constituent responses or a real gateway response and must not be fabricated.

## Initial execution profile

| Feature | Planned first slice | Later work |
| --- | --- | --- |
| Document representation | JSON; explicit Arazzo version checks | YAML through a separately evaluated KMP-compatible parser |
| Source descriptions | Supplied OpenAPI documents with stable identities | Additional Arazzo documents; AsyncAPI only with suitable execution bindings |
| Operation references | Source-qualified `operationId` translated to ServiceLocator bindings with explicit constituent/aggregate contract targeting | `operationPath`, comprehensive URI/reference handling and a separately specified opt-in native locator profile |
| Scheduling | Ordered synchronous steps | Nested workflows, conditional transitions and bounded retries |
| Inputs and outputs | JSON values, `$inputs`, prior step outputs, response body JSON Pointer | Broader expression coverage and advanced selectors |
| Criteria | Explicitly enumerated simple comparisons, including HTTP status | Additional operators, regex, JSONPath and XPath |
| Requests | Literal/expression parameters and JSON body mapping | Payload replacements and additional media types |
| Lifecycle | In-process execution, cancellation, deadline and structured events | Optional persistence/recovery through additional infrastructure |

This is an implementation profile, not permission to reinterpret specification semantics. Reject documents containing executable features outside the supported profile before making calls. Documentation annotations and extension preservation need a separate, explicit policy.

## Phase 1 — Models and validation

- [ ] Recheck published specification and schemas before implementation; use 1.1.0 as the current design reference.
- [ ] Define serializable models separately from runtime state. Preserve extensions without enabling arbitrary behavior.
- [ ] Distinguish specification version (`arazzo`) from document version (`info.version`). Publish the supported version/feature matrix.
- [ ] Parse complete supplied documents before resolving references. Maintain retrieval/base URI and `$self` identity where applicable.
- [ ] Validate required fields, unique IDs, mutually exclusive operation/workflow references, source names and reusable component references.
- [ ] Validate sequential dependency order and reject unresolved/forward output references for the initial profile.
- [ ] Decide input JSON Schema 2020-12 validation support explicitly; do not assume existing OpenAPI input validators implement the complete dialect. Reject unsupported validation constructs.
- [ ] Return useful diagnostics identifying workflow, step and document location.

## Phase 2 — Resolve and prepare execution

- [ ] Resolve source document plus operation ID to the declared contract, then bind through ServiceLocator-native execution with explicit constituent restriction or aggregate contract policy. Preserve source revision/provenance without imposing exact-one-operation execution on core.
- [ ] Reject missing, ambiguous or non-executable references before execution where possible. Standard concrete-operation references must not silently fan out because other operations share their locator; declared aggregate operation contracts may represent multiple implementations.
- [ ] Do not fetch arbitrary `sourceDescriptions.url` values. Start with supplied documents; any later remote resolver must be opt-in, policy-controlled and resource-bounded.
- [ ] Respect source identities and base URI semantics rather than treating a workflow document's location as the provider endpoint.
- [ ] Specify which declared API contract each binding exposes, especially when Weaver or Catalyst transforms results.
- [ ] Preflight supported constructs, static references and permissions where available; retain policy enforcement at invocation time.

## Phase 3 — First executable vertical slice

- [ ] Implement per-run state for inputs, completed step outputs and final outputs; no shared mutable state between runs.
- [ ] Parse supported runtime expressions with a dedicated grammar, not host-language evaluation or unrestricted string interpolation.
- [ ] Preserve JSON types in parameter/body mapping and distinguish missing values from explicit JSON null.
- [ ] Implement JSON Pointer escaping, arrays, missing-target errors and supported runtime-expression contexts.
- [ ] Evaluate supported success criteria against the declared operation response: the constituent response for a concrete reference, or the real aggregate gateway response for a gateway reference. Preserve error responses and metadata even when typed decoding is unavailable. Never invent aggregate HTTP metadata; define criteria/output contexts before accepting a native locator extension.
- [ ] Implement specification-defined success/failure defaults for supported HTTP steps, including explicit tests for omitted `successCriteria`.
- [ ] Execute sequential steps through shared ServiceLocator execution bindings with explicit targeting/policy, an isolated request context per constituent invocation, and aggregate/constituent outcomes where exposed.
- [ ] Stop on failure or cancellation as required; emit structured events and terminal outcomes without claiming previous side effects were rolled back.
- [ ] Enforce whole-run and per-step budgets counting actual constituent calls/concurrency/retries and aggregate plus constituent retention, with secret-aware diagnostic redaction.

## Phase 4 — Application proofs

### Embedded application / AreaAssist

- [ ] Create a read-only two-step fixture with a genuine output-to-input dependency, executed against locally supplied Aviator services.
- [ ] Verify embedded core bindings aggregate different operations sharing a ServiceLocator, while the standard Arazzo adapter preserves explicit concrete-operation targeting. Keep this extension-free HTTP proof separate from any future native locator profile.
- [ ] Exercise existing adapters and a transformed response binding without embedding AreaAssist models into the runtime.
- [ ] Verify execution does not require Synapse, discovery or Room. Keep eligibility selection and display conversion application-owned.
- [ ] Separately demonstrate local scheduling over Catalyst-backed operations where a gateway is required. This does not imply direct browser provider access or bypass CORS.

### Hosted application / Synapse

- [ ] Execute the same portable orchestration implementation with scope-aware registry bindings and enforced outbound policy.
- [ ] Verify a workflow cannot access services unavailable to its caller, including indirect references when nesting is added.
- [ ] Authorize every selected constituent of a capability and preserve per-constituent provenance and limits; hosted execution must not redefine native aggregation semantics.
- [ ] Keep workflow CRUD, generated invocation OpenAPI, MCP exposure and persistent run storage outside this module's initial scope.

## Phase 5 — Expand deliberately

- [ ] Add operation paths, reusable components, workflow-level parameter/action inheritance and wider expression coverage with specification fixtures.
- [ ] Add nested workflows and conditional actions with recursion/transition budgets and deterministic selection semantics.
- [ ] Add retries with specification-defined limits/delays and application-owned write/idempotency policy. Coordinate with lower-layer retry behavior to bound actual calls.
- [ ] Implement declared dependency semantics before accepting them; dependencies are not permission to invent automatic parallel scheduling.
- [ ] Evaluate 1.1 selectors and XML requirements independently of Weaver's transformation language.
- [ ] Evaluate AsyncAPI only after transport bindings, correlation, timeouts and async lifecycle semantics exist. Do not claim support merely because the document model accepts the fields.
- [ ] Compare against a second orchestrator candidate before extracting any shared workflow graph or expression language into core.
- [ ] Specify an opt-in native ServiceLocator Arazzo profile only when required, with explicit selection/aggregation policy, partial outcomes, provenance and criteria/output contexts; do not change standard operation-reference semantics.

## Verification and build integration

- [ ] Register `:orchestration:arazzo` and add a KMP build following existing conventions once implementation begins. Keep dependencies directed toward orchestration core and needed Aviator APIs, never applications.
- [ ] Test deterministic workflows, source qualification, status/header/body expressions, JSON types, null/missing distinctions, parameter-location collisions, bodyless responses and non-2xx outcomes.
- [ ] Test different operation IDs sharing a locator aggregate at the core binding level; different flavours remain separate; standard concrete Arazzo references stay restricted; aggregate gateway references use their public contract; no aggregate HTTP metadata is invented. Cover per-constituent authorization, provenance, partial outcomes and actual-call budgets.
- [ ] Test invalid/unsupported documents cause zero operation calls; failed criteria prevent later steps; concurrent runs remain isolated.
- [ ] Test cancellation, timeouts, budget exhaustion, redaction, blocked references and blocked destinations.
- [ ] Use fake operation bindings for scheduler tests and actual Aviator mock/transport fixtures for integration tests.
- [ ] Cross-check supported fixtures with independent Arazzo validation tooling; document each tool's supported version. External schema validation is not proof of runtime semantic conformance.
- [ ] Verify JVM and JS execution, then supported Android/Native compilation. Run Gradle under pinned Nix Java 25 with `--no-daemon`; report completed verification separately from planned commands.
- [ ] Evaluate shared npm packaging, artifact coordinates and public API exposure before publication. No new optional runtime should be pulled into existing clients by default.

## First milestone acceptance

A locally supplied, supported Arazzo document runs two read-only steps through ServiceLocator-native Aviator bindings with explicit reference targeting, passes a selected output into the second request, checks success criteria and returns declared outputs. Core bindings support aggregation of different operations sharing a locator; standard concrete Arazzo operation references do not silently fan out. The same runtime accepts application-local and server-policy bindings. Invalid references, unsupported features, authorization rejection and cancellation prevent inappropriate further calls. All support limitations are documented and tested.
