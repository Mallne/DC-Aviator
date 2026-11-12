group = "cloud.mallne.dicentra.aviator.orchestration"
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"
description = "Arazzo workflow orchestration for Aviator."

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenPublish)
}

mavenPublishing {
    coordinates(group.toString(), project.name)
}

kotlin {
    jvm()
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
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":clients:mock"))
            implementation(project(":plugins:orchestration:budget"))
            implementation(project(":plugins:orchestration:authorization"))
        }
        jvmTest.dependencies {
            implementation(project(":clients:ktor"))
            implementation(project(":plugins:adapter-json"))
            implementation(project(":plugins:orchestration:otel"))
            implementation(libs.opentelemetry.kotlin.api)
            implementation(libs.ktor.client.mock)
            implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.asProvider().get()}")
        }
        jsTest.dependencies {
            implementation(project(":clients:fetch"))
            implementation(project(":plugins:adapter-json"))
        }
    }
    jvmToolchain(25)
}

abstract class CompileTestClasspathArguments : CommandLineArgumentProvider {
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> =
        listOf("-Dorchestration.compileTest.classpath=${classpath.asPath}")
}

tasks.named<Test>("jvmTest") {
    val compileTestArguments = objects.newInstance<CompileTestClasspathArguments>()
    compileTestArguments.classpath.from(classpath)
    jvmArgumentProviders.add(compileTestArguments)
}
