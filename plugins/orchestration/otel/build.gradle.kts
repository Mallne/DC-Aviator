group = "cloud.mallne.dicentra.aviator.plugin"
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"
description = "Optional OpenTelemetry tracing plugin for Aviator orchestration."

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
}

mavenPublishing { coordinates(group.toString(), "orchestration-otel") }

kotlin {
    jvm()
    android {
        namespace = "${project.group}.orchestration.otel"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    js { nodejs(); browser() }
    iosArm64(); iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":orchestration:core"))
            api(libs.opentelemetry.kotlin.api)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
    jvmToolchain(25)
}
