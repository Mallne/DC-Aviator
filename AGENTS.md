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
