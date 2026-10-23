# Aviator

**Stack**: KMP library. OpenAPI-first API framework using Ktor.

> **Full docs**: [aviator/.ai/](.ai/) | [Notary](https://docs.mallne.cloud/doc/aviator-RdNz6f71NO)

## Critical Rules

1. OpenAPI specs are the source of truth -- all behavior derives from the spec
2. Use `@InternalAviatorAPI` for internal framework APIs
3. All network requests in business logic MUST use Aviator (not direct Ktor)
4. Code in `commonMain` must remain platform-agnostic
5. Use `x-dicentra-aviator-*` extensions only through `AviatorExtensionSpec`

## Build

```bash
./gradlew build
```

## Activity Log

- Serialized Catalyst forwarding (AA-114): `CatalystRequest.body` stays `JsonElement`, with sealed `bodyFormat` `Json` (default) / `Base64(contentType)`. Wire example: `{"bodyFormat":{"type":"base64","contentType":"application/xml"},"body":"<canonical padded base64>"}`. Synapse accepts nonempty canonical base64 JSON strings only for action-declared `application/xml` or `text/xml`; Aviator `RawRequestBody` forwards the decoded `ByteArray` without a DOM roundtrip. Generated clients remain JSON-envelope clients; scope/outbound/limit policies are unchanged.
- Final root verification passed after generated-schema assertions and raw-content header changes: 52 Catalyst, 24 shared JVM, and 2 Aviator Synapse tests (zero failures/skips). Subsequent Catalyst retry passed with malformed media-type rejection and `BadContentTypeFormatException` mapped to 400. `:aviator:plugins:synapse:compileKotlinJs` succeeded in an earlier mixed run; its unrelated concurrent `XmlResponseAdapter` import failure was resolved outside this task before the focused retry passed. XML adapter task was `NO-SOURCE`, not actual adapter tests; XML response integration passed in the Catalyst suite. Standalone AreaAssist Server passed 3 tests. No runnable local AreaAssist frontend/backend or actual origin was available (Playwright only `about:blank`); live-browser/real-provider verification, actual deployment origin, and deployment egress verification remain open.
- External Synapse plugin docs corrected and [serialized forwarding/browser deployment guide](https://docs.mallne.cloud/doc/serialized-request-forwarding-areaassist-browser-deployment-gxivMlTUHL) published. [AA-114](https://dicentra.youtrack.cloud/issue/AA-114#focus=Comments-5-142.0-0) remains Offen/unresolved. Deployment settings and exact completed commands are recorded in `../synapse/AGENTS.md`.
- Shared npm packaging: build-only `npm-distribution` links KMP JS once as ES modules to avoid incompatible symbol/runtime copies across independent links; `package-modules.mjs` splits 41 generated modules into dependency packages. Core/Koas/Fetch/plugin `assembleJsPackage` tasks consume these canonical packages. Fetch's public dependency graph shares Core/Koas/Kotlin runtime but contains no optional plugins; seven plugins expose named ES imports, and four Core plugin types expose named exports through shared declaration-only `aviator-js-types`.
- **Historical (copied-Playwright smoke, since replaced):** Shared npm verification passed under mise Java 25: root `mise exec -- .\gradlew.bat --no-daemon --console=plain :aviator:npm-distribution:assembleSharedNpmPackages :aviator:clients:fetch:assembleJsPackage :aviator:plugins:adapter-json:assembleJsPackage :aviator:plugins:weaver:assembleJsPackage -x :aviator:kotlinStorePackageLock`. Lock verification was excluded because `kotlin-js-store/package-lock.json` was already modified. In `npm-distribution`, `npm run verify:fetch` packed all 41 artifacts into a fresh isolated `build/verification/run-*` fixture without mutating monorepo pnpm `node_modules` or lock files; shared E2E TypeScript checking and two targeted Playwright tests passed (shared Core/no optional plugins in Fetch's graph; requests with and without JSON/Weaver activation). No live-browser/provider or registry publication verification was performed.
- 2026-10-23 — Current `npm-distribution` smoke is standalone `node:test` (`test/fetch-client.test.mjs`) plus TypeScript (`test/api-types.ts`); `manage-packages.mjs` no longer copies or relies on Shared/Playwright. After migration, `npm run verify:fetch` passed two Node tests and TypeScript checking. This verifies npm ABI/Fetch behavior, not Kotlin live-provider acceptance; Shared's dynamic JUnit 5 provider suite has separate results in `../areaassist/shared/AGENTS.md`. See the [updated npm guide](https://docs.mallne.cloud/doc/shared-npm-distribution-single-link-abi-and-fetch-verification-dAnv2NfYQ5).
- Packaging operations: Gradle `packJsPackage` remains blocked by `org.jetbrains.kotlin.npm-publish` expecting missing Windows `~/.gradle/nodejs/node-v24.16.0-win-x64/bin/node` and `bin/npm`; `npm run pack` succeeds using installed npm. Dependency-first `npm run publish:shared` is unexecuted and requires registry authentication plus a coherent same-version release. See [local packaging guide](npm-distribution/README.md) and [shared npm distribution guide](https://docs.mallne.cloud/doc/shared-npm-distribution-single-link-abi-and-fetch-verification-dAnv2NfYQ5); the external Aviator npm overview was also updated. YouTrack exposes read-only tools, so no feature ticket was created or updated.
