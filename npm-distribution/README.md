# Aviator shared npm distribution

Build-only Kotlin/JS linker and npm packager for Aviator. It links Core, Koas, Fetch, and all seven plugins **once**, then splits the linked modules into 41 individual npm packages. This module is not a consumer-facing umbrella package.

## Quick start: consume

Use named ES imports and a coherent, exact same-version Aviator release. Do not combine packages produced by independent Kotlin/JS links or mix release versions.

```ts
import { createFetchAviatorJsClient } from '@dicentra/aviator-client-fetch';
import { AviatorJsonAdapter } from '@dicentra/aviator-plugin-adapter-json';
import type { AviatorPluginActivationScope } from '@dicentra/aviator-core';

// openApiJson is a JSON string containing your OpenAPI document.
// Omit the third argument to activate no plugins.
const client = createFetchAviatorJsClient(openApiJson, globalThis.fetch,
  (scope: AviatorPluginActivationScope) => {
    scope.install(AviatorJsonAdapter);
  });
const result = JSON.parse(await client.request('findParcels', JSON.stringify({ q: 'A 1' })));
// result: { status, contentType, body }; body is raw response text.
```

Install `@dicentra/aviator-client-fetch` and only the optional plugins you need, pinning each to the same release version. Fetch's runtime dependency graph contains **no optional plugin packages**. Being included in the shared link does not make a plugin a Fetch runtime dependency or activate it.

## Build, pack, verify, publish

Prerequisites: the repository's mise-managed Java 25, Node.js, and npm. From the monorepo root in PowerShell:

```powershell
mise exec -- java -version
mise exec -- .\gradlew.bat --no-daemon :aviator:npm-distribution:assembleSharedNpmPackages
cd aviator/npm-distribution
npm run pack
npm run verify:fetch
# Only when authorized and ready to release:
npm run publish:shared -- --registry=https://registry.mallne.cloud/repository/NodeRegistry/
```

`VERSION_NAME` controls the shared version; packaging converts `-SNAPSHOT` to `-snapshot`. Generated packages and their dependency index are under `build/packages`; tarballs are under `build/tarballs`.

For the local verification recorded below, assembly used `-x :aviator:kotlinStorePackageLock` because of an existing changed local lock. This is a **local-only workaround**, not an unconditional CI instruction: normal CI must handle and validate the Kotlin package lock explicitly.

Publication uses normal npm registry authentication (for example an appropriately configured user npmrc). The script does **not** consume `NPM_TOKEN` itself; setting that variable alone does not authenticate it. Never commit credentials. The publish script orders all generated packages dependency-first and rejects cycles. Do not publish independently linked module outputs as a substitute.

## Architecture and ABI invariants

- `build.gradle.kts` links a KMP JS library in ES module mode with Core, Koas, Fetch, JSON/XML adapters, HTTP auth, interception, OpenTelemetry, Synapse, and Weaver.
- Independent Kotlin/JS links can assign incompatible internal ABI symbols even to identical Core/runtime sources. All generated packages in a release must come from this one link.
- `package-modules.mjs` rewrites generated relative module imports into exact-version npm dependencies. Public packages retain `@dicentra/aviator-core`, `@dicentra/aviator-koas`, `@dicentra/aviator-client-fetch`, and `@dicentra/aviator-plugin-*`; other generated Kotlin/external runtime modules use `@dicentra/aviator-js-*` names. External npm imports retain their generated dependency constraints.
- The declaration-only `@dicentra/aviator-js-types` package supplies shared declarations, avoiding duplicate nominal TypeScript identities. Core re-exports named types `AviatorPlugin`, `AviatorPluginActivationScope`, `AviatorPluginConfigScope`, and `AviatorPluginInstance`.
- Plugin entry facades unwrap Kotlin ES-module exported property getters. Consumers import objects such as `AviatorJsonAdapter` directly and call `scope.install(AviatorJsonAdapter)`; they do not call Kotlin-generated `.get()` accessors.
- Existing Core/Koas/Fetch/plugin `assembleJsPackage` tasks replace their generated package output with the corresponding shared-link package. They are compatibility entry points, not independent release pipelines.
- Packaging does not change request concurrency or coroutine semantics. No new scheduling, thread-safety, or browser compatibility guarantee is established by the packaging checks.

## Verification and limits

`npm run verify:fetch` packs generated packages and installs their tarballs into isolated `build/verification` fixtures, keeping npm away from monorepo pnpm-managed `node_modules` and lock files. It runs this module's Node `node:test` smoke suite (`test/fetch-client.test.mjs`) and TypeScript API check (`api-types.ts`). It no longer copies or depends on `areaassist/shared` tests or Playwright.

The smoke assertions cover matching shared Core dependency versions, absence of optional plugins from Fetch's runtime graph, and injected Fetch requests both with and without plugin activation. Earlier verification recorded Java 25 assembly and the former copied Playwright checks passing; that historical result is not verification of the replacement Node suite. Consult current command results for the migrated suite.

These are injected-fetch package smoke tests, not live-provider or browser-rendering tests. Shared's registry-generated Kotlin/JUnit 5 tests independently check definitions and live-provider/model drift; see [Shared API contract tests](../../areaassist/shared/README-api-contract-tests.md). No npm publication was executed.

The existing Gradle Fetch/JSON/Weaver `packJsPackage` invocation was also attempted under Java 25, but failed before packing at Fetch: the existing `org.jetbrains.kotlin.npm-publish` plugin looked for missing `bin/node` and `bin/npm` beneath the Windows `~/.gradle/nodejs/node-v24.16.0-win-x64` installation (Unix-style layout). Canonical assembly succeeded/up-to-date. Use the successfully verified `npm run pack` / `npm run verify:fetch` path above; fixing that unrelated plugin toolchain is outside this change.
