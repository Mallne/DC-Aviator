# Commands & Environment -- Aviator

## Scripts

```bash
# Build the entire module
./gradlew build

# Build specific submodule
./gradlew :aviator-core:build
./gradlew :aviator-koas:build
./gradlew :aviator-ktor-client:build
./gradlew :aviator-mock-client:build

# Run the resource server (test server)
./gradlew :aviator-resource-server:run

# Run all tests
./gradlew :aviator-core:test
./gradlew test

# Update version catalog
./gradlew versionCatalogUpdate

# Check for dependency updates
./gradlew dependencyUpdates
```

## Local Dev Setup

1. Ensure JDK 17+ is installed
2. Run `./gradlew build` from the `aviator/` directory
3. Run `./gradlew :aviator-resource-server:run` to start the test server
4. Test endpoints against the running server

## Environment Variables

No environment variables required for basic development. The Resource Server uses defaults for local testing.

## Runtime Notes

- The `aviator-resource-server` is a reference/test server -- not for production use.
- Plugins in `:plugins:*` are optional; only include what your service needs.
