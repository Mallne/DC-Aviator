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
