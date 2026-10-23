import nl.littlerobots.vcu.plugin.resolver.VersionSelectors
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

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

allprojects {
    plugins.withType<NodeJsPlugin> {
        extensions.configure<NodeJsEnvSpec> {
            download = false
        }
    }
}

versionCatalogUpdate {
    versionSelector(VersionSelectors.STABLE)
}

// Keep existing public package/publish tasks on the shared linker output as well.
// Fetch still has no optional-plugin dependency: this is only the release build graph.
val sharedNpmModules = mapOf(
    ":core" to "aviator-core",
    ":koas" to "aviator-koas",
    ":clients:fetch" to "aviator-client-fetch",
) + listOf("adapter-json", "adapter-xml", "http-auth", "interception", "otel", "synapse", "weaver")
    .associate { ":plugins:$it" to "aviator-plugin-$it" }

subprojects {
    val npmName = sharedNpmModules[path] ?: return@subprojects
    tasks.matching { it.name == "assembleJsPackage" }.configureEach {
        dependsOn(":npm-distribution:assembleSharedNpmPackages")
        val sharedPackage = rootProject.layout.projectDirectory.dir("npm-distribution/build/packages/$npmName")
        inputs.dir(sharedPackage)
        doLast {
            sync {
                from(sharedPackage)
                into(layout.buildDirectory.dir("packages/js"))
            }
        }
    }
}
