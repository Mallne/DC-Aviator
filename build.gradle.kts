plugins {
    id("maven-publish")
    alias(libs.plugins.kjvm) apply false
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

configurations {
    all {
        resolutionStrategy.dependencySubstitution {
            if (findProject(":polyfill") != null) {
                substitute(module(libs.dc.polyfill.get().toString())).using(project(":polyfill"))
                    .because("The Project is opened in the Monorepo, so we can use the local Library")
            }
        }
    }
}