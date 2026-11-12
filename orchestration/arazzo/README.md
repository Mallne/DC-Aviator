# Aviator Arazzo Orchestrator

Status: AV-10 provides serializable JSON document models and a fail-closed validation boundary. A narrow sequential runner/binding seam is in progress; the full AV-11 scheduler remains planned and depends on the optional plugin work in [AV-21](https://dicentra.youtrack.cloud/issue/AV-21). This module does not claim full Arazzo 1.1.0 conformance.

This module will implement the Arazzo workflow specification using [Aviator Orchestration Core](../core/README.md). It is an orchestrator, not an Aviator request-pipeline plugin.

Orchestration will use AV-21's first-class staged plugin architecture, with expressive parity to base Aviator: configurable installation, scoped context, before/after stage hooks, controlled input/result transformation and short-circuit behavior, not just policy observers. The Arazzo engine must expose its preparation, resolution, scheduling, mapping and completion stages through that architecture before AV-11 stabilizes; operation-level plugins remain independent and composable. Current authorization/non-byte execution budgets remain pending extraction. Core retained-byte accounting and aggregate/copy reservations are removed; Arazzo does not promise a heap-memory limit. Any future payload-size policy must explicitly define wire or serialized-size semantics.

## Responsibilities

- Parse and validate Arazzo documents with explicit version/feature support.
- Resolve source descriptions and operation references into supplied ServiceLocator-native bindings with explicit constituent or aggregate contract targeting.
- Evaluate inputs, runtime expressions, success criteria and outputs.
- Schedule steps and apply supported Arazzo control-flow semantics.
- Invoke individual operations through Aviator and emit shared execution events.

The runtime must work embedded in applications such as AreaAssist as well as hosted in Synapse. It must not require a remote registry, database or server to execute a local workflow definition.

Aviator can aggregate different operations sharing a ServiceLocator. Standard Arazzo references still identify declared OpenAPI contracts: concrete-operation references must not silently fan out, while gateway operations may explicitly expose aggregate responses. Direct native locator targeting would require a separately designed opt-in extension/profile; it must not reduce core to exact-one-operation execution or invent HTTP metadata for an in-process aggregate.

## Specification boundary

OpenAPI remains the source of truth for operation contracts. Arazzo defines how operations compose into a workflow. Existing Aviator plugins continue handling operation-level auth, content adaptation, transformation and telemetry.

The current published Arazzo specification is 1.1.0. `arazzo` is the specification version; `info.version` is the document version. The initial executable profile accepts JSON, Arazzo 1.1.0, OpenAPI source descriptions, and sequential operationId HTTP steps only. It rejects operationPath, nested workflows, non-OpenAPI sources, request-body mapping, explicit dependencies, criteria extensions, workflow input schemas, and unknown fields. JSON Schema Draft 2020-12 validation is **not implemented**, so workflow `inputs` schemas are rejected rather than treated as validated. YAML, selectors, AsyncAPI, actions/retries, external fetching, and arbitrary `x-` execution are unsupported. `x-` values are preserved as inert data; unrecognized `x-dicentra-aviator-*` keys are rejected pending a separately specified, opt-in Aviator profile defined through `AviatorExtensionSpec`.

## Intended source layout

```text
src/commonMain/kotlin/cloud/mallne/dicentra/aviator/orchestration/arazzo/
  model/       # Serializable document models
  validation/  # Version, feature and semantic checks
  resolution/  # Source, operation and workflow references
  expression/  # Runtime expressions and criteria
  execution/   # Arazzo scheduler and per-run state
src/commonTest/kotlin/cloud/mallne/dicentra/aviator/orchestration/arazzo/
src/commonTest/resources/ # Workflow fixtures where target tooling supports resources
```

Layout and package names are provisional. Tests may embed fixtures when common resource access is unavailable.

See [PLAN.md](PLAN.md) for the phased support matrix and implementation plan.

## References

- [Official Arazzo overview](https://www.openapis.org/arazzo-specification)
- [Published Arazzo 1.1.0](https://spec.openapis.org/arazzo/v1.1.0.html)
- [Specification repository and tooling](https://github.com/OAI/Arazzo-Specification)
