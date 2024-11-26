group = "cloud.mallne.dicentra.aviator"
version = "1.0.0-SNAPSHOT"
description = "The core compponents of DiCentra Aviator."

plugins {
    id("maven-publish")
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
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