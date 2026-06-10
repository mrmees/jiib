# Top-Down Audit & Roadmap — June 2026

**What this is:** a whole-repo, top-down audit of Dinghy Display against four goals —
cross-device compatibility, runtime efficiency, open-source maintainability, and
future-friendliness — plus the design for the build-time gcode **command map**. It ends with a
sequenced set of work packages (R1–R8), each sized for one CLI session and written so it can be
fed to `/gsd-execute-phase` (or quoted directly at the CLI) as-is.

**How it was produced:** five parallel static deep-dives over the full source tree (385 Kotlin
files), cross-checked against `docs/request-cadence-contract.md`, `docs/ui_design/*`, the
`.planning/` phase record, and the Gradle build config. Every finding carries file:line evidence.
(Build/test execution was not possible in the audit environment — network policy blocked
dependency resolution — so CI claims are from config inspection, not a run.)

---

## TL;DR — answers to the four questions

| Question | Verdict |
|---|---|
| **Cross-device: screen size, rotation, overflow?** | **Largely solved by design.** `ScreenScaffold` + `BoxWithConstraints` is a genuinely good responsive engine; text overflow discipline is consistent. Three real gaps: **zero insets handling** (content draws under status bar/cutout on Android 15+ where targetSdk 35 *forces* edge-to-edge), a fixed 72dp file-row height, and a 160dp macro-grid cell that wastes narrow-phone width. → **R1, R3** |
| **Efficiency: redraw rate vs source rate?** | **Already excellent — do not touch.** The 250 ms two-plane conflation in `PrinterStateStore` is honored end-to-end; render surfaces are allocation-free; webcam decode stops when backgrounded; no polling, no looping animations. The cadence contract is real, not aspirational. → nothing to fix; **R8** has one micro-advisory |
| **Maintainable in Android Studio? Previews?** | **Architecture yes, wrapper no.** Holder+StateFlow pattern, manual DI, and pure-function routing are contributor-friendly; the test harness (fake socket + golden fixtures) is a standout. But: no README/CONTRIBUTING/LICENSE, no CI, `gradlew` committed without its executable bit, lint disabled, and **18 of 27 screens lack the @Preview matrices** your own convention doc mandates. → **R5, R6** |
| **Future-friendly: modern phones?** | **One hard blocker:** the release APK is armeabi-v7a-only (`isUniversalApk = false`), so 64-bit-only devices (Pixel 7+, S25 Ultra — your own 2026-06-08 todo confirms) **cannot install it at all**. Plus: no doze/battery-exemption story for always-on use, no predictive-back opt-in, no POST_NOTIFICATIONS runtime request. Dependency cliffs are well-documented in `libs.versions.toml` already. → **R1, R2, R7, R8** |

**Command map (tokenization):** the groundwork is already done — `PrinterCommands.kt` centralizes
~99% of gcode behind clamped builders, and pause/resume/cancel go through Moonraker API methods
(`printer.print.*`), which already respect a user's overridden PAUSE/RESUME/CANCEL_PRINT macros
for free. The remap surface is genuinely small: extract it into one documented file. → **R4**

---

## Part 1 — What is already done well (do not rebuild)

These came back clean across all five audits. Future phases should *protect* these, not revisit them.

1. **Request cadence** — `PrinterStateStore.kt:219–228` implements the contract exactly: high-rate
   numerics conflated to 250 ms, control-plane edges (`print_stats`, `webhooks`, `homed_axes`)
   immediate, console on its own bounded buffer. No holder layers a second throttle. No screen
   polls. The one historical violation (page-open configfile re-query) was already removed and is
   guarded by `ProbeZOffsetFreshnessTest`.
2. **Render hot paths** — `GraphView.kt` (pre-allocated paths/paints, `rewind()` not `new`),
   `ProgressRing.kt` (value-driven, no animation), Files/Console RecyclerViews (DiffUtil /
   incremental append + single-notify eviction, thumbnail recycle + stale-fetch guard).
3. **Lifecycle hygiene** — every screen collects via `collectAsStateWithLifecycle()`; webcam
   decode runs under `repeatOnLifecycle(STARTED)` and fully stops off-screen
   (`AppShell.kt:270–291`); snapshot poller dedups identical frames and treats 401/403 as terminal
   (no self-DoS of the printer SBC).
4. **Responsive grammar** — `designsystem/layout/ScreenScaffold.kt` (BoxWithConstraints, weighted
   portrait stack, 50/50 landscape, `portraitFocusAspect` for sacred squares). Screens make
   continuous-measurement decisions (Files shows Focus on `maxWidth > maxHeight || selected`),
   which is *better* than Material WindowSizeClass buckets for this app. **Decision: do NOT adopt
   WindowSizeClass.**
