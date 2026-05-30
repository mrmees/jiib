# Stack Research

**Domain:** Native Android Moonraker/Klipper control surface ("KlipperScreen-equivalent") for old hardware (Nexus 7 2013, API 23, 2GB RAM, Tegra-era GPU)
**Researched:** 2026-05-29
**Confidence:** HIGH (the API-23 floor turned out to be the *new* AndroidX baseline, which de-risks the entire modern stack)

---

## TL;DR — The Prescriptive Stack

| Decision | Verdict | Confidence |
|----------|---------|------------|
| **UI toolkit** | **Jetpack Compose** (with a Baseline Profile). Not classic Views. | HIGH |
| **Language** | **Kotlin** | HIGH |
| **WebSocket + REST** | **OkHttp** (websocket) + **Retrofit** (REST), both Square, one client | HIGH |
| **JSON / JSON-RPC** | **kotlinx.serialization** | HIGH |
| **Reactive state** | **Coroutines + Flow** (`StateFlow`/`SharedFlow`), `callbackFlow` bridge for the socket | HIGH |
| **Image / thumbnails** | **Coil 3** (Compose-native, minSdk 23 exactly) | HIGH |
| **Persistence (settings)** | **DataStore (Preferences)** | HIGH |
| **Charting (temp history)** | **Custom Compose `Canvas`** for live temp graphs; **Vico** only if you want full axis/legend chrome | MEDIUM |
| **Camera (later phase)** | OkHttp multipart MJPEG decode → Compose `Image`; defer WebRTC | MEDIUM |
| **Build** | minSdk 23 / targetSdk 35 / compileSdk 35, AGP 8.7.x, Kotlin 2.1.x, **pin a known-good Compose BOM** | HIGH |

**The headline finding that settles the whole stack:** As of **June 2025, AndroidX moved its default `minSdk` from 21 to 23** (Activity, Lifecycle, AppCompat, Compose Material, Coil 3, etc.). Your `minSdk 23` floor is *exactly the new modern baseline* — you are not below it. This means the entire current Compose-first stack is in-bounds. The trap to avoid is the opposite of what you'd fear: you don't need to use ancient libraries; you need to **pin versions and not chase the bleeding edge** that's already starting to require `compileSdk 37` / AGP 9.

---

## The Big Decision: Compose vs Views on Tegra-era / 2GB / API 23

**Recommendation: Jetpack Compose, with a Baseline Profile. Confidence: HIGH.**

This is counterintuitive for weak hardware, so here's the concrete reasoning rather than a hedge:

**1. Jank parity is solved as of Compose 1.9.**
Google's own scrolling benchmarks show Compose and Views have the **same jank rate since Compose 1.9.0** (~0.2% janky frames on a long-scroll benchmark). The "Compose is slow" reputation comes from 2021–2023 (Compose 1.0–1.3) and is stale. You'll be on Compose 1.11+ (BOM 2026.05). Recent releases added **pausable composition** (enabled by default — the runtime can split heavy composition across frames) and lazy-layout prefetch, both of which specifically target jank on slow CPUs.

**2. Your UI is the *good* case for Compose, not the bad case.**
The jank horror stories are about deeply nested, massively recomposing lists and complex animations. A printer control surface is the opposite: mostly static panels (jog grid, heater rows, button banks) with a handful of frequently-updating values (temps, progress, position). With correct state hoisting and `derivedStateOf`, only the text that changed recomposes — recomposition is granular, not whole-screen. The file browser is the one `LazyColumn`/`LazyVerticalGrid`, and lazy layouts are exactly what Compose 1.9+ benchmarks optimized.

