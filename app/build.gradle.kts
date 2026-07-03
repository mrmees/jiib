import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

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

// D-05: static dev config. Host/port/optional API key are read from a GITIGNORED
// `local.properties` (see .gitignore) into BuildConfig at build time — nothing secret
// is committed. SAFE PLACEHOLDER defaults below let a fresh clone with no local.properties
// still compile. To point at your printer: add `moonraker.host=<ip>` etc. to local.properties
// then rebuild (BuildConfig regenerates each build). NEVER commit a real IP/key here.
val devProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun devProp(key: String, default: String): String =
    (devProps.getProperty(key) ?: default)

// Versioning scheme (owner-defined 2026-07-03): <CANDIDATE>-<yy>.<m>.<d>.<n>
//   CANDIDATE = release track (ALPHA for now; BETA/stable later),
//   yy.m.d    = release date, no zero padding (26.7.3 = 2026-07-03),
//   n         = Nth release on that date, 1-based. Keep n ≤ 9.
// Bump these MANUALLY per release — never auto-derive from the build clock (reproducibility).
val versionCandidate = "ALPHA"
val versionYear = 26
val versionMonth = 7
val versionDay = 3
val versionRelease = 1

val appVersionName = "$versionCandidate-$versionYear.$versionMonth.$versionDay.$versionRelease"

// R1 (26.5-04): the SINGLE versionCode base. defaultConfig reads it, and the per-ABI
// onVariants block below derives each split's versionCode from it (v7a = base, arm64 = base+1)
// — never hardcode a stale copy of this value anywhere else.
// Derived as yymmdd·100 + n·10 (e.g. ALPHA-26.7.3.1 → 26070310): monotonic across dates and
// same-day re-releases, and the ×10 gap between releases guarantees the per-ABI +0/+1 offsets
// can never collide with a neighboring release. Max value 99123199 is well under the 2.1B cap.
val appVersionCode = (versionYear * 10_000 + versionMonth * 100 + versionDay) * 100 + versionRelease * 10

