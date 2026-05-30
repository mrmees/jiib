// Root build file. Declares plugins (apply false) from the version catalog so
// each module applies the same pinned versions (PKG-02). No allprojects/
// subprojects repository blocks — repositories live in settings.gradle.kts
// (dependencyResolutionManagement, FAIL_ON_PROJECT_REPOS).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