**3. The startup cost is real but mitigable.**
The one genuine Compose downside on old hardware: Compose is an *unbundled* library, so unlike Views it doesn't get preloaded by Zygote, and on API 23 there's no class preloading benefit. Cold start is heavier. **Mitigation: ship a Baseline Profile** (`androidx.profileinstaller`). It pre-compiles the hot startup/scroll paths AOT, cutting interpretation/JIT cost — Google measures ~30% faster execution on covered paths from first launch. For a kiosk-style printer screen that launches once and stays open, startup is a minor, one-time cost anyway. This is an always-on dashboard, not an app the user cold-launches 40 times a day.

**4. Memory: a wash, and you control it.**
On a 2GB device the killer is bitmaps, not the UI framework. Compose's own runtime overhead is modest and dwarfed by your gcode thumbnails. Coil with proper sizing (see below) is where memory is won or lost, not Compose-vs-Views.

**5. Productivity and maintainability strongly favor Compose.**
Capability-gating (show/hide panels based on what the printer reports), dynamic macro-parameter forms, and live state updates are *dramatically* less code in Compose than in `View`/`XML` + findViewById/adapters/observers. For a solo-maintained project replicating ~34 panels eventually, this matters enormously. Classic Views would mean hand-writing `RecyclerView` adapters, `ConstraintLayout` XML, and manual view-state sync for every panel.

