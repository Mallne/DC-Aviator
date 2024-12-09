plugins {
    id("maven-publish")
    kotlin("jvm") version "2.1.0"
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}