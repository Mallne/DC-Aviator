# Architecture -- Aviator

## Purpose

Aviator is a KMP library implementing an API-first framework that materializes OpenAPI (OAS 3.x) specifications into executable Kotlin services. It uses custom `x-dicentra-aviator-*` extensions to map declarative API operations to Ktor service implementations.

## Tech Stack

- **Language:** Kotlin Multiplatform (KMP)
- **HTTP:** Ktor (client and server)
- **Serialization:** kotlinx.serialization (JSON, XML, OpenAPI specs)
- **API Spec:** OpenAPI 3.x with custom extensions
- **DI:** Koin

## Project Structure

```
aviator/
+-- core/                    # APIToServiceConverter, plugin system, OpenAPI materialization
+-- koas/                    # Kotlin OpenAPI Specification model (KOAS)
+-- clients/
|   +-- ktor/                # Ktor-based client implementation
|   +-- mock/                # Mock client for testing
+-- plugins/
|   +-- adapter-json/        # JSON content negotiation
|   +-- adapter-xml/         # XML content negotiation
|   +-- http-auth/           # Authentication support
|   +-- interception/        # Middleware/interceptor support
|   +-- weaver/              # Weaver integration plugin
|   +-- synapse/             # Synapse integration plugin
```

## Data Flow

1. OpenAPI spec is loaded and parsed via KOAS
2. `APIToServiceConverter` reads the spec + `x-dicentra-aviator-*` extensions
3. Extensions map API operations to Kotlin service implementations
4. Plugins are applied during materialization (auth, content negotiation, interception)
5. Result is an executable Ktor service that handles HTTP requests per the spec

## API Surface

- **`:core`** -- `APIToServiceConverter`, `AviatorExtensionSpec`, plugin interfaces
- **`:koas`** -- `OpenAPI`, route/model/type definitions for OAS 3.x
- **`:clients:ktor`** -- Ktor HTTP client for calling Aviator services
- **`:clients:mock`** -- In-memory mock client for testing
- **`:plugins:*`** -- Content negotiation, auth, interception, integration plugins

## Orchestration Boundary

- Core execution identity is `ServiceLocator`, including flavour; Arazzo operation references preserve the concrete operation contract rather than implying locator fanout. Embedded and hosted aggregation both use the existing Aviator operation pipeline. Synapse and AreaAssist are consumers/proof environments, not orchestration dependencies.
- The completed simplification removed retained-byte accounting, its estimator/reservations and transport body-copy budget callbacks, not source response snapshots or cancellation. Other current budget and per-constituent authorization implementations remain in place; do not treat this as completed plugin extraction.
- [AV-21](https://dicentra.youtrack.cloud/issue/AV-21) is a foundational staged plugin architecture with expressive parity to base Aviator's `AviatorPlugin`, `AviatorPluginInstance`, configuration scopes and `PluginStagedExecutor`, not a minimal call/result SPI or observer/limiter layer. Design it alongside AV-7/AV-8 before API stabilization; integrate actual binding/attempt boundaries before AV-11. Completion dependencies do not make plugins an afterthought. This architecture remains planned, not implemented or qualified.
- Planned parity includes public installation/configuration DSL, identity/lookup, applicability filtering, reconfiguration with in-flight snapshots, metadata providers and typed run/step/attempt extension contexts. Suspending before/after hooks cover preparation/validation, resolution, selection, scheduling, input mapping, actual attempts/retries, response/results, aggregation, output mapping and terminal cleanup. Plugins can perform controlled mutation and rejection/short-circuiting, with deterministic composition, concurrent-state ownership and cleanup on success, failure or cancellation. See the [parity matrix](../orchestration/core/PLAN.md#base-aviator-parity-requirements).
- Format engines expose explicit stage adapters without embedding Arazzo language semantics in core. Existing operation plugins remain separate and composable. Hooks must preserve cancellation, mandatory host authorization, explicit targeting restrictions and truthful provenance. Optional policy plugins are one use case of this general extension system, not its purpose or mandatory defaults; future wire/serialized payload limits must not claim heap/retained-size accounting.

## Dependencies on Other Modules

- **Polyfill** -- Utility functions and contracts
- **Weaver** -- Optional integration via `:plugins:weaver`
- **Synapse** -- Optional integration via `:plugins:synapse`

## Non-negotiable Rules

- OpenAPI specs are the "source of truth" -- all service behavior derives from the spec
- Use `@InternalAviatorAPI` for internal framework APIs
- All network requests in business logic MUST use Aviator (not direct Ktor)
- Code in `commonMain` must remain platform-agnostic