**When Views would win (and why it doesn't apply here):** Views win if you must support API <21, need absolute-minimum cold start with zero baseline-profile tooling, or are porting an existing XML codebase. None apply — this is greenfield, API 23 floor, kiosk usage.

**Non-negotiable Compose performance rules for this hardware** (put these in the architecture/pitfalls docs):
- Ship a **Baseline Profile** from day one.
- Build/profile in **release mode** — debug Compose is 5–10× slower and will lie to you about jank.
- Hoist state; use `StateFlow` + `collectAsStateWithLifecycle()`; wrap derived values in `derivedStateOf`.
- Keep socket-driven state objects `@Stable`/`@Immutable` so Compose can skip.
- Keys on `LazyColumn`/`LazyVerticalGrid` items; never read a rapidly-changing value (temps) higher in the tree than necessary.

---

## Recommended Stack (detail)

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Kotlin** | 2.1.x (e.g. 2.1.21) | Language | First-class coroutines/Flow, null-safety, kotlinx.serialization compiler plugin, the entire modern Android ecosystem is Kotlin-first. No reason to use Java. |
| **Jetpack Compose (BOM)** | `2026.05.00` (pin it) → Compose UI 1.11.x | UI toolkit | Jank parity with Views since 1.9; far less code for dynamic/gated panels; minSdk 21 (you're at 23). See big-decision section. |
| **Compose Compiler plugin** | matches Kotlin (2.0+ managed with Kotlin) | Compiles Compose | Since Kotlin 2.0 the compiler version == Kotlin version; apply `org.jetbrains.kotlin.plugin.compose`. |
| **AndroidX Activity-Compose / Lifecycle** | Activity 1.9.x, Lifecycle 2.8.x | Host, lifecycle-aware collection | `collectAsStateWithLifecycle()`, `ComponentActivity`. **These are the libs that moved to minSdk 23 in June 2025 — 23 is supported, <23 is not.** |
| **OkHttp** | 4.12.x | WebSocket (Moonraker JSON-RPC push) **and** the HTTP engine under Retrofit | Battle-tested websocket since 2016, one connection pool / TLS stack for both socket and REST, trivially wrapped in `callbackFlow`. minSdk 21. |
| **Retrofit** | 2.11.x | Moonraker REST endpoints (file upload, simple GETs) | Pairs with OkHttp + a kotlinx.serialization converter; clean typed REST without hand-rolling. |
| **kotlinx.serialization** | 1.7.x (`json`) | All JSON incl. JSON-RPC envelopes | Compile-time codegen (fastest, lightest, no reflection — good on weak CPU), polymorphic/`JsonElement` handling is ideal for Moonraker's `notify_*` heterogeneous params and partial `objects/subscribe` deltas. Kotlin-native, no annotation-processor build step. |
| **Coroutines + Flow** | kotlinx-coroutines 1.9.x | Reactive state plumbing | `callbackFlow` to bridge the OkHttp `WebSocketListener` into a `Flow`; `StateFlow` for current printer state; structured concurrency for reconnect logic. |
| **Coil 3** | 3.x (latest stable) | gcode thumbnails, any imagery | Compose-native `AsyncImage`, ~half Glide's size, Kotlin/coroutines-based, **bumped to minSdk 23 (your exact floor)**, fixed OOM decoding large PNGs on API ≤23 — directly relevant to 2GB. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **DataStore (Preferences)** | androidx.datastore 1.1.x | Persist connection settings (host, port, API key, prefs) | Default choice. Coroutine/Flow-native, no main-thread ANR risk, supports minSdk 23. A handful of key/values = Preferences DataStore, not Proto, not Room. |
| **androidx.profileinstaller + Baseline Profile** | 1.4.x | AOT-precompile hot paths | **Mandatory** for acceptable Compose startup/scroll on Tegra-era hardware. Generate via a Macrobenchmark module. |
| **Vico** | 2.x | Temperature history chart *if* you want built-in axes/legends/markers | Only if custom Canvas chrome becomes tedious. Multiplatform, Compose + Views modules, draws via `android.graphics.Canvas`. See charting note — custom Canvas is the leaner default. |
| **Navigation-Compose** | 2.8.x | Panel routing / back-stack | If you want structured nav for ~34 eventual panels. For v1's handful of panels a simple `when(screen)` state holder is lighter; adopt Nav-Compose when the panel count grows. |
| **kotlinx-collections-immutable** | 0.3.x | `ImmutableList` for stable Compose params | Helps Compose skip recomposition for list params (lists are unstable by default). Cheap win on weak CPU. |
| **Material 3 (Compose)** | via BOM | UI components | `androidx.compose.material3`. Dark, high-contrast theme suits a printer-side screen. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Android Gradle Plugin (AGP)** | Build | **Pin 8.7.x.** Do NOT jump to AGP 9 yet — Compose 1.12 will require compileSdk 37 + AGP 9; stay on the 8.x / compileSdk 35 line for stability. |
| **Gradle Version Catalog** (`libs.versions.toml`) | Dependency management | Pin every version. This project's whole risk profile is "modern libs silently dropping API 23" — a version catalog makes the floor auditable. |
| **Macrobenchmark module** | Generate Baseline Profile + measure real startup/jank | Run on a *real Nexus 7 or equivalent old device*, in release mode. Emulators lie about old-GPU performance. |
| **`apksigner` / signing config** | Sign release APK for GitHub Releases | Generate a keystore, commit signing to CI (GitHub Actions), publish the signed APK as a release asset. No Play App Signing (no Play). |
| **R8 (minify + shrink)** | Smaller APK, faster load | Enable for release. Smaller dex = faster install/verify on old hardware. Keep rules for kotlinx.serialization + Retrofit models. |

## Installation (Gradle, not npm — this is Android)

`gradle/libs.versions.toml` (illustrative, pin to verified-current at build time):

```toml
[versions]
kotlin = "2.1.21"
agp = "8.7.3"
composeBom = "2026.05.00"
okhttp = "4.12.0"
retrofit = "2.11.0"
kotlinxSerialization = "1.7.3"
coroutines = "1.9.0"
coil = "3.1.0"
datastore = "1.1.1"
lifecycle = "2.8.7"
activity = "1.9.3"
profileinstaller = "1.4.1"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { module = "com.squareup.retrofit2:converter-kotlinx-serialization", version.ref = "retrofit" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
```

```kotlin
// build.gradle.kts (app)
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 23      // the new AndroidX floor — do not go lower
        targetSdk = 35
    }
}
plugins {
    id("org.jetbrains.kotlin.plugin.compose")        // Kotlin 2.0+ Compose compiler
    id("org.jetbrains.kotlin.plugin.serialization")  // kotlinx.serialization
}
```

## Networking deep-dive: the Moonraker JSON-RPC model

Moonraker speaks **JSON-RPC 2.0 over WebSocket** with `notify_*` server-push events plus a REST API. Architecture for it:

- **One OkHttp `WebSocket`**, wrapped in a `callbackFlow` that emits raw text frames. `OkHttp WebSocketListener.onMessage` → `trySend`; close the flow on `onClosed`/`onFailure`; OkHttp handles ping/pong keepalive.
- **A thin JSON-RPC layer over kotlinx.serialization.** Requests carry an incrementing `id`; maintain a `Map<id, CompletableDeferred<JsonElement>>` to correlate responses to suspend calls. Messages *without* an `id` (or with method `notify_status_update`, `notify_proc_stat_update`, etc.) are push notifications — route them to `SharedFlow`s by method name.
- **kotlinx.serialization is the right JSON lib here specifically** because Moonraker's `objects/subscribe` deltas and `notify_status_update` payloads are heterogeneous, partial, and schema-loose. Decode into `JsonObject`/`JsonElement` and walk it, or use `@Serializable` + `JsonContentPolymorphicSerializer` for the parts you model strictly. Moshi can do this too, but kotlinx is Kotlin-native, faster (compile-time, no reflection), and avoids a kapt/KSP step.
- **Reconnect** is structured-concurrency-friendly: a supervising coroutine re-establishes the socket with backoff, re-sends `objects/subscribe`, and re-emits a connection-state `StateFlow` (`Connecting/Connected/Disconnected/Error`) that the shell observes for the splash/status surface.

**Why OkHttp over Ktor/Scarlet/Java-WebSocket here:**
- **vs Ktor:** Ktor's websocket is more coroutine-idiomatic out of the box, but it pulls a second HTTP engine/stack. You already want OkHttp under Retrofit for REST; using OkHttp for both means *one* TLS/connection-pool/timeout config and a smaller dependency graph — meaningful on a 2GB device and for a solo maintainer. Bridging OkHttp's listener to a Flow is ~20 lines. Ktor is the better pick *only* if you go Kotlin Multiplatform later; you're not.
- **vs Scarlet (Tinder):** Nice declarative JSON-RPC-ish abstraction, but **effectively unmaintained** and adds magic you don't need. Don't.
- **vs Java-WebSocket (org.java_websocket):** Bare, no coroutine story, no shared HTTP stack, you'd hand-roll TLS/keepalive. No reason over OkHttp.

## Charting note (temperature history on weak GPU)

**Default recommendation: custom Compose `Canvas`. Confidence: MEDIUM.**

A temperature graph is just N polylines (one per heater) over a rolling time window — a few hundred points, redrawn a few times per second. That's trivial draw work; a `Canvas { drawPath(...) }` in Compose with a downsampled rolling buffer is lighter than any library and gives you total control over allocation (reuse `Path` objects, avoid per-frame garbage — important on a 2GB GC-sensitive device). KlipperScreen's own graph is custom-drawn for the same reason.

**Use Vico instead if** you want axes, gridlines, legends, and touch markers without hand-coding them, and profiling shows the custom version isn't a bottleneck. Vico draws via `android.graphics.Canvas` (same path as Views), supports Compose, and is reasonably light. But it's more general than you need; start custom, escalate to Vico only if the chrome cost outweighs the dependency.

**Avoid MPAndroidChart** — heavyweight, View-based (needs `AndroidView` interop in Compose), historically GC-heavy on continuous updates, and the project is barely maintained. Wrong tool for a live graph on old hardware.

## Camera note (later phase — not core)

For the post-core camera panel:
- **MJPEG (the common Klipper case, e.g. `ustreamer`/`mjpg-streamer`/crowsnest):** decode the `multipart/x-mixed-replace` stream yourself — OkHttp streaming response → split on the multipart boundary → `BitmapFactory.decodeByteArray` per frame → push into a Compose `Image`/`Canvas`. Reuse a bitmap and `inSampleSize`-downscale to the display size; on a 2GB Tegra device, decoding full-res MJPEG frames will OOM/jank, so scale down hard. Existing references: `niqdev/ipcam-view`, `perthcpe23/android-mjpeg-view` (both old/Java — use as reference, likely re-implement lean in Kotlin rather than depend on them).
- **WebRTC (camera-streamer / go2rtc low-latency):** real WebRTC on API-23 Tegra hardware is heavy and the `org.webrtc` lib is large. **Defer past MJPEG.** Ship MJPEG first (covers most setups), add WebRTC only if low latency is demanded and the hardware can take it.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Jetpack Compose | Classic Views/XML | Only if you needed API <21, or were porting an existing XML app. Neither applies. |
| Kotlin | Java | Never, for greenfield. Java loses coroutines/Flow ergonomics, kotlinx.serialization, Compose. |
| OkHttp websocket | Ktor client websocket | If you commit to Kotlin Multiplatform (shared iOS/desktop). You're Android-only. |
| OkHttp websocket | Scarlet / Java-WebSocket | Don't — Scarlet unmaintained, Java-WebSocket too bare. |
| kotlinx.serialization | Moshi | Fine alternative if the team already standardizes on Moshi+Retrofit; slightly easier loose-JSON ergonomics for some. Still recommend kotlinx for Kotlin-native + speed. |
| kotlinx.serialization | Gson | Never for new code — reflection-based (slow on weak CPU), larger, effectively legacy. |
| Coil 3 | Glide | If you were on Java/Views. Glide is faster in some real-world cases but ~2× the size and not Compose-native. |
| Coil 3 | Picasso | No — stagnant, no Compose, fewer features. |
| Custom Canvas chart | Vico | When you want built-in axes/legend/markers and profiling clears it. |
| DataStore | SharedPreferences | Acceptable fallback (simplest, works everywhere) but main-thread I/O can ANR; DataStore is the modern coroutine-safe choice and supports 23. |
| DataStore | Room | Only if settings grow into relational/queryable data (multi-printer profiles, console history persistence). Overkill for v1 connection settings. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **Any library requiring minSdk 24+ or 26+** | Hard-breaks the Nexus 7 floor. *Check every dependency's minSdk* — this is the project's central trap. | minSdk-23 libs (the post-June-2025 AndroidX baseline is exactly 23, so most current libs are fine). |
| **Bleeding-edge Compose 1.12 / AGP 9 / compileSdk 37** | Compose 1.12 will require compileSdk 37 + AGP 9 — needless churn and instability for this project. | Pin Compose BOM 2026.05 (Compose 1.11), AGP 8.7.x, compileSdk 35. |
| **Gson** | Reflection-based, slow on weak CPU, large, legacy. | kotlinx.serialization. |
| **MPAndroidChart** | Heavy, View-based, GC-churn on live updates, near-unmaintained. | Custom Compose Canvas (or Vico). |
| **Scarlet (Tinder websocket)** | Unmaintained; unnecessary abstraction. | OkHttp websocket + thin JSON-RPC layer. |
| **WebRTC in v1** | `org.webrtc` is large and heavy on Tegra/API 23. | MJPEG decode first; WebRTC much later, if ever. |
| **Google Play Services / Firebase deps** | Target hardware lacks current Play Services; you're sideloading. | Nothing — keep the dependency graph GMS-free so it runs on bare AOSP. |
| **Hilt/Dagger (for v1)** | DI codegen is overkill for a single-screen-graph app; adds build complexity. | Manual DI / a simple service-locator object. Add Hilt only if the graph grows unwieldy. |
| **SharedPreferences for the hot path** | Main-thread disk I/O → ANR risk on slow flash. | DataStore (coroutine-backed). |

## Version Compatibility (the load-bearing checks)

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| AndroidX (Activity 1.9+, Lifecycle 2.8+, Compose Material) | **minSdk 23** | Floor raised from 21→23 in **June 2025**. 23 is supported; <23 is *not* on current releases. This is why your floor is safe. |
| Coil 3.x | **minSdk 23** | Coil 3 raised its floor to exactly 23; fixed large-PNG OOM on API ≤23. Perfect match. |
| Compose Compiler | == Kotlin version (2.0+) | Apply the `kotlin.plugin.compose` Gradle plugin; don't pin a separate compiler version. |
| Compose BOM 2026.05 (Compose 1.11) | AGP 8.x, compileSdk 35 | **Compose 1.12 jumps to compileSdk 37 / AGP 9 — stay on 1.11 line.** |
| DataStore 1.1.x | minSdk 23 (AndroidX default) | Coroutine-safe settings store. |
| OkHttp 4.12 / Retrofit 2.11 | minSdk 21 | Comfortably below your floor; one stack serves both websocket and REST. |
| kotlinx.serialization 1.7 + Retrofit converter | Retrofit 2.11, Kotlin 2.1 | Use `converter-kotlinx-serialization`; no kapt/KSP needed. |

## Stack Patterns by Variant

**If you later go Kotlin Multiplatform (share logic with a desktop/iOS client):**
- Swap OkHttp websocket → **Ktor client** (multiplatform), keep kotlinx.serialization and Coil (both already KMP). Compose Multiplatform for shared UI.
- Because *this* is Android-only, don't pay that tax now — but kotlinx.serialization + Coil keep the door open cheaply.

**If multi-printer / console-history persistence arrives (v2):**
- Promote persistence from DataStore → **Room** for the relational/queryable bits; keep DataStore for simple app prefs.

**If the panel count approaches full ~34 KlipperScreen parity:**
- Adopt **Navigation-Compose** for the back-stack, and consider **Hilt** for DI once the object graph stops fitting in your head. Neither is worth it for v1.

## Sources

- developer.android.com/develop/ui/compose/performance, /migrate/compare-metrics — Compose↔Views jank parity since 1.9, baseline-profile guidance — **HIGH**
- android-developers.googleblog.com (Dec '25, Nov '25, April '26 Compose releases) — pausable composition default, Compose 1.11/1.12, compileSdk 37/AGP 9 for 1.12 — **HIGH**
- developer.android.com/jetpack/androidx/versions + Lifecycle/Activity/AppCompat release notes — **AndroidX minSdk 21→23 in June 2025** (the key finding) — **HIGH**
- coil-kt.github.io/coil/changelog + upgrading_to_coil3 — Coil 3 minSdk 23, API≤23 OOM fix — **HIGH**
- Context7: `/square/okhttp`, `/coil-kt/coil`, `/websites/developer_android_develop_ui_compose` — library currency confirmation — **HIGH**
- developer.android.com/develop/ui/compose/bom + mvnrepository compose-bom — BOM 2026.05.00 / Compose 1.11 — **MEDIUM-HIGH** (version pinned at build time)
- Moshi vs kotlinx.serialization benchmarks (zacsweers JSON benchmarking; Medium comparisons) — kotlinx compile-time speed/size advantage — **MEDIUM**
- github.com/patrykandpatrick/vico — Compose+Views Canvas-based charting — **MEDIUM**
- github.com niqdev/ipcam-view, perthcpe23/android-mjpeg-view; obico.io Klipper webcam — MJPEG reference (old/Java) — **LOW-MEDIUM** (reference, not dependency)
- DataStore vs SharedPreferences (Android codelab + comparisons) — coroutine-safe, minSdk-23-compatible — **MEDIUM**

---
*Stack research for: native Android Moonraker/Klipper control surface on API-23 / 2GB / Tegra-era hardware*
*Researched: 2026-05-29*
