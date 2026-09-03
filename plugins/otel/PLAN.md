# Aviator OpenTelemetry Plugin — Implementation Plan

## Overview

A new Aviator plugin module that instruments API calls with OpenTelemetry traces,
providing semantic spans with service names, HTTP attributes, and pipeline stage
visibility. Built on the KMP-native `opentelemetry-kotlin` SDK.

**Scope:** Traces only (spans). No metrics or logs in v1.
**Platforms:** JVM, Android, JS, iOS (where `opentelemetry-kotlin` works).
wasmJs and linuxX64 get no-op stubs.

---

## 1. Module Structure

```
aviator/plugins/otel/
├── build.gradle.kts
├── src/
│   ├── commonMain/kotlin/cloud/mallne/dicentra/aviator/plugin/otel/
│   │   ├── OpenTelemetryPlugin.kt          # object : AviatorPlugin<...>
│   │   ├── OpenTelemetryPluginConfig.kt    # class : AviatorPluginConfigScope
│   │   ├── OpenTelemetryPluginInstance.kt  # data class : AviatorPluginInstance
│   │   ├── OpenTelemetryPluginExecutor.kt  # class : PluginStagedExecutor
│   │   └── SpanKeys.kt                     # bundle key constants
│   └── commonTest/kotlin/cloud/mallne/dicentra/aviator/plugin/otel/
│       └── OpenTelemetryPluginTest.kt
```

No `expect`/`actual` needed — `opentelemetry-kotlin:api` is a `commonMain` dependency
(their own KMP module publishes for JVM/Android/JS/iOS). On unsupported platforms
(wasmJs, linuxX64) the Gradle resolution will fail at dependency lookup; those targets
are excluded from this module (see build file).

---

## 2. File-by-File Plan

### 2.1 `build.gradle.kts`

Follow the exact template from `plugins/http-auth/build.gradle.kts`:

```kotlin
@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "cloud.mallne.dicentra.aviator.plugin"
version = "1.0.0-SNAPSHOT"
description = "DiCentra Aviator Plugin for OpenTelemetry distributed tracing"

plugins {
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            pom {
                name = "DiCentra Aviator OpenTelemetry Plugin"
                description = "DiCentra Aviator Plugin for OpenTelemetry distributed tracing"
                inceptionYear = "2025"
                developers { developer { name = "Mallne"; url = "mallne.cloud" } }
            }
            repositories {
                maven {
                    url = uri("https://registry.mallne.cloud/repository/DiCentraArtefacts/")
                    credentials {
                        username = project.findProperty("dc.username") as String?
                        password = project.findProperty("dc.password") as String?
                    }
                }
            }
        }
    }
}

kotlin {
    jvm()
    android {
        namespace = "${project.group}.otel"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    js { nodejs(); browser() }
    // No wasmJs — opentelemetry-kotlin does not support it
    iosArm64(); iosSimulatorArm64()
    // No linuxX64 — opentelemetry-kotlin does not support it

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":core"))
                implementation(project(":clients:ktor"))
                implementation(libs.ktor.client.core)
                implementation("io.opentelemetry.kotlin:api:0.7.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.opentelemetry.kotlin:exporters-in-memory:0.7.0")
                implementation("io.opentelemetry.kotlin:testing:0.7.0")
            }
        }
    }
    jvmToolchain(25)
}
```

**Key differences from other plugins:**
- Adds `opentelemetry-kotlin:api` as an `implementation` dependency
- Excludes `wasmJs` and `linuxX64` targets (unsupported by opentelemetry-kotlin)
- Adds `:clients:ktor` dependency (needs `KtorAviatorService` for HTTP details)
- Test dependencies include in-memory exporter for span verification

### 2.2 `SpanKeys.kt`

Constants for `context.bundle` keys used to carry span state between hooks.

```kotlin
package cloud.mallne.dicentra.aviator.plugin.otel

internal object SpanKeys {
    const val SPAN = "dc-otel-span"
    const val SPAN_CONTEXT = "dc-otel-span-context"
    const val REQUEST_START_NANOS = "dc-otel-request-start-nanos"
}
```

