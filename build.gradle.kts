plugins {
    id("maven-publish")
    alias(libs.plugins.kjvm) apply false
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}