5. **Text discipline** — `--fs` via `fsSp()` everywhere including the Views side; maxLines /
   ellipsize / `basicMarquee()` on every dynamic string surface checked; ≥64dp touch floor
   enforced through `OutlinedControl` (with the sanctioned Settings densify carve-out).
6. **Command centralization** — `command/PrinterCommands.kt` (576 lines): every gcode string is
   built by a pure function with named clamp bounds, flowing through one path
   (`CommandRegistry → PrinterCommands → scriptParams → CommandDispatcher → JsonRpcClient`).
   Zero duplicate definitions. Heater/fan/LED/servo names are runtime-discovered parameters,
   never hardcoded.
7. **Test spine** — 150 test files; `net/`, `state/`, `command/` heavily covered;
   `SessionTestHarness` + `FakeWebSocket` + golden Moonraker fixtures mean contributors can verify
   changes without a printer.
8. **Version discipline** — `gradle/libs.versions.toml` pins everything with rationale comments;
   `verify-min-sdk` asserts the merged-manifest API-23 floor on every build.
9. **Icon** — adaptive + monochrome layers already present (`mipmap-anydpi-v26/ic_launcher.xml`);
   Android 13+ themed icons work today.
10. **BackHandler wiring** — comprehensive and correctly layered in `AppShell.kt:486–516`
    (drawer > sub-stacks > nav stack); no deprecated `onBackPressed`.

---

## Part 2 — Findings (by severity, with evidence)

### CRITICAL

| # | Finding | Evidence | Why it matters |
|---|---|---|---|
| C1 | **Release APK uninstallable on 64-bit-only devices.** ABI split is `include("armeabi-v7a")` + `isUniversalApk = false`. | `app/build.gradle.kts:53–59`; confirmed by your own S25 Ultra test in `.planning/todos/pending/2026-06-08-phase-22-arm64-abi-ship-requirement.md` | Pixel 7+/most 2023+ flagships are arm64-only → `INSTALL_FAILED_NO_MATCHING_ABIS`. The "modern phone user" cannot even install the app. The `D-01a` comment ("can never carry an arm64 slice") conflates the dev-floor guarantee with the ship artifact. |
| C2 | **Zero insets/edge-to-edge handling.** No `enableEdgeToEdge`, no `WindowInsets`, no `systemBarsPadding`/`safeDrawingPadding` anywhere in the app. | grep across `app/src/main` = 0 hits; root is `Box(Modifier.fillMaxSize())` in `MainActivity.kt` | targetSdk 35 **forces** edge-to-edge on Android 15+ — this is not optional. On a notched phone, the top of every screen (and the gutter buttons at the bottom, against gesture-nav) will sit under system UI. |
| C3 | **No open-source wrapper.** No `README.md`, no `LICENSE`, no `CONTRIBUTING.md`, no `.github/workflows/`; `gradlew` committed mode `100644` (not executable) so `./gradlew` fails on every fresh Linux/macOS clone. | `git ls-files` (no matches); `git ls-files -s gradlew` → `100644` | First-five-minutes failure for every contributor. Without a LICENSE the project is not legally open source at all. |

### HIGH

| # | Finding | Evidence | Why it matters |
|---|---|---|---|
| H1 | **Always-on mode will die in Doze.** `FLAG_KEEP_SCREEN_ON` only in `BenchActivity.kt:56`, not MainActivity; `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` not declared; no first-run guidance. `START_STICKY` is not an exemption. | `AndroidManifest.xml`; `service/MoonrakerService.kt:116`; planned as PKG-03 in `.planning/REQUIREMENTS.md:114`, researched in `.planning/research/PITFALLS.md:94–109` | Wall-mounted tablet, screen off >10 min → Doze suspends the FGS's network → monitoring silently goes dark. This is the core product promise breaking. |
| H2 | **18 of 27 screens have no @Preview matrix** (Move, Temperature, Extrude, Calibration×5, Files, Macros×2, Console, Webcam, Settings, Theme, ThemeEditor, About, FwRetraction, Devices). | `preview/` package vs `*Screen.kt` enumeration; convention in `docs/ui_design/PREVIEW_AND_TOKENS.md` §3 | This is your "GUI vs CLI changes" lever. Until backfilled, most screens can't be visually validated in Android Studio without a live printer. The 3 exemplars (PrintStatus, FineTune, Spool) make backfill mechanical. |
| H3 | **No CI.** Nothing builds or runs the 150-file test suite on push. | no `.github/` directory | Open-source PRs are unreviewable at scale without a build+test gate. The JVM test suite needs no device/emulator, so this is cheap. |

