import nl.littlerobots.vcu.plugin.resolver.VersionSelectors

plugins {
    id("maven-publish")
    alias(libs.plugins.kjvm) apply false
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.ben.manes.versions)
}

versionCatalogUpdate {
    versionSelector(VersionSelectors.STABLE)
}