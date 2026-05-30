pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // Version catalog is the single source of versions (PKG-02). Gradle picks up
    // gradle/libs.versions.toml automatically as the default `libs` catalog.
}

rootProject.name = "dinghy-display"

include(":app")
include(":macrobenchmark")