### MEDIUM

| # | Finding | Evidence | Fix direction |
|---|---|---|---|
| M1 | Fixed 72dp file-row minimum height, not `--fs`/width responsive. | `ui/files/FileRowsAdapter.kt:97` | Derive from `fsSp`-scaled text metrics; keep ≥64dp floor. |
| M2 | Macro grid `GridCells.Adaptive(minSize = 160.dp)` → 1 wasteful column on narrow phones. | `ui/macros/BookmarkedMacrosScreen.kt:73` | Width-aware min size (e.g. `min(160.dp, maxWidth/2 - gap)`). |
| M3 | Predictive back: no `android:enableOnBackInvokedCallback="true"`; gesture preview on Android 13+ unverified. | `AndroidManifest.xml` | Add the manifest opt-in; verify drawer/sub-stack BackHandlers preview correctly on a real Android 15/16 device. |
| M4 | `POST_NOTIFICATIONS` declared but never requested at runtime → FGS notification silently absent on Android 13+. | manifest:31; no permission launcher in app code | Request once via Activity Result API alongside the connection setup flow (non-blocking; app must keep working if denied — it already does). |
| M5 | NSC permits cleartext **app-wide**; no TLS/`wss://` option for Moonraker. | `res/xml/network_security_config.xml` (tightening already deferred as D-11 in its own comment) | Scope cleartext to RFC-1918 ranges; add an `https/wss` scheme toggle in Settings later. Not a v1 blocker (LAN-only product), but cheap hardening before strangers run it. |
| M6 | Lint fully disabled (`checkReleaseBuilds=false`, `abortOnError=false`) due to AGP 8.7/JDK 21 UAST crash; no detekt/ktlint either. | `app/build.gradle.kts:91–94` | In CI, run lint under a JDK 17 toolchain (the crash is JDK-21-specific) or pin a lint baseline; revisit fully at the AGP 9 jump. |
| M7 | 16 hardcoded UI strings remain (Tilt×8, Files×2, Spool×4, ScanConfirm×1, ActiveSpool×1) vs 126 `stringResource` calls. | e.g. `ui/calibration/TiltScreen.kt`, `ui/files/FilesScreen.kt` | Finish the SC-3c sweep; the debug pseudolocale (`en-XA`) build is the verification tool and already wired. |
| M8 | OS font-scale is deliberately neutralized (`fontScale = 1f`) in favor of `--fs`. | `theme/compose/DinghyTheme.kt:48` | Keep the design, but **document it** (README/a11y note) and consider seeding the default S/M/L from the OS scale on first run. Accessibility reviewers of an OSS app *will* raise this. |
| M9 | God files: `PrintStatusScreen.kt` (1584 lines), `AppShell.kt` (1028), `CommandRegistry.kt` (879). Wide, not tangled. | those files | No urgent action; add a file-level KDoc "map" to each. Split only when next touched for features. |

### LOW / advisory

- Console eviction does `repeat(n) { items.removeAt(0) }` → O(n²) worst case on a burst; use
  `items.subList(0, n).clear()` (`ui/console/ConsoleRowsAdapter.kt:83–86`). Not reachable at
  normal console rates.
- `GalleryScreen.kt:267,306,322` fixed 280/320dp boxes — debug-only gallery, cosmetic.
- 16KB page sizes: only relevant to native `.so` libs (media3, CameraX — both Google-built and
  compliant; zxing is pure Java). Verify once on a 16KB device when arm64 ships; no action
  expected.
- Phase/decision comment convention (≈1465 `D-##`/`SC-##`/`Phase ##` refs) is load-bearing for
  the team but opaque to outsiders — fix with a 20-line decoder section in CONTRIBUTING, not by
  rewriting comments.

---

## Part 3 — The build-time command map (tokenization design)

**Scope decision (locked):** build-time file only. A user whose Klipper config remaps a
*documented* system function forks the repo, edits **one file**, rebuilds. No runtime remap UI,
no Klipper command cross-reference.

**Why the surface is small.** The audit found:

- Universal gcode (`G28`, `M104`, `M106`, `M220`…) never needs remapping — already built by
  clamped functions in `PrinterCommands.kt`.
- Printer-specific *names* (heaters, fans, LEDs, servos, pins, user macros) are already
  **runtime-discovered** from `printer.objects.list` and passed as parameters — nothing to remap.
