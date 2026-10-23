import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    alias(libs.plugins.kmp)
}

version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"

// Link once, then publish each generated module as its own npm dependency. Independent
// Kotlin/JS links assign different internal symbols, even for identical Core/runtime code.
kotlin {
    js {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions.moduleKind.set(JsModuleKind.MODULE_ES)
    }
    sourceSets.jsMain.dependencies {
        api(project(":core"))
        api(project(":koas"))
        api(project(":clients:fetch"))
        api(project(":plugins:adapter-json"))
        api(project(":plugins:adapter-xml"))
        api(project(":plugins:http-auth"))
        api(project(":plugins:interception"))
        api(project(":plugins:otel"))
        api(project(":plugins:synapse"))
        api(project(":plugins:weaver"))
    }
}

tasks.register<Exec>("assembleSharedNpmPackages") {
    dependsOn("jsProductionLibraryCompileSync")
    inputs.dir(layout.buildDirectory.dir("compileSync/js/main/productionLibrary/kotlin"))
    inputs.file("package-modules.mjs")
    inputs.property("distributionVersion", project.version.toString())
    outputs.dir(layout.buildDirectory.dir("packages"))
    commandLine("node", "package-modules.mjs", project.version.toString().replace("-SNAPSHOT", "-snapshot"))
}
