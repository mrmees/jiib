# Technology Stack

**Analysis Date:** 2026-06-08

## Languages

**Primary:**
- Kotlin 2.1.21 — all application code, build scripts, test code

**Secondary:**
- XML — Android resource files (`res/values/`, `res/drawable/`, `res/layout/` for Views-based surfaces)
- Kotlin DSL (`.gradle.kts`) — all Gradle build scripts including `build-logic/`

## Runtime

**Environment:**
- Android (minSdk 23 / Android 6.0 — Nexus 7 2013 floor, enforced on MERGED manifest via `verifyMinSdk` custom task)
- targetSdk 35 (Android 15)
- compileSdk 36 (bumped from 35 in Phase 21 — media3 1.10.1 AAR metadata hard-requires ≥36; non-fatal "max recommended 35" warn from AGP 8.7.0 accepted)
- ABI: release split to `armeabi-v7a` ONLY (`app/build.gradle.kts` `splits { abi }`) — Nexus 7 is 32-bit ARMv7

**JVM Target:**
- JVM 17 (`compileOptions sourceCompatibility/targetCompatibility`, `kotlinOptions jvmTarget`)

**JDK (dev box):**
- Adoptium JDK 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`)

**Package Manager:**
- Gradle 8.9 (wrapper in `gradlew` / `gradlew.bat`)
- Version Catalog: `gradle/libs.versions.toml` — single version source of truth; ALL versions pinned, no `+` dynamic versions
- Lockfile: present (wrapper checksum in `gradle/wrapper/gradle-wrapper.properties`)

## Frameworks

**Core UI:**
- Jetpack Compose BOM `2026.05.00` (STABLE channel) → Compose UI 1.11.1 — primary UI toolkit
- Material 3 Compose (`androidx.compose.material3`) — component library + theming base
- Compose Compiler: version == Kotlin 2.1.21 (managed via `org.jetbrains.kotlin.plugin.compose`; no separate pin since Kotlin 2.0+)

**Classic Views (Hybrid):**
- RecyclerView 1.3.2 — Files list, Console scrollback (measured ~2× lower p95 frame time on Adreno 320 vs Compose `LazyColumn`)
- Custom `Canvas`-drawn Views — live temperature graph
- Hosted in Compose via `AndroidView` / in Activities via `ComposeView`; ViewModels are toolkit-agnostic (`StateFlow` to both)
- Governed by ADR 0001 (`docs/adr/0001-ui-toolkit-decision.md`): Compose for shell + most panels; Views for the three high-churn surfaces

**Networking:**
- OkHttp 4.12.0 — single shared client for WebSocket + REST + MJPEG stream + snapshot polling (`MoonrakerSocket.defaultClient()`); minSdk 21
- Retrofit 2.11.0 — REST endpoints (file upload, simple GETs); uses `converter-kotlinx-serialization`

**Serialization:**
- kotlinx.serialization 1.7.3 (`kotlinx-serialization-json`) — all JSON including JSON-RPC envelopes; compile-time codegen, no reflection; lenient/ignoreUnknownKeys instance at `MoonrakerJson` (`net/JsonRpc.kt`)

**Reactive State:**
- kotlinx.coroutines 1.9.0 — coroutines + Flow; `StateFlow`/`SharedFlow` for printer state; `callbackFlow` bridge for OkHttp WebSocket → Flow

**Image Loading:**
- Coil 3.1.0 (`coil-compose` + `coil-network-okhttp`) — gcode thumbnails, Compose-native `AsyncImage`; minSdk 23 (exact floor match); fixed large-PNG OOM on API ≤23. Memory cache capped at 2 MB for the Files screen (`FileThumbnailLoader`)

**Camera / Streaming:**
- CameraX 1.5.0 (4 artifacts: `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) — QR code scanning in Spool screen; floor raised to exactly minSdk 23 in 1.5.0
- ZXing Core 3.3.3 — QR decode; pinned at 3.3.3 (3.4.0+ uses `List.sort` that NoSuchMethodErrors on API 23)
- media3-exoplayer 1.10.1 + media3-exoplayer-rtsp + media3-exoplayer-hls — native H.264 webcam feed (Phase 21); RTSP over TCP (forced, `setForceUseRtpTcp(true)`) is the recorded lead; HLS is the robustness fallback. Custom `SurfaceView` host (no `media3-ui`). minSdk 23 since 1.9.0; compileSdk 36 hard-required by 1.10.1 AAR metadata. An RTSP H.264 multi-RTP-packet corruption regression present in 1.9.x is FIXED in 1.10.0.

**Persistence:**
- DataStore Preferences 1.1.7 — all persistent settings; six independent `*.preferences_pb` files in `DinghyApp`: `theme`, `connection`, `macros`, `webcam`, `profiles`, `babystep`. Coroutine/Flow-native; no main-thread I/O risk. minSdk 23-safe.

**Fonts:**
- Geist (Regular/Medium/SemiBold/Bold) + Geist Mono (Medium/SemiBold) — bundled as static TTF in `res/font/`; static weights required because variable-font axis selection is API 26+, which would silently collapse to one weight on the API 23 floor
- Material Symbols Outlined — icon ligature font bundled as `res/font/material_symbols_outlined.ttf` (v2.944, verified by `fonttools`; used by `DinghyIcon`/`DinghyIcons` for ligature-driven icon rendering)

