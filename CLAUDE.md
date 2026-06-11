**Last verified:** 2026-06-11
**Status:** active
<!-- GSD:project-start source:PROJECT.md -->
## Project

**Dinghy Display**

A native Android app that turns cheap, old Android hardware (Nexus 7 2013 class) into a
touchscreen control surface for a Klipper 3D printer, talking directly to the Moonraker API
(websocket + REST). It replaces the convoluted KlipperScreen-on-Linux + VNC/XSDL/X11 stack
people use today to get a printer touchscreen, giving the same printer-control capability as
a self-contained `.apk` with no Linux host, no remote-desktop layer, and no display-server glue.

The target user is a Klipper owner who has an old tablet or Android device lying around and
wants a dedicated, always-on printer screen without fighting VNC or re-flashing a Raspberry Pi.

**Core Value:** **Direct, reliable printer control from an old Android tablet over Moonraker — install an APK,
point it at the printer, and drive a print.** If everything else fails, the connect → monitor →
control-a-print loop must work flawlessly on a Nexus 7.

### Constraints

- **Compatibility**: minSdk 23 (Android 6.0). The Nexus 7 2013 is the **support FLOOR** (the must-run worst case), not the only target — v1 targets phones through tablets. Why: the "runs on cheap old hardware" promise is the project's soul; it stays as the floor even though scope broadened.
- **Performance**: The Adreno 320 (Snapdragon S4 Pro, 32-bit ARMv7, 1920×1200, 2GB) is the **worst-case perf budget** everything is measured against — fill rate is the bottleneck; a janky printer screen is worse than none. Richer effects are fine on capable hardware but must not break the floor. (The *2013* Nexus 7 is Adreno 320 / 1920×1200 — NOT the *2012* Tegra 3 / 1280×800.)
- **Orientation**: **Portrait AND landscape** (was landscape-only). Every screen is responsive via the Focus/Field/Gutter grammar — see UI Design System below.
- **Theming**: **Full semantic-token theme system — dark + light + user custom — plus an S/M/L text-size setting.** All UI routes through role tokens, never raw colors (see `docs/ui_design/THEMING.md`).
- **Tech stack**: Native Android, Kotlin, Jetpack Compose + classic Views hybrid (ADR 0001). Why: direct hardware/OS access, offline operation, broad-device compatibility.
- **Connectivity**: Local-network Moonraker (websocket + REST), optional API-key/trusted-client auth — Why: Moonraker is the only integration surface; printer and tablet share a LAN.
- **Distribution**: Sideloaded signed APK via GitHub Releases — Why: no current Play Services on the target hardware.
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

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
| **Build** | minSdk 23 / targetSdk 35 / **compileSdk 36** (bumped 35→36 in Phase 21 — required by media3 1.10.1's AAR metadata; AGP 8.7.x accepts it with a warn-only "max recommended 35"; still NOT the AGP-9 / compileSdk-37 / Compose-1.12 jump), AGP 8.7.x, Kotlin 2.1.x, **pin a known-good Compose BOM** | HIGH |
## The Big Decision: Compose vs Views on Adreno 320 / 2GB / 1920×1200 / API 23
- Ship a **Baseline Profile** from day one.
- Build/profile in **release mode** — debug Compose is 5–10× slower and will lie to you about jank.
- Hoist state; use `StateFlow` + `collectAsStateWithLifecycle()`; wrap derived values in `derivedStateOf`.
- Keep socket-driven state objects `@Stable`/`@Immutable` so Compose can skip.
- Keys on `LazyColumn`/`LazyVerticalGrid` items; never read a rapidly-changing value (temps) higher in the tree than necessary.
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
| **androidx.profileinstaller + Baseline Profile** | 1.4.x | AOT-precompile hot paths on API 24+ | ⚠ **NO-OP on STOCK API 23 only.** Baseline Profiles need profile-guided compilation (API 24+); stock Android 6 / API 23 does **full AOT at install**, so the profile would never be consulted on a stock-Android Nexus 7 2013. **BUT the actual test device (flox) runs LineageOS 18.1 / API 30, where profiles DO apply** — and so do all modern phones in v1's broadened scope. This raises the priority of the `macrobenchmark-module-wiring` todo. Do NOT gate floor perf on it — measure the plain release/R8 build — but ship the profile for the API-24+ majority. Still build the Macrobenchmark module. |
| **Vico** | 2.x | Temperature history chart *if* you want built-in axes/legends/markers | Only if custom Canvas chrome becomes tedious. Multiplatform, Compose + Views modules, draws via `android.graphics.Canvas`. See charting note — custom Canvas is the leaner default. |
| **Navigation-Compose** | 2.8.x | Panel routing / back-stack | If you want structured nav for ~34 eventual panels. For v1's handful of panels a simple `when(screen)` state holder is lighter; adopt Nav-Compose when the panel count grows. |
| **kotlinx-collections-immutable** | 0.3.x | `ImmutableList` for stable Compose params | Helps Compose skip recomposition for list params (lists are unstable by default). Cheap win on weak CPU. |
| **Material 3 (Compose)** | via BOM | UI components | `androidx.compose.material3`. Dark, high-contrast theme suits a printer-side screen. |
### Development Tools
| Tool | Purpose | Notes |
|------|---------|-------|
| **Android Gradle Plugin (AGP)** | Build | **Pin 8.7.x.** Do NOT jump to AGP 9 yet — Compose 1.12 will require compileSdk 37 + AGP 9; stay on the 8.x line for stability. **compileSdk is 36 as of Phase 21** (media3 1.10.1's AAR metadata mandates ≥36; AGP 8.7.0 accepts 36 warn-only) — this is an accepted middle step on the 8.x line, NOT the forbidden AGP-9 / compileSdk-37 / Compose-1.12 jump. |
| **Gradle Version Catalog** (`libs.versions.toml`) | Dependency management | Pin every version. This project's whole risk profile is "modern libs silently dropping API 23" — a version catalog makes the floor auditable. |
| **Macrobenchmark module** | Measure real startup/jank (primary); generate a Baseline Profile (only useful on API 24+ devices) | Run on a *real Nexus 7 or equivalent old device*, in release mode. Emulators lie about old-GPU performance. NOTE: frame metrics (`FrameTimingMetric`) may not work on API 23 — fall back to raw `gfxinfo framestats` on-device. |
| **`apksigner` / signing config** | Sign release APK for GitHub Releases | Generate a keystore, commit signing to CI (GitHub Actions), publish the signed APK as a release asset. No Play App Signing (no Play). |
| **R8 (minify + shrink)** | Smaller APK, faster load | Enable for release. Smaller dex = faster install/verify on old hardware. Keep rules for kotlinx.serialization + Retrofit models. |
## Installation (Gradle, not npm — this is Android)
## Networking deep-dive: the Moonraker JSON-RPC model
- **One OkHttp `WebSocket`**, wrapped in a `callbackFlow` that emits raw text frames. `OkHttp WebSocketListener.onMessage` → `trySend`; close the flow on `onClosed`/`onFailure`; OkHttp handles ping/pong keepalive.
- **A thin JSON-RPC layer over kotlinx.serialization.** Requests carry an incrementing `id`; maintain a `Map<id, CompletableDeferred<JsonElement>>` to correlate responses to suspend calls. Messages *without* an `id` (or with method `notify_status_update`, `notify_proc_stat_update`, etc.) are push notifications — route them to `SharedFlow`s by method name.
- **kotlinx.serialization is the right JSON lib here specifically** because Moonraker's `objects/subscribe` deltas and `notify_status_update` payloads are heterogeneous, partial, and schema-loose. Decode into `JsonObject`/`JsonElement` and walk it, or use `@Serializable` + `JsonContentPolymorphicSerializer` for the parts you model strictly. Moshi can do this too, but kotlinx is Kotlin-native, faster (compile-time, no reflection), and avoids a kapt/KSP step.
- **Reconnect** is structured-concurrency-friendly: a supervising coroutine re-establishes the socket with backoff, re-sends `objects/subscribe`, and re-emits a connection-state `StateFlow` (`Connecting/Connected/Disconnected/Error`) that the shell observes for the splash/status surface.
- **vs Ktor:** Ktor's websocket is more coroutine-idiomatic out of the box, but it pulls a second HTTP engine/stack. You already want OkHttp under Retrofit for REST; using OkHttp for both means *one* TLS/connection-pool/timeout config and a smaller dependency graph — meaningful on a 2GB device and for a solo maintainer. Bridging OkHttp's listener to a Flow is ~20 lines. Ktor is the better pick *only* if you go Kotlin Multiplatform later; you're not.
- **vs Scarlet (Tinder):** Nice declarative JSON-RPC-ish abstraction, but **effectively unmaintained** and adds magic you don't need. Don't.
- **vs Java-WebSocket (org.java_websocket):** Bare, no coroutine story, no shared HTTP stack, you'd hand-roll TLS/keepalive. No reason over OkHttp.
## Charting note (temperature history on weak GPU)
## Camera note (later phase — not core)
- **MJPEG (the common Klipper case, e.g. `ustreamer`/`mjpg-streamer`/crowsnest):** decode the `multipart/x-mixed-replace` stream yourself — OkHttp streaming response → split on the multipart boundary → `BitmapFactory.decodeByteArray` per frame → push into a Compose `Image`/`Canvas`. Reuse a bitmap and `inSampleSize`-downscale to the display size; on a 2GB Adreno 320 device, decoding full-res MJPEG frames will OOM/jank, so scale down hard. Existing references: `niqdev/ipcam-view`, `perthcpe23/android-mjpeg-view` (both old/Java — use as reference, likely re-implement lean in Kotlin rather than depend on them).
- **WebRTC (camera-streamer / go2rtc low-latency):** real WebRTC on API-23 Adreno 320 hardware is heavy and the `org.webrtc` lib is large. **Defer past MJPEG.** Ship MJPEG first (covers most setups), add WebRTC only if low latency is demanded and the hardware can take it.
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
| **Bleeding-edge Compose 1.12 / AGP 9 / compileSdk 37** | Compose 1.12 will require compileSdk 37 + AGP 9 — needless churn and instability for this project. (compileSdk **36** is fine and in use since Phase 21 for media3 1.10.1 — it does NOT cross into the 37/AGP-9 line.) | Pin Compose BOM 2026.05 (Compose 1.11), AGP 8.7.x, compileSdk 36. |
| **Gson** | Reflection-based, slow on weak CPU, large, legacy. | kotlinx.serialization. |
| **MPAndroidChart** | Heavy, View-based, GC-churn on live updates, near-unmaintained. | Custom Compose Canvas (or Vico). |
| **Scarlet (Tinder websocket)** | Unmaintained; unnecessary abstraction. | OkHttp websocket + thin JSON-RPC layer. |
| **WebRTC in v1** | `org.webrtc` is large and heavy on Adreno 320 / API 23. | MJPEG decode first; WebRTC much later, if ever. |
| **Google Play Services / Firebase deps** | Target hardware lacks current Play Services; you're sideloading. | Nothing — keep the dependency graph GMS-free so it runs on bare AOSP. |
| **Hilt/Dagger (for v1)** | DI codegen is overkill for a single-screen-graph app; adds build complexity. | Manual DI / a simple service-locator object. Add Hilt only if the graph grows unwieldy. |
| **SharedPreferences for the hot path** | Main-thread disk I/O → ANR risk on slow flash. | DataStore (coroutine-backed). |
## Version Compatibility (the load-bearing checks)
| Package | Compatible With | Notes |
|---------|-----------------|-------|
| AndroidX (Activity 1.9+, Lifecycle 2.8+, Compose Material) | **minSdk 23** | Floor raised from 21→23 in **June 2025**. 23 is supported; <23 is *not* on current releases. This is why your floor is safe. |
| Coil 3.x | **minSdk 23** | Coil 3 raised its floor to exactly 23; fixed large-PNG OOM on API ≤23. Perfect match. |
| Compose Compiler | == Kotlin version (2.0+) | Apply the `kotlin.plugin.compose` Gradle plugin; don't pin a separate compiler version. |
| Compose BOM 2026.05 (Compose 1.11) | AGP 8.x, compileSdk 36 | **Compose 1.12 jumps to compileSdk 37 / AGP 9 — stay on 1.11 line.** (compileSdk 36 since Phase 21 for media3 1.10.1 is still the 8.x / 1.11 line.) |
| DataStore 1.1.x | minSdk 23 (AndroidX default) | Coroutine-safe settings store. |
| OkHttp 4.12 / Retrofit 2.11 | minSdk 21 | Comfortably below your floor; one stack serves both websocket and REST. |
| kotlinx.serialization 1.7 + Retrofit converter | Retrofit 2.11, Kotlin 2.1 | Use `converter-kotlinx-serialization`; no kapt/KSP needed. |
## Stack Patterns by Variant
- Swap OkHttp websocket → **Ktor client** (multiplatform), keep kotlinx.serialization and Coil (both already KMP). Compose Multiplatform for shared UI.
- Because *this* is Android-only, don't pay that tax now — but kotlinx.serialization + Coil keep the door open cheaply.
- Promote persistence from DataStore → **Room** for the relational/queryable bits; keep DataStore for simple app prefs.
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
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.

### Local Build Environment (READ THIS before running Gradle)

This repo is developed under **WSL** but **builds Windows-side** so the USB Nexus 7 is reachable by
adb natively. The committed `gradlew`/`gradlew.bat` are the stock cross-platform wrapper (CI builds
on Linux) — do **not** replace them. Locally, **`./gradlew` does NOT work from WSL bash**; drive the
Windows build via the helper instead.

- **JDK:** Adoptium **JDK 21** — `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- **Android SDK:** `E:\Android\Sdk` (platform-tools/adb, `platforms;android-35`, `build-tools;34.0.0`)
- **Build helper:** `E:\Android\gw.bat` sets `JAVA_HOME`+`ANDROID_HOME` and calls the repo `gradlew.bat`.
- **adb:** `E:\Android\Sdk\platform-tools\adb.exe` (run via interop from WSL).

**Run any Gradle task** from the repo root:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <gradle args>"
# e.g. ... "E:\Android\gw.bat :app:assembleRelease --no-daemon"
```
Gradle prints CR progress bars — pipe through `tr -d '\r'`; the process exit code is authoritative.
`local.properties` is gitignored and unneeded (`gw.bat` exports `ANDROID_HOME`).

**Installing a RELEASE build on-device** (the release APK is unsigned until PKG-01/Phase 8): zipalign +
debug-sign it, then `adb install`. Helper: `E:\Android\sign-release.bat <in.apk> <out.apk>` (uses the
debug keystore at `C:\Users\matth\.android\debug.keystore`). `connectedAndroidTest`/`assembleDebug`
are auto debug-signed, so instrumented tests install without this.

**Device reality:** the physical "Nexus 7 2013" (`flox`) runs **LineageOS 18.1 / Android 11 / API 30**,
not stock Android 6 / API 23. Hardware is genuine (Adreno 320 / 2GB / 1920×1200 / `armeabi-v7a`).
`minSdk 23` is retained as the install floor; on-device evidence reflects API-30 (NSC cleartext path,
newer ART). See `docs/adr/0001-ui-toolkit-decision.md` and the Phase-1 SUMMARYs.

### UI toolkit (locked by ADR 0001)

**Hybrid:** Jetpack Compose for the shell and most panels; **classic Views** (RecyclerView + custom
`Canvas`) for the three high-churn surfaces — **Files list, live temperature graph, Console scrollback**
(measured ~2× lower p95 frame time on the real device). Host Views in Compose via `AndroidView`/`ComposeView`;
keep view-models toolkit-agnostic (StateFlow to both). See `docs/adr/0001-ui-toolkit-decision.md`.
<!-- GSD:conventions-end -->

## UI Design System (LAW — read `docs/ui_design/` before building any screen)

The complete visual + interaction contract for the WHOLE app lives in **`docs/ui_design/`** (authored by
Matthew in a dedicated design session, 2026-05-31). It **supersedes** any earlier per-phase UI-SPEC.
Fidelity is HIGH — `reference/hifi.css` is the canonical token/component source; reproduce its values in
the Compose/Views stack (it is a reference, not code to copy verbatim).

**Read in this order (they are the law):**
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables.
- `docs/ui_design/LAYOUT.md` — the **Focus / Field / Gutter** grammar (regions, orientation rules, the ⚠ non-negotiables: one shared tabular grid, sacred aspect ratios, ratio-only sizing — no hardcoded px).
- `docs/ui_design/THEMING.md` — the semantic token system, dark/light values, button-intent colors, `--fs`.
- `docs/ui_design/PREVIEW_AND_TOKENS.md` — the **preview-first / tokenized-first** build convention (Phase 18): every new screen ships a `@Preview` matrix (6 theme combos + `fs=L`, no live Moonraker), `stringResource` strings, and `DinghyIcon` tokens from day one.
- `docs/ui_design/images/*.png` — hi-fi mockups of all 10 core screens (portrait + landscape).

**Load-bearing rules (and the decisions that reconcile them with this project's constraints):**
- **Focus / Field / Gutter** on one shared grid; portrait stacks, landscape is Focus|Field 50/50 + full-width gutter. No persistent status bar — status is **color on an existing element** (homed axis green / unhomed amber).
- **Outline-led, touch-first controls** (2px outline + glow, ≥64px targets). **Button intent = color**: red = stop/cancel/back, green = accept, amber = proceed-at-peril, accent(blue) = physical command, white = setting.
- **Navigation = swipe-up full-screen App Drawer** (tiles incl. Settings + red Power). **Stop → full-screen Confirm guard** (not a hold gesture, not a dialog). **Single-setting scrubber/stepper page** for every numeric value.
- **No alphanumeric keyboard in printer controls.** The **Settings screen** (conventional Android, keyboard allowed) owns connection (host/port/key), theme, and feature toggles. Controls needing alphanumeric input (console, macro params, file search) are triaged per-control in their own phases.
- **Type:** Geist + Geist Mono (tabular numerals for live data). **Theming:** dark + light + user custom via role tokens; **S/M/L** text-size (`--fs`).
- **Motion:** static glow YES; **no continuous "breathing"/looping animation** (Adreno-320 budget — match the aesthetic without a CPU-cycle burner). One-shot transitions OK if cheap.
- **Target/floor:** v1 = phones→tablets, portrait + landscape; the **Nexus 7 2013 (Adreno 320) is the perf floor**, not the only target.

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Mapped in `.planning/codebase/ARCHITECTURE.md` (layer map, data flow, state holders, the single-subscribe spine) and `docs/adr/0001-ui-toolkit-decision.md` (Compose + classic-Views hybrid — which surfaces are Views and why). Read both before structural changes.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

- **Sketch findings (jiib redesign)** (design decisions, CSS patterns, component classes, visual direction from the 001–003 sketches — waterfall home, Spoolman lists/detail, Fine-Tune adjustment) → `Skill("sketch-findings-dinghy-display")`. **Auto-load before building any redesigned UI** (Layer-1 onward). Companion source-of-truth notes: `.planning/notes/2026-06-09-*.md`.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
