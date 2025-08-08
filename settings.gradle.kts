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

include(":clients:ktor")
include(":plugins:translation-keys")
include(":plugins:interception")
include(":plugins:weaver")
include(":plugins:synapse")
include(":aviator-resource-server")
include(":core")
include(":koas")
val apiDest = file("../polyfill")
if (apiDest.exists()) {
    includeBuild(apiDest.absolutePath) {
        dependencySubstitution {
            substitute(module("cloud.mallne.dicentra:polyfill")).using(project(":library"))
        }
    }
} else {
    println("[AVIATOR:polyfill] This Project seems to be running without the Monorepo Context, please consider using the Monorepo")
}

val weaverDest = file("../weaver")
if (weaverDest.exists()) {
    includeBuild(weaverDest.absolutePath) {
        dependencySubstitution {
            substitute(module("cloud.mallne.dicentra.weaver:core")).using(project(":core"))
            substitute(module("cloud.mallne.dicentra.weaver:tokenizer")).using(project(":tokenizer"))
        }
    }
} else {
    println("[AVIATOR:weaver] This Project seems to be running without the Monorepo Context, please consider using the Monorepo")
}