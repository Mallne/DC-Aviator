# Commands & Environment -- Aviator

## Scripts

```bash
# Build the entire module
./gradlew build

# Build specific submodule
./gradlew :core:build
./gradlew :koas:build
./gradlew :clients:ktor:build
./gradlew :clients:mock:build

# Run all tests
./gradlew :core:test
./gradlew test

# Update version catalog
./gradlew versionCatalogUpdate

# Check for dependency updates
./gradlew dependencyUpdates
```

## Local Dev Setup

1. Ensure JDK 25+ is installed
2. Run `./gradlew build` from the `aviator/` directory

## Runtime Notes

- Plugins in `:plugins:*` are optional; only include what your service needs.