- `PAUSE`/`RESUME`/`CANCEL_PRINT` flow through Moonraker API methods (`printer.print.pause` etc.,
  `request-cadence-contract.md` call inventory), and Moonraker invokes whatever macro the user's
  Klipper config defines — **remap-immune for free**.
- What's left: macro names the app both *gates on* and *executes by name*. Today that is exactly
  `LOAD_FILAMENT` / `UNLOAD_FILAMENT`, hardcoded in four places:
  `PrinterCommands.kt:330,333`, `CommandRegistry.kt:491,498` (`MacroPresent(...)`),
  `ExtrudeHolder.kt:106–107`.

**Design — `command/CommandMap.kt`** (single new file, heavily commented, linked from README):

```kotlin
/**
 * THE fork-and-edit customization point. If your Klipper config maps a documented
 * system function to a different macro name, change it HERE and rebuild — nothing
 * else in the app hardcodes these names.
 *
 * Each entry is both (a) the gcode the app sends and (b) the macro name whose
 * presence (case-insensitive, via printer.objects.list) gates the button. If your
 * macro takes no args, the bare name is fine; a full command line also works:
 *   override val loadFilament = Slot(macro = "M701")               // marlin-style alias
 *   override val unloadFilament = Slot(macro = "FILAMENT_EJECT", gcode = "FILAMENT_EJECT BEEP=1")
 */
object CommandMap {
    data class Slot(val macro: String, val gcode: String = macro)

    val loadFilament   = Slot("LOAD_FILAMENT")
    val unloadFilament = Slot("UNLOAD_FILAMENT")

    // Forward slots — wire these up as the corresponding features gain buttons,
    // so future hardcoding lands here instead of in a screen:
    // val parkToolhead = Slot("PARK")          // pre-maintenance park
    // val purgeLine    = Slot("PURGE_LINE")    // pre-print purge if exposed in UI
}
```

Mechanical rewiring (all four sites):
`PrinterCommands.loadFilament() = CommandMap.loadFilament.gcode`;
`MacroPresent(CommandMap.loadFilament.macro)`;
`ExtrudeHolder` gates via the same constants. Add a unit test proving a renamed slot both gates
and emits the new string (the existing `MacroHolderTest`/`ExtrudeHolderTest` patterns cover this
shape). Document the file prominently in README ("Customizing for your printer") — that doc
section is as much the deliverable as the code.

