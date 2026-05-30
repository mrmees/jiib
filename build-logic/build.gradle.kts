// The build-logic module compiles `*.gradle.kts` files under
// src/main/kotlin as PRECOMPILED SCRIPT PLUGINS. The kotlin-dsl plugin gives
// them the Gradle Kotlin DSL accessors AND lets them depend on AGP, so the
// verifyMinSdk plugin can use the typed Variant API (SingleArtifact.MERGED_MANIFEST).
plugins {
    `kotlin-dsl`
}

dependencies {
    // AGP on the plugin compile classpath → the precompiled script plugin can
    // import com.android.build.api.* (Variant API). Version from the catalog.
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
}