### 2.3 `OpenTelemetryPluginConfig.kt`

```kotlin
package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginConfigScope
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginDsl
import cloud.mallne.dicentra.aviator.model.ServiceLocator
import io.opentelemetry.kotlin.api.OpenTelemetry

@AviatorPluginDsl
class OpenTelemetryPluginConfig : AviatorPluginConfigScope {
    override var serviceFilter: MutableList<ServiceLocator> = mutableListOf()
    override val silentLoggingTags: MutableList<String> = mutableListOf()

    /** The OpenTelemetry instance to use for tracing. Required. */
    var openTelemetry: OpenTelemetry? = null

    /**
     * HTTP response status codes to consider errors (set ERROR span status).
     * Default: 500-599.
     */
    var errorStatusCodes: IntRange = 500..599

    /**
     * Whether to record the full URL (including query params) as a span attribute.
     * When false, only the path component is recorded.
     * Default: true.
     */
    var recordFullUrl: Boolean = true
}
```

### 2.4 `OpenTelemetryPlugin.kt`

```kotlin
package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance

object OpenTelemetryPlugin : AviatorPlugin<OpenTelemetryPluginConfig> {
    override val identity: String = "DC-AV-OpenTelemetry"

    override fun install(config: OpenTelemetryPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = OpenTelemetryPluginConfig()
        config.invoke(pluginConfig)
        return OpenTelemetryPluginInstance(
            configurationBundle = pluginConfig,
            identity = identity,
            x = OpenTelemetryPluginExecutor(pluginConfig)
        )
    }
}
```

### 2.5 `OpenTelemetryPluginInstance.kt`

```kotlin
package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import kotlinx.serialization.Serializable

data class OpenTelemetryPluginInstance(
    override val configurationBundle: OpenTelemetryPluginConfig,
    override val identity: String,
    override val x: PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any>
) : AviatorPluginInstance
```

### 2.6 `OpenTelemetryPluginExecutor.kt` — the core

This is the main implementation. Hooks into Aviator's pipeline stages:

| Hook | Action |
|------|--------|
| `beforeRequesting` | Start a CLIENT span. Store in `bundle[SpanKeys.SPAN]`. Set HTTP method, URL, service name, operation ID. |
| `afterRequesting` | Record HTTP status code, response body size. Set span status (ERROR for 5xx). |
| `afterPaintingResponse` | Record deserialization success/failure. End the span. |

