// Included build hosting the project's convention/precompiled script plugins.
// Lives in its own build so its plugins can be compiled against the AGP Variant
// API (com.android.build.api.*) — something a plain apply(from=) script cannot
// do. The root build pulls this in via `includeBuild("build-logic")`.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