**Explicit non-goals** (so scope doesn't creep back): no remapping of safety commands
(`M112`/`emergency_stop` stays absolute), no per-command UI, no arbitrary macro→button builder
(that's what the existing dynamic Macros screen already does), no runtime override store.

---

## Part 4 — Roadmap: sequenced work packages

Ordering logic: ship-blockers for the modern-phone story first (they're also the smallest),
then the open-source wrapper, then the backfills. R1+R2 roughly equal the already-planned
Phase-22 ship items; this roadmap folds the audit's new findings (insets, predictive back,
notifications) into them rather than inventing a parallel track. Each package = one CLI session
unless noted.

### R1 — Install & display correctness on modern devices *(merges with Phase-22 ABI todo)*
1. ABI: `include("armeabi-v7a", "arm64-v8a")`, keep `isUniversalApk = false`; release CI/script
   publishes **both** per-ABI APKs as separate GitHub Release assets with clear names. Fix the
   `D-01a` comment to distinguish dev-floor from ship artifact.
2. Edge-to-edge: `enableEdgeToEdge()` in `MainActivity.onCreate`; wrap the root `Box` in
   `Modifier.safeDrawingPadding()` (covers status/nav bars *and* cutouts); verify the four
   Views-hosted surfaces inherit correctly (they're inside the Compose root, so they should).
3. Predictive back: add `android:enableOnBackInvokedCallback="true"` on `<application>`.
4. `POST_NOTIFICATIONS`: one-shot runtime request via Activity Result API at first connect;
   denial must change nothing except the notification.
- **Acceptance:** signed release APK installs and renders chrome-clear (no content under
  cutout/status/nav) on an arm64-only Android 15/16 phone in both orientations; back-gesture
  preview animates drawer-close and stack-pop; Nexus 7 behavior unchanged; `verifyMinSdkRelease`
  green.

### R2 — Always-on survival *(= PKG-03, already researched in PITFALLS.md Pitfall 5)*
1. `FLAG_KEEP_SCREEN_ON` on MainActivity's window, gated by a Settings toggle ("Keep screen
   awake", default ON for the dedicated-display use case), cleared in `onPause`.
2. Declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; add a first-run/Settings step that deep-links
   to the exemption dialog with one explanatory sentence; surface current exemption state.
3. UAT per PITFALLS: unplugged, screen off, 20+ min → connection alive or clean resync on wake.
- **Acceptance:** the doze test passes on both the Nexus 7 (LineageOS) and a modern phone.

### R3 — Phone-size layout polish
1. M1 file-row height, M2 macro grid (fixes above).
2. A 360dp-width sweep: run every existing preview at `widthDp = 360` (add the device spec to the
   shared preview annotation set) and fix what clips; spot-check `fs=L` on the same width.
3. M8: document the OS-font-scale decision; seed default S/M/L from `fontScale` on first run.
- **Acceptance:** preview matrix at 360dp clean for all previewed screens; no overflow at fs=L.

### R4 — Build-time command map *(Part 3 design)*
- New `CommandMap.kt`, rewire 4 sites, unit test renamed-slot gating+emission, README
  "Customizing for your printer" section.
- **Acceptance:** changing one constant in one file makes a renamed load-filament macro gate and
  fire correctly with no other edits; test proves it.

### R5 — Open-source wrapper *(do before the repo goes public; 1–2 sessions)*
1. `git update-index --chmod=+x gradlew` (one command, do it first).
2. **LICENSE** (your call — GPLv3 fits the Klipper ecosystem's norms; MIT/Apache-2.0 maximizes
   adoption. Decide before public.)
3. `README.md`: what/why, screenshots (`img/` exists), supported devices + the two-APK ABI story,
   build (`./gradlew assembleDebug`, `local.properties` moonraker.* keys, no other setup),
   Gallery activity for printerless UI browsing, the command-map pointer, the a11y/font-scale note.
4. `CONTRIBUTING.md`: architecture tour (Holder+StateFlow, AppContainer, TopRoute), the
   preview-first convention with PrintStatusPreviews as the template, string tokenization +
   pseudolocale check, **the comment-convention decoder** (`D-##` = design decision, `SC-##` =
   scaffold concern, `Phase ##` → `.planning/phases/`), test expectations.
5. `.github/workflows/ci.yml`: ubuntu-latest, JDK 17, `./gradlew :app:testDebugUnitTest
   :app:assembleDebug` on push/PR; lint job under JDK 17 toolchain (M6), non-blocking at first.
- **Acceptance:** fresh clone on a clean Linux machine: `./gradlew assembleDebug` succeeds with
  no instructions beyond the README; CI green on the PR that adds it.

### R6 — Preview & string backfill *(mechanical; parallelizable across sessions; = Phase-22 SC items)*
- 18 missing `*Previews.kt` files from the exemplar template (6 theme combos + fs=L per screen);
  extract the 16 remaining string literals; run the `en-XA` pseudolocale sweep on device to close.
- **Acceptance:** every screen has its matrix; pseudolocale build shows zero plain-English chrome.

### R7 — Network posture
- Scope NSC cleartext to RFC-1918 + the configured host; add `https/wss` toggle to connection
  settings (OkHttp handles TLS; mostly URL-scheme plumbing + cert-failure UX).
- **Acceptance:** cleartext to a public IP refused on API 24+; wss:// connect succeeds against a
  TLS-fronted Moonraker.

### R8 — Dependency runway *(document now, execute later — keep as a standing doc section, not a session)*
- **The coordinated jump** (one future phase, never piecemeal): AGP 9 + compileSdk 37 + Compose
  BOM ≥ 2026.06 (Compose 1.12) + Gradle 8.10+ + lint re-enable. Trigger: when Compose 1.11 line
  stops receiving fixes, or a needed lib requires compileSdk 37.
- **Floor-dependent:** zxing 3.4+ only after minSdk ≥ 24 (or add coreLibraryDesugaring); revisit
  the floor itself only if Nexus-7-class hardware stops being the project's soul.
- **Routine:** Coil 3.x, OkHttp, kotlinx.* track latest stable; re-run `verifyMinSdkRelease`
  after any bump (already enforced).
- **One-time check:** 16KB page behavior on a Pixel-9-class device once arm64 ships.
- Low advisory: console eviction `subList.clear()` swap whenever that file is next touched.

### Explicit "do NOT do" list (decisions, recorded so future sessions don't relitigate)
- No Material `WindowSizeClass` — continuous `BoxWithConstraints` is working and finer-grained.
- No Hilt, no Navigation-Compose, no module split — the manual graph and `TopRoute.derive()` are
  assets at this size; revisit only past ~2× current scope.
- No changes to the cadence layer, the subscribe set, or the render hot paths without a
  measured regression on the Nexus 7.
- No runtime gcode remap UI (R4 is build-time by decision); no WebRTC; no Play-services deps.
