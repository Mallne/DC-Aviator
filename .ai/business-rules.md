# Business Rules -- Aviator

Rules an agent must respect when writing code for this module.

## API-First Design

- **Rule**: Every service definition must originate from an OpenAPI spec.
- **Why**: Aviator's core philosophy is spec-as-source-of-truth. Bypassing this breaks the framework contract.

## OpenAPI Extensions

- **Rule**: Use `x-dicentra-aviator-*` custom extensions only through `AviatorExtensionSpec` in `:core`.
- **Why**: Direct extension access bypasses validation and may produce invalid service mappings.

## Multiplatform Compatibility

- **Rule**: Code in `commonMain` must not use platform-specific APIs.
- **Why**: Aviator targets JVM, JS, Wasm, iOS, and Linux. Platform-specific code breaks cross-platform builds.

## Serialization

- **Rule**: Always use `kotlinx.serialization` for data modeling.
- **Why**: Consistent serialization across all platforms and content types.

## Internal API Stability

- **Rule**: Use `@InternalAviatorAPI` for any API that is not yet stable or intended for external use.
- **Why**: Prevents accidental dependency on unstable internals.

## Edge Cases

- **Mock client behavior**: Mock client returns canned responses -- it does not validate OpenAPI compliance at runtime. Use the Resource Server for integration testing.
- **Plugin ordering**: Plugins are applied in declaration order. Changing plugin registration order may change request/response behavior.

## Overrides

- None. Aviator follows root coding standards without module-specific overrides.
