# Aviator Project Overview

## Project Overview

**Aviator** is a specialized application framework developed by DiCentra, designed for building services and APIs using
an API-first approach centered around **OpenAPI (OAS)**. It is built using **Kotlin Multiplatform (KMP)**, allowing for
shared logic across different platforms while leveraging the power of **Ktor** and **Kotlin Serialization**.

The framework's core philosophy is to use the OpenAPI specification as the "source of truth" for service definitions,
extending it with custom attributes (`x-dicentra-aviator-*`) to map declarative API operations to actual Kotlin service
implementations.

### Key Technologies

- **Kotlin Multiplatform (KMP):** Core logic and models are shared.
- **Ktor:** Used for both client and server-side HTTP interactions.
- **Kotlin Serialization:** Primary mechanism for handling JSON, XML, and OpenAPI specifications.
- **OpenAPI 3.x:** The foundation for service definitions.
- **Koin:** Dependency injection (seen in `libs.versions.toml`).

## Project Structure

- **`:core`**: Contains the heart of the framework, including the `APIToServiceConverter` and the plugin system. It
  defines how OpenAPI specs are "materialized" into executable services.
- **`:koas` (Kotlin OpenAPI Specification)**: A dedicated module for modeling and manipulating OpenAPI 3.x
  specifications in Kotlin.
- **`:clients`**:
    - `ktor`: Ktor-based client implementation for Aviator services.
    - `mock`: A mock implementation for testing and simulation.
- **`:plugins`**: A collection of extensions for various cross-cutting concerns:
    - `adapter-json`/`adapter-xml`: Content negotiation and serialization adapters.
    - `http-auth`: Authentication support.
    - `interception`: Middleware/interceptor support for service calls.
    - `weaver`/`synapse`: Advanced integration or code-generation related modules.
- **`:aviator-resource-server`**: A Ktor-based reference or test server used to validate the framework's capabilities.

## Building and Running

The project uses Gradle with Kotlin DSL.

### Key Commands

- **Build the project:**
  ```bash
  ./gradlew build
  ```
- **Run the Resource Server (Test Server):**
  ```bash
  ./gradlew :aviator-resource-server:run
  ```
- **Run Tests:**
  ```bash
  ./gradlew test
  ```
- **Update Version Catalog:**
  ```bash
  ./gradlew versionCatalogUpdate
  ```

## Development Conventions

- **API-First Design:** Always consider how changes affect the OpenAPI representation.
- **OpenAPI Extensions:** Use the `AviatorExtensionSpec` in `:core` to interact with custom `x-dicentra-aviator`
  extensions in the specification.
- **Multiplatform Compatibility:** Ensure that code in `commonMain` remains platform-agnostic.
- **Serialization:** Favor `kotlinx.serialization` for all data modeling.
- **Opt-in APIs:** Be aware of `@InternalAviatorAPI` for features that are not yet stable or intended for external use.

## Important Files

- `settings.gradle.kts`: Defines the modular structure and monorepo integrations.
- `gradle/libs.versions.toml`: Centralized dependency management.
- `core/.../AviatorExtensionSpec.kt`: Defines the custom OpenAPI extensions used by the framework.
- `koas/.../OpenAPI.kt`: The root model for OpenAPI specifications.
