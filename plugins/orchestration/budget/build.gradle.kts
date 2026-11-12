group = "cloud.mallne.dicentra.aviator.plugin"
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"
description = "Optional non-byte execution-budget plugin for Aviator orchestration."

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
}

mavenPublishing {
    coordinates(group.toString(), "orchestration-budget")
}

kotlin {
    jvm()
    android {
        namespace = "${project.group}.orchestration.budget"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    js {
        nodejs()
        browser()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":orchestration:core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
    jvmToolchain(25)
}
