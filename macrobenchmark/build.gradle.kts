// :macrobenchmark — MEASUREMENT module (D-08).
//
// Justified by measurement, NOT by baseline profiles (Baseline Profiles are a
// no-op on the API-23 target — D-03). This module hosts the MacrobenchmarkRule +
// UiAutomator script + FrameTimingMetric harness that drive :app's RELEASE
// variant on the real Nexus 7. The authoritative on-device metric is gfxinfo
// framestats CSV (captured/parsed in plan 01-03); FrameTimingMetric corroborates.
//
// This plan (01-01) only stands the module up so `:app:assembleRelease`,
// `./gradlew projects`, and the benchmark wiring exist. The actual benchmark
// test classes + gfxinfo capture land in plan 01-03.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "works.mees.jiib.macrobenchmark"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        // Macrobenchmark library minSdk == 23 (VERIFIED) — runs on the target.
        minSdk = 23
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Benchmarks must run against a non-debuggable build of :app. We benchmark
    // the RELEASE variant (the measured, shipped artifact — D-03/D-07).
    buildTypes {
        // Inherit from :app's variants; only the release path is benchmarked.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        // A `release` build type so `:macrobenchmark:assembleRelease` exists and compiles
        // ToolkitBenchmark against the :app RELEASE variant (the measured artifact —
        // D-03/D-07). A com.android.test module declares no `release` by default, so the
        // plan's release-variant verify needs this. Debuggable + debug-signed because the
        // Phase-1 :app release APK is still unsigned (real signing is PKG-01 / Phase 8);
        // it still consumes :app's release output via the matching fallback.
        create("release") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // This is a com.android.test module measuring the :app under test.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.junit)
}
