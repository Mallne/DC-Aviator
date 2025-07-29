import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "cloud.mallne.dicentra.aviator"
version = "1.0.0-SNAPSHOT"
description = "The core compponents of DiCentra Aviator."

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.http)
                //Dependency Substitution does not work in KMP for whatever Reason
                implementation(libs.dc.polyfill)
                api(project(":koas"))
            }
        }
    }
}

android {
    namespace = "cloud.mallne.dicentra.aviator"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

mavenPublishing {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()

                pom {
                    name = "DiCentra Aviator Core"
                    description =
                        "Essential Interfaces and Methods for DiCentra Aviator"
                    inceptionYear = "2025"
                    developers {
                        developer {
                            name = "Mallne"
                            url = "mallne.cloud"
                        }
                    }
                }
                repositories {
                    maven {
                        url = uri("https://registry.mallne.cloud/repository/DiCentraArtefacts/")
                        credentials {
                            username = properties["dc.username"] as String?
                            password = properties["dc.password"] as String?
                        }
                    }
                }
            }
        }
    }

    signAllPublications()

    coordinates(group.toString(), "library", version.toString())
}