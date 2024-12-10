group = "cloud.mallne.dicentra.aviator"
version = "1.0.0-SNAPSHOT"
description = "DiCentra Aviator preconfigured for Spring Reactive."

plugins {
    id("maven-publish")
    alias(libs.plugins.kjvm)
    alias(libs.plugins.kotlin.serialization)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
            pom {
                name = "DiCentra Aviator Core"
                description = project.description
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

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(project(":core"))
}