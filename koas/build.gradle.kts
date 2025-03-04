import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kotlin.serialization)
}

group = "cloud.mallne.dicentra.aviator"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
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
                if (findProject(":polyfill") != null) {
                    implementation(project(":polyfill"))
                } else {
                    implementation(libs.dc.polyfill)
                }
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
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
                    name = "Dicentra Aviator Koas"
                    description =
                        "An Kotlin Multiplatform OpenAPI specification and parser utilizing kotlinx.serialization"
                    inceptionYear = "2024"
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