android {
    namespace = "works.mees.jiib"
    compileSdk = 36                                  // bumped 35→36 (Phase 21): media3 1.10.x AAR metadata
                                                     // MANDATES compileSdkVersion >= 36. AGP 8.7.0 accepts it
                                                     // (warns "max recommended 35" — non-fatal); merged-manifest
                                                     // minSdk floor stays 23 (PROVEN by verifyMinSdkRelease).

    defaultConfig {
        applicationId = "works.mees.jiib"
        minSdk = 23                                  // PKG-02 floor (asserted on MERGED manifest by verifyMinSdk)
        targetSdk = 35                               // D-10/D-11 cleartext caveat applies on API 24+
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // D-05 static config → BuildConfig (read from gitignored local.properties; placeholders only).
        // Strings are emitted as quoted literals so DevConfig can read them directly.
        buildConfigField("String", "MOONRAKER_HOST", "\"${devProp("moonraker.host", "192.168.1.50")}\"")
        buildConfigField("String", "MOONRAKER_PORT", "\"${devProp("moonraker.port", "7125")}\"")
        buildConfigField("String", "MOONRAKER_API_KEY", "\"${devProp("moonraker.apiKey", "")}\"")
    }

    // D-01a (amended R1, 26.5-04): per-ABI APK splits, no universal APK. The Nexus 7 2013
    // (32-bit ARMv7 dev FLOOR) installs the armeabi-v7a APK; ship artifacts publish BOTH
    // per-ABI APKs so 64-bit-only devices (Pixel 7+, S25 Ultra) can install the arm64-v8a
    // one. Each split carries only its own native slice (asserted via `unzip -l | grep lib/`).
    // (NOTE: do NOT also set defaultConfig.ndk.abiFilters — AGP rejects the two together.)
    // Per-ABI versionCodes are assigned in the androidComponents.onVariants block below.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
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
        debug {
            // SC-3c (Phase 18): generate the en-XA (accented) + ar-XB (RTL+bracketed) pseudolocales
            // on the DEBUG build only. Switching the device/emulator system locale to "English (XA)"
            // renders every resource-sourced string accented+padded; any plain-English text that
            // shows through is a still-hardcoded literal — the i18n completeness sweep. Build-time
            // capability only (no translations shipped); the project has no resConfigs to trim.
            isPseudoLocalesEnabled = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true   // D-05: emit BuildConfig with the MOONRAKER_* static-config fields
    }

    // R5a (§R5a step 4): lint baselined and enforced. The AGP-8.7 UAST crash
    // (IncompatibleClassChangeError) is JDK-21-specific. REALITY CHECK (26.5-01 + codex
    // review): jvmToolchain(17) + runLintInProcess=false do NOT fully repin the lint worker
    // locally — it follows the daemon JVM (JDK 21 under gw.bat). The local workaround is the
    // NullSafeMutableLiveData disable below (the only detector that crashes); CI runs on a
    // JDK-17 runner where the hazard doesn't exist and lintDebug enforces the baseline there.
    lint {
        baseline = file("lint-baseline.xml")
        // NullSafeMutableLiveData's detector (androidx.lifecycle lint) throws
        // IncompatibleClassChangeError under this AGP-8.7/JDK combo even with the JDK-17
        // toolchain (the lint worker follows the daemon JVM). The app has ZERO LiveData
        // (StateFlow/Flow everywhere), so the check is inert here — disabled to unblock
        // the whole lint run rather than gating on a detector we can never trip.
        disable += "NullSafeMutableLiveData"
        // HOUSE RULE: the baseline may only shrink, never grow — never re-run
        // updateLintBaseline without reviewing what new violations it would absorb.
        // Any violation not in the baseline fails the build (abortOnError).
        checkReleaseBuilds = true
        abortOnError = true
    }

    // R10 (26.5-03): the dispatcher's debug rejection instrumentation calls android.util.Log at
    // its early returns; host unit tests exercise those exact paths (busy/debounce tests), so
    // unmocked android.jar methods must return defaults instead of throwing "not mocked".
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// R5a: TOP-LEVEL KotlinAndroidProjectExtension (NOT inside android{} — that is a build error).
// Pins the Kotlin/lint worker JVM to a JDK-17 toolchain; compileOptions/kotlinOptions above
// already target 17 and stay.
kotlin {
    jvmToolchain(17)
}

// R1 (26.5-04): distinct versionCode per ABI split so the two release APKs never collide
// in a package manager (Pitfall 3): armeabi-v7a = base, arm64-v8a = base+1. Uses the modern
// variant API (androidComponents.onVariants), NOT the legacy applicationVariants.all.
// AGP 8.7: output.versionCode is a Property<Int> — it MUST be .set(), plain assignment
// does not compile. Base is the single `appVersionCode` hoisted above defaultConfig.
androidComponents {
    // Scoped to release (codex review LOW): only the published split artifacts need the
    // offsets; debug installs keep the plain base versionCode (no metadata churn).
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            val offset = when (abi) {
                "armeabi-v7a" -> 0
                "arm64-v8a" -> 1
                else -> 0   // no ABI filter (shouldn't happen with isUniversalApk=false)
            }
            output.versionCode.set(appVersionCode + offset)
        }
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
    // SC-4a (Phase 18, RESEARCH Q1 — VERIFIED leave-as-is): the -preview artifact MUST stay
    // `implementation`, NOT `debugImplementation`. The @Preview / @PreviewParameter annotations
    // it provides are referenced by @Preview functions that live in the `main` sourceset
    // (alongside the screens they document — there is no separate debug/ Kotlin sourceset),
    // so the annotation symbols must be on main's compile classpath. Moving this to
    // debugImplementation makes those @Preview references unresolved and breaks the module compile.
    // It is an inert annotations-only jar (no runtime behavior) that R8 strips from release as
    // unreachable, so "it ships in release" is harmless. DO NOT "optimize" this to debugImplementation.
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // --- AndroidX core / lifecycle / activity ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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
    implementation(libs.kotlinx.collections.immutable)   // D-02: ImmutableList/Map stable Compose params

    // --- Coil 3 (real PNG decode under memory pressure — used by 01-03 scenes) ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // --- Camera + QR decode (SPOOL-05/06, Phase 11) ---
    // CameraX 1.5.0 (floor == 23, PROVEN by verifyMinSdkRelease) + canonical zxing:core 3.3.3
    // (NOT 3.4.0+ — see libs.versions.toml zxingCore comment; no coreLibraryDesugaring needed).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    // --- Media3 / ExoPlayer H.264 transport (CAM-11/13/14/16, Phase 21 — D-01/D-09) ---
    // media3 >=1.10.1 (floor == 23, PROVEN by verifyMinSdkRelease); the RTSP multi-RTP-packet H.264
    // access-unit fix is PRESENT (a 1.9.x regression fixed in 1.10.0 — confirm in the resolved
    // artifact, don't trust the version string). Raw SurfaceView host — NO media3-ui (footprint
    // discipline). -hls is in for the spike (plan 02 measures both transports). Google Maven only.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.exoplayer.hls)

    // --- Navigation-Compose (Phase 24, D-01 nav-spine): type-safe NavHost back-stack routing ---
    // 2.8.9 — NOT BOM-governed; 2.8.x is minSdk-21-safe and stays on the AGP-8.7/compileSdk-36 line.
    // No navigation-testing added — the D-04 pop-to-root test is a PURE JVM predicate (FIX-8; no TestNavController).
    implementation(libs.androidx.navigation.compose)

    // --- Persistence (THEME-02/D-02): S/M/L --fs + theme base + custom token deltas ---
    implementation(libs.androidx.datastore.preferences)

    // --- Profile installer: NO-OP on the API-23 target (D-03); present for API 24+ devices ---
    implementation(libs.androidx.profileinstaller)

    // --- Instrumented test deps for the THROWAWAY cleartext smoke test (plan 01-02 / CONN-05) ---
    // androidTest extends the main `implementation` configuration, so OkHttp +
    // kotlinx.serialization (already declared above) are visible to the smoke test
    // without re-declaring them. Only the AndroidX test-runner stack is added here.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    // UiAutomator drives device rotation for the 04-03 service-survival gate (ServiceSurvivesRotationTest).
    // ApplicationProvider (for resolving the process JiibApp/AppContainer) comes transitively via ext.junit's androidx.test:core.
    androidTestImplementation(libs.androidx.test.uiautomator)

    // --- Compose UI test (04-07 ShellPresenceTest) — BOM-governed; the test composes RootController
    //     under createComposeRule and drives the drawer/routing/splash assertions. ui-test-manifest
    //     supplies the empty test Activity createComposeRule launches into (debug source set). ---
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // --- JVM unit-test source set (Wave 0): the off-hardware proving ground for the spine ---
    // kotlinx-coroutines-test gives virtual time (runTest) to Wave 2/3 reducer/correlation/reconnect
    // tests; kotlinx-serialization-json lets unit tests + fixtures parse golden Moonraker frames.
    // No RUNTIME dep is added here, so the merged-manifest minSdk-23 floor cannot move (verifyMinSdk).
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}
