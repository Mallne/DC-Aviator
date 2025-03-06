rootProject.name = "Aviator"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":clients:aviator-ktor")
include(":plugins:translation-keys")
include(":plugins:interception")
include(":aviator-resource-server")
include(":core")
include(":koas")
val apiDest = file("../polyfill/library")
if (apiDest.exists()) {
    include("polyfill")
    project(":polyfill").projectDir = apiDest
} else {
    println("This Project seems to be running without the Monorepo Context, please consider using the Monorepo")
}