**Testing:**
- JUnit 4.13.2 — host unit tests and instrumented tests
- kotlinx-coroutines-test 1.9.0 — virtual time (`runTest`/`TestScope`) for coroutine unit tests
- Compose UI Test (`compose-ui-test-junit4`) — BOM-governed; drives `ShellPresenceTest`
- UiAutomator 2.3.0 — device rotation test (`ServiceSurvivesRotationTest`)
- Macrobenchmark 1.3.3 — `:macrobenchmark` module; measures startup/jank on the real Nexus 7 in release mode; Baseline Profile is a NO-OP on API 23 (full AOT at install); `compileSdk 35` in that module (not bumped)

## Key Dependencies

**Critical — minSdk audit items:**
- `activity 1.9.3`, `lifecycle 2.8.7` — AndroidX floor raised to 23 in June 2025; `<23` is NOT supported on these versions. This is why minSdk 23 is safe and also why it cannot be lowered.
- `coil 3.1.0` — minSdk 23 exactly; Coil 3 raised its floor from 21→23 and fixed the large-PNG OOM on API ≤23 that directly affects the 2 GB Nexus 7.
- `cameraX 1.5.0` — floor is exactly 23 (PROVEN by `verifyMinSdkRelease`); do NOT bump blindly.
- `media3 1.10.1` — floor 23 since 1.9.0 (PROVEN by `verifyMinSdkRelease`); requires compileSdk ≥36.
- `zxing-core 3.3.3` — do NOT bump to 3.4.0+ (Java-8 `List.sort` → `NoSuchMethodError` on API 23; `flox` runs API 30 so device tests would NOT catch this regression).
- `datastore 1.1.7` — June 2025 AndroidX baseline; minSdk 23-safe.

**Infrastructure:**
- `retrofit-converter-kotlinx-serialization 2.11.0` — Retrofit ↔ kotlinx.serialization bridge; no kapt/KSP step
- `androidx-profileinstaller 1.4.1` — included but is a NO-OP on API 23 (full AOT at install; only useful on API 24+ devices running the APK)
- `appcompat 1.7.0`, `constraintlayout 2.1.4` — Views hybrid; AppCompat for the Activity base in hybrid screens

## Configuration

**Environment:**
- Moonraker connection (host/port/optional API key) is stored at runtime via DataStore (`ConnectionStore` / `ProfileStore`)
- Static dev config read from gitignored `local.properties` → `BuildConfig` at build time (`MOONRAKER_HOST`, `MOONRAKER_PORT`, `MOONRAKER_API_KEY`); safe placeholder defaults allow fresh-clone compiles without `local.properties`
- Pseudolocales (`en-XA`, `ar-XB`) enabled on debug builds only for i18n completeness sweeps

**Build:**
- `app/build.gradle.kts` — app module (minSdk 23, compileSdk 36, targetSdk 35, ABI split armeabi-v7a)
- `gradle/libs.versions.toml` — single version catalog; ALL deps pinned
- `build-logic/src/main/kotlin/verify-min-sdk.gradle.kts` — custom precompiled script plugin; asserts the MERGED manifest minSdk never exceeds 23; registered as `id("verify-min-sdk")` in `:app`
- R8 minify + resource shrink enabled for release; ProGuard rules in `app/proguard-rules.pro`
- Lint: `checkReleaseBuilds = false`, `abortOnError = false` (AGP 8.7 + JDK 21 UAST crash workaround)

## Platform Requirements

**Development:**
- JDK 21 (Adoptium); Android SDK at `E:\Android\Sdk` (platform-tools, `platforms;android-35`, `build-tools;34.0.0`)
- Build must run Windows-side via `E:\Android\gw.bat` (not `./gradlew` from WSL — adb needs to reach the USB Nexus 7 via native Windows)
- Gradle wrapper at `gradlew` / `gradlew.bat`; build helper: `cmd /c "E:\Android\gw.bat <task>"`

**Production:**
- Sideloaded signed APK via GitHub Releases (no Play Store; target hardware lacks current Play Services)
- `armeabi-v7a` release APK, signed with the release keystore
- Debug builds auto-signed with the debug keystore; release builds use `sign-release.bat` until Phase 8 CI signing
- Target/floor device: Nexus 7 2013 (Adreno 320 / 2 GB / 1920×1200 / `armeabi-v7a`); on-device reality is LineageOS 18.1 / API 30, not stock Android 6

## What NOT to Use (enforced constraints)

| Avoid | Why |
|-------|-----|
| Any library requiring minSdk 24+ | Hard-breaks the Nexus 7 floor; `verifyMinSdkRelease` enforces this |
| Compose BOM alpha channel / 1.12 / compileSdk 37 / AGP 9 | Compose 1.12 requires compileSdk 37 + AGP 9 — stays on the 1.11/8.x line |
| Gson | Reflection-based, slow on weak CPU; use kotlinx.serialization |
| MPAndroidChart | Heavy, View-based, GC-churn; use custom Compose Canvas |
| Scarlet websocket | Unmaintained; use OkHttp websocket + thin JSON-RPC layer |
| WebRTC (`org.webrtc`) | Large, heavy on Adreno 320 / API 23; MJPEG/H.264 used instead |
| Google Play Services / Firebase | Target hardware lacks current Play Services; keep GMS-free |
| Hilt/Dagger | Overkill for v1; manual DI via `AppContainer` service-locator |
| Kotlin variable-font axes | API 26+; static TTF weights bundled in `res/font/` instead |
| zxing-core ≥ 3.4.0 | `List.sort` crash on API 23 |

---

*Stack analysis: 2026-06-08*
