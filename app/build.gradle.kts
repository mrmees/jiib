plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // PKG-02 control: registers the verifyMinSdk merged-manifest assertion task.
    // Precompiled script plugin from the build-logic included build
    // (build-logic/src/main/kotlin/verify-min-sdk.gradle.kts).
    id("verify-min-sdk")
}

android {
    namespace = "works.mees.dinghy"
    compileSdk = 35                                  // VERIFIED in-range for AGP 8.7

    defaultConfig {
        applicationId = "works.mees.dinghy"
        minSdk = 23                                  // PKG-02 floor (asserted on MERGED manifest by verifyMinSdk)
        targetSdk = 35                               // D-10/D-11 cleartext caveat applies on API 24+
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // D-01a: the Nexus 7 2013 is 32-bit ARMv7. The ABI split restricts the APK
    // to armeabi-v7a ONLY (asserted via `unzip -l | grep lib/`), so the release
    // APK can never carry an arm64/x86 slice the device can't run. (NOTE: do NOT
    // also set defaultConfig.ndk.abiFilters — AGP rejects the two together.)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true                   // R8 — the MEASURED artifact (D-03/D-07)
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    // The lintVital UAST detectors bundled with AGP 8.7 crash with an
    // IncompatibleClassChangeError under JDK 21 (the dev box's JDK). Lint is not
    // a deliverable of this scaffold phase and the crash is a tool/JDK
    // incompatibility, not a code defect — so do not let it gate assembleRelease.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // --- Compose (BOM governs versions; assert :app:dependencies shows ui:1.11.x) ---
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // --- AndroidX core / lifecycle / activity ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // --- Hybrid-Views head-to-head scene deps (scenes themselves land in 01-03) ---
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)

    // --- Networking / serialization (cleartext smoke test lands in 01-02) ---
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // --- Coil 3 (real PNG decode under memory pressure — used by 01-03 scenes) ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // --- Profile installer: NO-OP on the API-23 target (D-03); present for API 24+ devices ---
    implementation(libs.androidx.profileinstaller)

    // --- Instrumented test deps for the THROWAWAY cleartext smoke test (plan 01-02 / CONN-05) ---
    // androidTest extends the main `implementation` configuration, so OkHttp +
    // kotlinx.serialization (already declared above) are visible to the smoke test
    // without re-declaring them. Only the AndroidX test-runner stack is added here.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}
