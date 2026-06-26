pluginManagement {
    // Convention/precompiled script plugins (verify-min-sdk → PKG-02) live here.
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// R5a: first-party Gradle toolchain resolver — auto-provisions the JDK-17 toolchain
// that app/build.gradle.kts pins via kotlin { jvmToolchain(17) } (the dev box runs
// JDK 21; the JDK-17 toolchain is what keeps AGP-8.7 lint off its JDK-21 UAST crash).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // Version catalog is the single source of versions (PKG-02). Gradle picks up
    // gradle/libs.versions.toml automatically as the default `libs` catalog.
}

rootProject.name = "jiib"

include(":app")
include(":macrobenchmark")
