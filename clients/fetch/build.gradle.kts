group = "cloud.mallne.dicentra.aviator.client"
description = "Aviator client backed by the JavaScript platform's native Fetch API."
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.npm.publish)
}

kotlin {
    js {
        nodejs()
        browser()
        binaries.library()
    }

    sourceSets {
        jsMain.dependencies {
            api(project(":core"))
            api(project(":koas"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.dc.polyfill)
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":plugins:adapter-json"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

npmPublish {
    organization = "dicentra"
    registries {
        register("nodeRegistry") {
            uri.set("https://registry.mallne.cloud/repository/NodeRegistry/")
            auth.set(providers.environmentVariable("NPM_TOKEN").orNull)
        }
    }
    packages {
        named("js") {
            packageName = "aviator-client-fetch"
            version = project.version.toString().replace("-SNAPSHOT", "-snapshot")
        }
    }
}
