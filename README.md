# Aviator ✈️

[![DiCentra](https://img.shields.io/badge/DiCentra-grey.svg)](https://code.mallne.cloud)
[![Kotlin](https://img.shields.io/badge/kotlin-grey.svg?logo=kotlin)](https://kotlinlang.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-3.x-green.svg?logo=openapiinitiative)](https://www.openapis.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-blue.svg?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)

**Aviator** is a modern, API-first application framework built with **Kotlin Multiplatform**. It empowers developers to
build robust services and clients by treating the **OpenAPI (OAS)** specification as the single source of truth.

---

## 🚀 Overview

Aviator bridges the gap between API design and implementation. By extending the OpenAPI specification with custom
`x-dicentra-aviator-*` attributes, the framework can automatically "materialize" declarative API operations into
executable Kotlin service logic.

### Why Aviator?

- **API-First:** Design your API in OpenAPI and let Aviator handle the plumbing.
- **Multiplatform:** Share service definitions and client logic across JVM, Android, and other KMP targets.
- **Type-Safe:** Leverage Kotlin's powerful type system for your API models.
- **Extensible:** A rich plugin system for authentication, interception, and content negotiation.

---

## 📦 Project Structure

The project is highly modular, allowing you to include only what you need:

| Module                         | Description                                                             |
|:-------------------------------|:------------------------------------------------------------------------|
| **`:core`**                    | The heart of Aviator. Handles service conversion and plugin management. |
| **`:koas`**                    | Kotlin-native models and utilities for OpenAPI 3.x specifications.      |
| **`:clients`**                 | Implementations for consuming Aviator services.                         |
| **`:plugins`**                 | Extensions for various cross-cutting concerns.                          |
| **`:aviator-resource-server`** | A reference Ktor server for testing and validation.                     |

---

## 🔌 Plugins

Aviator uses a powerful plugin system to handle cross-cutting concerns. Plugins are activated during the service
crystallization process.

### Content Adapters

Enable support for different content types (JSON, XML) using Kotlin Serialization.

- **`JsonAdapter`**: Provides JSON serialization support.
  ```kotlin
  plugins {
      install(JsonAdapter) {
          // Standard configuration
      }
  }
  ```
- **`XmlAdapter`**: Provides XML serialization support.
  ```kotlin
  plugins {
      install(XmlAdapter) {
          // Standard configuration
      }
  }
  ```

### Security & Middleware

- **`HttpAuth`**: Adds support for HTTP authentication (e.g., Basic, Bearer).
  ```kotlin
  plugins {
      install(HttpAuth) {
          doBase64Encode = true
          serviceFilter = mutableListOf(ServiceLocator("my-secure-service"))
      }
  }
  ```
- **`Interception`**: Allows for custom request/response interception via a staged pipeline.
  ```kotlin
  plugins {
      install(Interception) {
          steps {
              before { context -> 
                  println("Executing: ${context.serviceLocator}")
              }
          }
      }
  }
  ```

### Advanced Integrations

- **`Synapse`**: Facilitates communication between disparate service layers or external systems.
  ```kotlin
  plugins {
      install(Synapse) {
          active = true
      }
  }
  ```
- **`Weaver`**: Integrates with the DiCentra Weaver engine for advanced data transformation and schema validation.
  ```kotlin
  plugins {
      install(Weaver) {
          weaver = myCustomWeaver
          schema = myWeaverSchema
      }
  }
  ```

---

## 🛰️ Clients

Aviator provides multiple client implementations to suit different environments and testing needs.

- **`Ktor Client` (`:clients:ktor`)**:
  The primary production-ready client. It uses the Ktor HTTP engine to perform real network requests based on the
  Aviator service definitions derived from OpenAPI. It supports full content negotiation and plugin execution.

- **`Mock Client` (`:clients:mock`)**:
  A specialized client for testing and local development. Instead of making network calls, it captures the execution
  context (headers, body, parameters) and allows you to simulate service responses, making it ideal for unit and
  integration tests.

---

## 🛠️ Getting Started

### Prerequisites

- JDK 17 or higher
- Gradle

### Building the Project

```bash
./gradlew build
```

### Running the Test Server

To see Aviator in action, you can run the built-in resource server:

```bash
./gradlew :aviator-resource-server:run
```

---

## 🧩 Custom OpenAPI Extensions

Aviator uses custom extensions to map OpenAPI operations to Kotlin services:

- `x-dicentra-aviator`: Specifies the version of the Aviator spec.
- `x-dicentra-aviator-serviceDelegateCall`: Points to the Kotlin service implementation.
- `x-dicentra-aviator-serviceOptions`: Provides configuration options for the service.
- `x-dicentra-aviator-pluginMaterialization`: Configures plugins for specific routes.

---

## 📜 License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Built with ❤️ by Mallne under the DiCentra umbrella
</p>