```kotlin
package cloud.mallne.dicentra.aviator.plugin.otel

import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionContext
import cloud.mallne.dicentra.aviator.core.execution.AviatorExecutionStages
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutorBuilder
import io.ktor.http.*
import io.opentelemetry.kotlin.api.OpenTelemetry
import io.opentelemetry.kotlin.api.span.SpanKind
import io.opentelemetry.kotlin.api.trace.StatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long

class OpenTelemetryPluginExecutor(
    private val config: OpenTelemetryPluginConfig
) : PluginStagedExecutor<AviatorExecutionContext<@Serializable Any, @Serializable Any>, @Serializable Any, @Serializable Any> {

    private val openTelemetry: OpenTelemetry
        get() = config.openTelemetry
            ?: throw IllegalStateException("OpenTelemetry instance not configured in OpenTelemetryPluginConfig")

    private val tracer by lazy {
        openTelemetry.tracerProvider.getTracer(
            instrumentationName = "io.opentelemetry.aviator",
            instrumentationVersion = "1.0.0"
        )
    }

    override suspend fun beforeRequesting(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        val chain = context.networkChain.lastOrNull() ?: return
        val request = chain.request ?: return
        val serviceLocator = context.dataHolder.serviceLocator
        val route = context.dataHolder.route

        val spanName = "${request.method.value} ${serviceLocator}/${route.operationId ?: route.path}"

        val span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("aviator.service", serviceLocator.toString())
            .setAttribute("aviator.operation", route.operationId ?: "unknown")
            .setAttribute("http.request.method", request.method.value)
            .setAttribute("url.full", chain.url)
            .setAttribute("http.route", route.path)
            .startSpan()

        val requestSize = when (val body = request.outgoingContent) {
            is NetworkBody.Text -> body.string.length.toLong()
            is NetworkBody.Form -> body.formData.size.toLong()
            is NetworkBody.Empty -> 0L
        }
        if (requestSize > 0) {
            span.setAttribute("http.request.body.size", requestSize)
        }

        context.bundle[SpanKeys.SPAN] = JsonPrimitive(span.getSpanContext().spanId)
        // Store span reference via a thread-local or context map (see note below)
        context.bundle[SpanKeys.REQUEST_START_NANOS] = JsonPrimitive(System.nanoTime())

        // Keep a reference map since JsonElement can't hold Span objects directly
        spanCache[span.getSpanContext().spanId] = span
    }

    override suspend fun afterRequesting(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        val spanId = (context.bundle[SpanKeys.SPAN] as? JsonPrimitive)?.content ?: return
        val span = spanCache.remove(spanId) ?: return

        val lastChain = context.networkChain.lastOrNull()
        val response = lastChain?.response

        if (response != null) {
            val statusCode = response.status.value
            span.setAttribute("http.response.status_code", statusCode.toLong())

            val responseSize = response.content?.size?.toLong() ?: 0L
            if (responseSize > 0) {
                span.setAttribute("http.response.body.size", responseSize)
            }

            if (statusCode in config.errorStatusCodes) {
                span.setStatus(StatusCode.ERROR, "HTTP $statusCode")
            } else {
                span.setStatus(StatusCode.OK)
            }
        } else {
            span.setStatus(StatusCode.ERROR, "No response received")
        }

        // Don't end yet — wait for afterPaintingResponse to capture deserialization status
        // Store for afterPaintingResponse
        pendingSpans[spanId] = span
    }

    override suspend fun afterPaintingResponse(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        val spanId = (context.bundle[SpanKeys.SPAN] as? JsonPrimitive)?.content ?: return
        val span = pendingSpans.remove(spanId) ?: spanCache.remove(spanId) ?: return

        // If result is null after painting, deserialization may have failed
        // (only meaningful if HTTP was successful)
        val lastChain = context.networkChain.lastOrNull()
        val httpOk = (lastChain?.response?.status?.value ?: 500) < 400
        if (httpOk && context.result == null) {
            span.setAttribute("aviator.deserialization.success", false)
            span.setStatus(StatusCode.ERROR, "Deserialization failed")
        } else {
            span.setAttribute("aviator.deserialization.success", true)
        }

        span.end()
    }

    override suspend fun afterFinished(context: AviatorExecutionContext<@Serializable Any, @Serializable Any>) {
        // Safety net: if span wasn't ended by afterPaintingResponse, end it here
        val spanId = (context.bundle[SpanKeys.SPAN] as? JsonPrimitive)?.content ?: return
        val span = pendingSpans.remove(spanId) ?: spanCache.remove(spanId) ?: return
        if (span.spanContext.isValid) {
            span.end()
        }
    }

    companion object {
        // In-memory span cache keyed by spanId.
        // Works because Aviator processes requests sequentially per coroutine.
        // For concurrent use, consider a CoroutineContext-based approach.
        private val spanCache = mutableMapOf<String, io.opentelemetry.kotlin.api.span.Span>()
        private val pendingSpans = mutableMapOf<String, io.opentelemetry.kotlin.api.span.Span>()
    }
}
```

**Note on Span storage:** The `context.bundle` is `MutableMap<String, JsonElement>`,
which can't hold `Span` objects directly. The executor uses a companion-object cache
as a pragmatic solution. A cleaner long-term approach would be to add a
`MutableMap<String, Any>` alongside `bundle` in `AviatorExecutionContext`, but that
changes the core API and is out of scope for v1.

### 2.7 `OpenTelemetryPluginTest.kt`

