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
+-- aviator-resource-server/ # Reference/test Ktor server
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

## Dependencies on Other Modules

- **Polyfill** -- Utility functions and contracts
- **Weaver** -- Optional integration via `:plugins:weaver`
- **Synapse** -- Optional integration via `:plugins:synapse`

## Non-negotiable Rules

- OpenAPI specs are the "source of truth" -- all service behavior derives from the spec
- Use `@InternalAviatorAPI` for internal framework APIs
- All network requests in business logic MUST use Aviator (not direct Ktor)
- Code in `commonMain` must remain platform-agnostic
