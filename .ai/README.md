# `.ai/` -- Agent Documentation for Aviator

This folder is the AI-agent onboarding guide for the Aviator submodule.

## Prerequisites

Read these root `.ai/` articles first:

- `../../.ai/coding-standards.md` -- shared lint/format conventions
- `../../.ai/testing-guidelines.md` -- shared test strategy
- `../../.ai/glossary.md` -- shared domain terms

## Index

| Article | Contents |
|---------|----------|
| [architecture.md](architecture.md) | Module structure, data flow, API surface |
| [business-rules.md](business-rules.md) | Module-specific invariants and edge cases |
| [commands.md](commands.md) | Scripts, env vars, local dev setup |

## Published Documentation

Full documentation is published in the DiCentra collection:

- [Aviator Hub](https://docs.mallne.cloud/doc/aviator-RdNz6f71NO)
- [Core Framework](https://docs.mallne.cloud/doc/core-framework-a6bcINJOwY)
- [KOAS](https://docs.mallne.cloud/doc/koas-openapi-specification-model-WiZ6yZLdXV)
- [Ktor Client](https://docs.mallne.cloud/doc/ktor-client-fxIUjBdbe2)
- [Mock Client](https://docs.mallne.cloud/doc/mock-client-sF8hqYkIQN)
- [Plugins](https://docs.mallne.cloud/doc/plugins-ucXpKbmw4T)
- [Resource Server](https://docs.mallne.cloud/doc/resource-server-lNvQj1ixfv)

## Maintenance

- Update the relevant article on **every** edit that touches architecture, structure, commands, or rules.
- Whenever you discover an **inconsistency** between the docs and the code, fix it here.
- Do not duplicate content that lives in `../../.ai/` -- reference it instead.