```kotlin
package cloud.mallne.dicentra.aviator.plugin.otel

import io.opentelemetry.kotlin.api.OpenTelemetry
import io.opentelemetry.kotlin.exporters.inmemory.InMemorySpanExporter
import io.opentelemetry.kotlin.sdk.trace.export.SimpleSpanProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenTelemetryPluginTest {

    @Test
    fun testPluginIdentity() {
        assertEquals("DC-AV-OpenTelemetry", OpenTelemetryPlugin.identity)
    }

    @Test
    fun testInstallCreatesInstance() {
        val exporter = InMemorySpanExporter()
        val openTelemetry = io.opentelemetry.kotlin.core.openTelemetry {
            addSpanProcessor(SimpleSpanProcessor(exporter))
        }

        val instance = OpenTelemetryPlugin.install {
            this.openTelemetry = openTelemetry
        }

        assertEquals("DC-AV-OpenTelemetry", instance.identity)
        assertTrue(instance is OpenTelemetryPluginInstance)
    }

    // Additional tests:
    // - Verify span attributes are set correctly with a mock AviatorExecutionContext
    // - Verify error status is set for 5xx responses
    // - Verify span is ended after afterPaintingResponse
    // - Verify safety net in afterFinished doesn't double-end
}
```

---

## 3. Version Catalog Addition

Add to `aviator/gradle/libs.versions.toml`:

```toml
[versions]
# ... existing ...
opentelemetry-kotlin = "0.7.0"

[libraries]
# ... existing ...
opentelemetry-kotlin-api = { module = "io.opentelemetry.kotlin:api", version.ref = "opentelemetry-kotlin" }
opentelemetry-kotlin-exporters-in-memory = { module = "io.opentelemetry.kotlin:exporters-in-memory", version.ref = "opentelemetry-kotlin" }
opentelemetry-kotlin-testing = { module = "io.opentelemetry.kotlin:testing", version.ref = "opentelemetry-kotlin" }
```

---

## 4. Settings Addition

Add to `aviator/settings.gradle.kts`:

```kotlin
include(":plugins:otel")
```

---

## 5. Consumer Usage

```kotlin
// 1. Create the OpenTelemetry instance (KMP-native)
val openTelemetry = openTelemetry {
    addSpanProcessor(BatchSpanProcessor(OTlpSpanExporter("http://localhost:4318")))
}

// 2. Create the Aviator converter with the OTEL plugin
val converter = KtorAviatorServiceConverter(httpClient) {
    install(OpenTelemetryPlugin) {
        this.openTelemetry = openTelemetry
        errorStatusCodes = 500..599
    }
}

// 3. All Aviator calls are now automatically traced
val services = converter.build(openApiDoc)
val result = services[0].request<UserResponse, UserRequest>(body)
```

---

## 6. What's NOT in Scope (v1)

| Feature | Why deferred |
|---------|-------------|
| **Metrics** (HTTP duration histograms, request counts) | opentelemetry-kotlin metrics API is experimental; can add as a separate plugin module |
| **Distributed context propagation** (W3C traceparent header injection) | Requires lower-level Ktor client hooks or opentelemetry-kotlin's Ktor plugin; transport-layer concern |
| **wasmJs / linuxX64 support** | opentelemetry-kotlin doesn't support these platforms yet |
| **Pipeline stage child spans** (constraint validation, path matching timing) | Adds complexity; most users care about HTTP round-trip, not internal stages. Can add later. |
| **Captured request/response headers** | Configurable but deferred to keep v1 simple |

---

## 7. Resolved Questions

| Question | Decision | Rationale |
|----------|----------|-----------|
| **Span cache thread safety** | Wrap maps with `Mutex` from `kotlinx.coroutines` | KMP-safe, no platform-specific APIs, handles concurrent coroutines |
| **Version catalog placement** | Root catalog (`aviator/gradle/libs.versions.toml`) | Centralized, matches how `kotlin`/`ktor`/`serialization` are managed |
| **Validate at install time** | Yes — throw `IllegalArgumentException` if `openTelemetry` is null | Fail fast at configuration, not at first request |
| **Artifact name** | `otel` | Concise, community convention, no ambiguity |
