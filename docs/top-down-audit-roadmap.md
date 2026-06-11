# Top-Down Audit & Roadmap — June 2026

**What this is:** a whole-repo, top-down audit of Dinghy Display against four goals —
cross-device compatibility, runtime efficiency, open-source maintainability, and
future-friendliness — plus the design for the build-time gcode **command map**. It ends with a
sequenced set of work packages (R1–R10), each sized for one CLI session and written so it can be
fed to `/gsd-execute-phase` (or quoted directly at the CLI) as-is.

*Amended 2026-06-10:* added **R9 (memory/docs delint)** from the follow-up corpus audit, and
folded the adoptable items from external ChatGPT/Gemini review input into R5/R6 (lint baseline
discipline, StrictMode, token-purity rule, scoped context-hygiene CI). Everything else in that
input was either already implemented (cadence throttling, R8, semantic tokens), already found by
this audit (ABI), or rejected (doc fan-out, toolpath advice — see "do NOT do").

*Re-sequenced 2026-06-10 (same day):* Part 4 now lists packages in **execution order** —
R5a → R9 → R1 → R2 → R6 → R3 → R4 → R7 → R5b (R8 stays a standing doc section). R5 split into
**R5a** (guardrail infra, first) and **R5b** (README/CONTRIBUTING, last) to avoid three rework
traps: a late lint baseline freezing in earlier packages' violations, sessions executing against
un-delinted memory, and R3's 360dp sweep running before R6's previews exist.

*Reconciled 2026-06-11 against Phases 22–26* (the "pre-ship quality slate", ~250 commits since
this audit's baseline): ship renumbered **Phase 22 → Phase 29** (27 = motion+calibration,
28 = system/settings cluster); M9 god-files resolved (PrintStatusScreen split 22-04, NavHost
shell 24-03); this audit's efficiency verdict **corrected** (see TL;DR — the unstable
`PrinterState` tree WAS a real recomposition storm; Phase 22 fixed it); previews/strings largely
backfilled (Phases 25–26); M2 superseded by the Phase-25 Macros rebuild; two "do NOT do" entries
(Navigation-Compose, immutable collections) **overruled by Phases 24/22 — correctly**. New
**Part 5 (touch responsiveness)** + work package **R10** added from the owner-reported
tap-reliability investigation. Several R9 items were pre-empted on master (Tegra paragraph,
Phase-19 TBD, spoolman notify catalog, webcam todo pair) — struck in place.

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
| **Efficiency: redraw rate vs source rate?** | **Cadence layer excellent; one verdict corrected (2026-06-11).** The 250 ms two-plane conflation in `PrinterStateStore` is honored end-to-end; render surfaces are allocation-free; webcam decode stops when backgrounded; no polling, no looping animations. **But this audit's "collections are immutable by pattern, annotations not needed" call was wrong:** the unstable `PrinterState` tree made every 250 ms tick recompose the whole home screen + shell (28 collects in one scope) — the navigation-lag root cause, caught by the repo's own `.planning/codebase/CONCERNS.md` audit. Phase 22 fixed it (@Immutable tree + ImmutableMap/List, collection hoisting/stateScope, PrintStatusScreen split, AndroidView invalidate guards). → remaining: console-eviction micro-advisory (**R8**) |
| **Maintainable in Android Studio? Previews?** | **Architecture yes, wrapper no.** Holder+StateFlow pattern, manual DI, and pure-function routing are contributor-friendly; the test harness (fake socket + golden fixtures) is a standout. But: no README/CONTRIBUTING/LICENSE, no CI, `gradlew` committed without its executable bit, lint disabled, and **18 of 27 screens lack the @Preview matrices** your own convention doc mandates. → **R5a/R5b, R6** |
| **Future-friendly: modern phones?** | **One hard blocker:** the release APK is armeabi-v7a-only (`isUniversalApk = false`), so 64-bit-only devices (Pixel 7+, S25 Ultra — your own 2026-06-08 todo confirms) **cannot install it at all**. Plus: no doze/battery-exemption story for always-on use, no predictive-back opt-in, no POST_NOTIFICATIONS runtime request. Dependency cliffs are well-documented in `libs.versions.toml` already. → **R1, R2, R7, R8** |

**Command map (tokenization):** the groundwork is already done — `PrinterCommands.kt` centralizes
~99% of gcode behind clamped builders, and pause/resume/cancel go through Moonraker API methods
(`printer.print.*`), which already respect a user's overridden PAUSE/RESUME/CANCEL_PRINT macros
for free. The remap surface is genuinely small: extract it into one documented file. → **R4**

---

## Part 1 — What is already done well (do not rebuild)

These came back clean across all five audits. Future phases should *protect* these, not revisit them.

*(Status 2026-06-11: verified pre-Phase-22; still true with three deltas — #1's cadence is intact
but the TL;DR's recomposition correction applies on top of it; #4's Gutter region was retired by
the Phase-23 design-kit rework — the law is now two regions + `FootButtonBar` + the `U` unit grid,
see `docs/ui_design/LAYOUT.md` + the new `COMPONENTS.md`; #10's BackHandler wiring now lives in
the Phase-24 NavHost shell.)*

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

*(Status 2026-06-11, post Phases 22–26: **C1/C2/C3, H1, H3, M1, M3–M6, M8 remain OPEN** —
the quality slate was design/perf work, not these items. **H2 largely CLOSED:** 17 `*Previews.kt`
files now cover the rebuilt screens; remaining gaps are Move + the Calibration cluster,
deliberately deferred until their Phase-27 redesign. **M2 SUPERSEDED:** the Phase-25 Macros
rebuild replaced the grid with ListBlock/ListRow. **M7 partially closed** by Phase-26 WR-11;
the Views-layer strings in `FileRowsAdapter` remain. **M9 largely CLOSED:** PrintStatusScreen
1584 → 657 lines (22-04), AppShell's ~1000-line `when(dest)` replaced by NavHost (24-03,
now 962 lines); CommandRegistry unchanged and fine. Console-eviction advisory still open.)*

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

Ordering logic *(re-sequenced 2026-06-10)*: **guardrails before work, memory before execution,
previews before the layout sweep, docs last.** The original R1-first sequence had three rework
traps: a lint baseline generated late would freeze in violations introduced by earlier packages;
the docs delint must precede the CLI sessions that read those docs as memory; and R3's 360dp
sweep needs R6's previews to exist or it runs twice. Package names are identities, not order —
the sections below appear in **execution order**. R1+R2 roughly equal the already-planned
Phase-22 ship items; this roadmap folds the audit's new findings (insets, predictive back,
notifications) into them rather than inventing a parallel track. Each package = one CLI session
unless noted.

| Order | Package | Gate it clears |
|---|---|---|
| 1 | **R5a** guardrail infra | CI/lint/StrictMode exist before any code lands; baseline starts minimal |
| 2 | **R9** memory/docs delint | the memory is true before sessions consume it |
| 3 | **R10** touch responsiveness | taps land reliably with immediate feedback (live UX pain; touches components Phases 27–28 build on) |
| 4 | **R1** install & display correctness | modern phones can install and render it |
| 5 | **R2** always-on survival | the ship pair (now Phase 29) is complete |
| 6 | **R6** preview + string backfill | *largely done by Phases 25–26* — close the Move/Calibration + Views-strings remainder after Phase 27 |
| 7 | **R3** phone-size polish | one complete 360dp/fs=L pass over all screens |
| 8 | **R4** command map | fork-and-edit customization ready for the README |
| 9 | **R7** network posture | hardening before strangers run it |
| 10 | **R5b** README + CONTRIBUTING | the "go public" gate — written once, against final state |
| — | **R8** dependency runway | standing doc section, no execution slot |

R1↔R2 and R4↔R7 are order-free pairs — swap within a pair freely. R9's optional bulk-archive
step may trail anytime; only its correctness edits are order-critical. R6's remainder and R3
should wait until the Phase-27/28 redesigns land (don't preview/polish screens about to be
rebuilt).

### R10 — Touch responsiveness *(owner-reported; full analysis in Part 5)*
1. **Instrument before fixing** (one debug session on flox): enable Developer Options "Show
   taps" + screen-record; add pointer-event logging to the shell swipe detector and one stepper;
   correlate missed taps against (a) busy/debounce windows, (b) the 150 ms Crossfade morph,
   (c) frame drops. Part 5 has the recipes — the fixes below are ranked hypotheses, not
   confirmed causes, until this runs.
2. **Kill the silent drops (highest-confidence fix):** thread a real `enabled` param through
   `OutlinedControl`/stepper rows so disabled controls aren't clickable at all (today they
   ripple, then swallow the click inside `if (controlsEnabled)` — AdjusterPanel); make
   `CommandDispatcher`'s in-flight + 400 ms debounce rejections produce visible feedback
   (brief dim-flash or settle tick) instead of dropping taps invisibly
   (`CommandDispatcher.kt:108–116`).
3. **Harden the shell swipe-up detector** (`AppShell.kt`, `SWIPE_UP_THRESHOLD_PX = 80f`):
   accumulate drag across the gesture instead of requiring a single event's `dragAmount > 80f`
   (the per-event check is already proven fragile — it's why `FineTuneNavTest`'s `swipeUp()`
   can't open the drawer); fail fast on consumed downs (`requireUnconsumed`).
4. **Indication immediacy:** explicit fast ripple/indication on `ListRow`/`OutlinedControl`
   inside scrollable containers (Compose delays press indication in scrollables by design —
   on a janky Adreno 320 that delay reads as a missed tap).
5. **Crossfade tap-guard** on the home morph only if step 1 implicates it.
- **Acceptance:** 20-tap torture run per control class on flox: ≥95% registered with visible
  same-frame feedback; intentional rejections (busy/debounce) show feedback instead of nothing;
  drawer swipe still opens reliably; `FineTuneNavTest` passes on-device.

### R5a — Guardrail infra *(run FIRST; the infra half of the former R5)*
1. `git update-index --chmod=+x gradlew` (one command, do it first).
2. **LICENSE** (your call — GPLv3 fits the Klipper ecosystem's norms; MIT/Apache-2.0 maximizes
   adoption. Decide before public.)
3. `.github/workflows/ci.yml`: ubuntu-latest, JDK 17, `./gradlew :app:testDebugUnitTest
   :app:assembleDebug` on push/PR.
4. Lint, baselined (M6): run lint under a **JDK 17 toolchain** (the AGP-8.7 UAST crash is
   JDK-21-specific), generate `lint-baseline.xml`, then flip `abortOnError = true` +
   `checkReleaseBuilds = true` with the baseline in place. House rule: **the baseline may only
   shrink, never grow** — CI fails on any new violation. Landing this before R1–R4 is what keeps
   the baseline minimal.
5. `StrictMode` (detect-all + penaltyLog) in `DinghyApp` for debug builds only — the main thread
   is currently clean (efficiency audit); this keeps it that way for free.
- **Acceptance:** fresh clone on a clean Linux machine: `./gradlew assembleDebug` succeeds; CI
  green on the PR that adds it; lint baseline checked in and enforced.

### R9 — Memory/docs delint *(run SECOND — from the 2026-06-10 corpus audit: ~660 files → ~10 misleading, ~15 obsolete, ~200 scaffolding, ~85 load-bearing, rest harmless archive)*

1. **Correctness edits (~10 files — these lie to the very sessions that will execute R1–R8):**
   - `research/STACK.md`: compileSdk 35 → 36 sweep (the file root CLAUDE.md's stack section was
     copied from; they have drifted since Phase 21).
   - Root `CLAUDE.md`: the leftover "compileSdk-35 line" phrase in *What NOT to Use*; replace
     "Architecture not yet mapped" with pointers to `research/ARCHITECTURE.md` + ADR-0001; soften
     the "Baseline Profile = NO-OP" framing — true for stock API 23, but flox runs LineageOS
     API 30 where profiles DO apply (this also raises the priority of the
     `macrobenchmark-module-wiring` todo).
   - ~~`.planning/PROJECT.md` Tegra paragraph~~ **DONE on master** (corrected 2026-06).
   - ~~`.planning/REQUIREMENTS.md` Phase-19 "TBD"~~ **DONE on master** (now "shipped 2026-06-08").
   - Mark UI-SPECs 07–16 `status: superseded / superseded_by: docs/ui_design/` (04's already is;
     verified nothing in any of the 8 isn't covered by `docs/ui_design/`). Note the design law
     itself evolved in Phase 23 (Gutter retired, unit grid added) — making the supersession
     markers MORE urgent, since those specs are now two generations stale.
   - ~~spoolman notify entries in `docs/commands/moonraker-api.md`~~ **DONE on master**
     (lines ~166–188); the `request-cadence-contract.md` table row is still missing.
   - **NEW:** stamp `.planning/codebase/CONCERNS.md` + `UI-REVIEW.md` (the Phase-22-25 audit
     inputs) as `Status: historical — findings resolved by Phases 22–26`; CONCERNS.md still
     reads as a list of open CRITICALs and has already misled one analysis session into
     reporting the @Immutable migration as un-landed.
2. **Todo re-triage:** ~~close webcam-screen-crash + webcam-tile-gating~~ **DONE on master**;
   still open: archive the 4 stale untargeted todos (`status-progress-ring-dual-source-jump`,
   `console-macro-page-ux-flow`, `benchmark-harness-fairness-fixes`,
   `shellpresencetest-device-determinism` — the last one becomes R10 step 3's test fix); 17
   currently pending — re-triage against the new Phase-27/28/29 map. Adopt the rule:
   **re-triage `todos/pending/` at every phase close.**
3. **Single-sourcing + metadata headers:** assign one owner per fact — root CLAUDE.md owns
   stack + constraints; REQUIREMENTS.md owns core value + requirements; STATE.md owns status;
   ROADMAP.md owns phases — and delete the duplicated Constraints/Core-Value sections from
   PROJECT.md (currently stated in triplicate; the compileSdk drift proves the mechanism). Add a
   `Last verified: YYYY-MM-DD / Status: active|historical` header to the ~15 active memory files;
   mark all 5 `research/*` files historical instead of editing their content.
4. **Bulk archive (optional; may trail any later package):** delete the ~26 DISCUSSION-LOG files
   (each self-declares "audit trail only, do not use as input"); move shipped-phase
   CONTEXT/VALIDATION/VERIFICATION/REVIEW files to `.planning/archive/`, keeping PLAN/SUMMARY
   pairs in place. **Protected list — never move or delete:** `phases/01/captures/*` (cited by
   ADR-0001), `phases/13/captures/*` (cited by the cadence contract), `15.2-AUDIT.md` (cited by
   THEMING.md — update that link if anything moves), PATTERNS 02/03/18 (foundational), all
   `docs/commands/spoolman-live-*.json` + e3/e5 `.jsonl` (consumed by the test suite via
   `FakeSpoolmanClient`), and PLAN/SUMMARY pairs (code comments reference task IDs like
   "13-05 Task 3").
5. **Hygiene guardrail in CI (scoped; depends on R5a's CI):** a stale-phrase grep +
   `markdown-link-check` for dead internal references, run ONLY against the active context set
   (root CLAUDE.md, `docs/*.md`, the four `.planning/` root files) — **never** against
   `.planning/phases/`, where historical vocabulary ("superseded", "deferred", "old approach")
   is correct and would false-positive.
- **Acceptance:** zero contradictions among active memory files; every active file carries a
  current `Last verified` header; the 2 done todos closed and 4 stale ones archived; hygiene
  check green in CI.

### R1 — Install & display correctness on modern devices *(merges with the arm64 ABI todo; ship is now Phase 29)*
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

### R2 — Always-on survival *(= PKG-03, lands with Phase 29; already researched in PITFALLS.md Pitfall 5)*
1. `FLAG_KEEP_SCREEN_ON` on MainActivity's window, gated by a Settings toggle ("Keep screen
   awake", default ON for the dedicated-display use case), cleared in `onPause`.
2. Declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; add a first-run/Settings step that deep-links
   to the exemption dialog with one explanatory sentence; surface current exemption state.
3. UAT per PITFALLS: unplugged, screen off, 20+ min → connection alive or clean resync on wake.
- **Acceptance:** the doze test passes on both the Nexus 7 (LineageOS) and a modern phone.

### R6 — Preview & string backfill *(LARGELY DONE by Phases 25–26 as of 2026-06-11; runs BEFORE R3 so the sweep covers all screens)*
- ~~18 missing `*Previews.kt` files~~ → 17 `*Previews.kt` files now exist covering the rebuilt
  screens. **Remainder:** Move + the Calibration cluster (do them WITH their Phase-27 redesign,
  not before), the Views-layer strings in `FileRowsAdapter` ("SEL"/"UP"/"DIR"/"GCO" etc.), and
  the on-device `en-XA` pseudolocale sweep to close.
- Optional enforcement: a custom detekt/lint rule flagging raw `Color(0x…)`/hex literals and raw
  `.sp` values not routed through `fsSp()` inside `ui/` — turns the manual Phase-15.2
  token-conformance audit (`15.2-AUDIT.md`) into a self-enforcing gate.
- **Acceptance:** every screen has its matrix; pseudolocale build shows zero plain-English chrome.

### R3 — Phone-size layout polish *(uses R6's full preview deck)*
1. M1 file-row height, M2 macro grid (fixes above).
2. A 360dp-width sweep: run every preview at `widthDp = 360` (add the device spec to the shared
   preview annotation set) and fix what clips; spot-check `fs=L` on the same width.
3. M8: document the OS-font-scale decision; seed default S/M/L from `fontScale` on first run.
- **Acceptance:** preview matrix at 360dp clean for all 27 screens; no overflow at fs=L.

### R4 — Build-time command map *(Part 3 design)*
- New `CommandMap.kt`, rewire 4 sites, unit test renamed-slot gating+emission, README
  "Customizing for your printer" section.
- **Acceptance:** changing one constant in one file makes a renamed load-filament macro gate and
  fire correctly with no other edits; test proves it.

### R7 — Network posture
- Scope NSC cleartext to RFC-1918 + the configured host; add `https/wss` toggle to connection
  settings (OkHttp handles TLS; mostly URL-scheme plumbing + cert-failure UX).
- **Acceptance:** cleartext to a public IP refused on API 24+; wss:// connect succeeds against a
  TLS-fronted Moonraker.

### R5b — README + CONTRIBUTING *(run LAST — the "go public" gate; the docs half of the former R5)*
Written once, against the finished state: the two-APK ABI story (R1), the keep-awake setting
(R2), the command map (R4), and the post-R9 doc structure (so it cites nothing the delint
archived).
1. `README.md`: what/why, screenshots (`img/` exists), supported devices + the two-APK ABI story,
   build (`./gradlew assembleDebug`, `local.properties` moonraker.* keys, no other setup),
   Gallery activity for printerless UI browsing, the command-map pointer, the a11y/font-scale note.
2. `CONTRIBUTING.md`: architecture tour (Holder+StateFlow, AppContainer, TopRoute), the
   preview-first convention with PrintStatusPreviews as the template, string tokenization +
   pseudolocale check, **the comment-convention decoder** (`D-##` = design decision, `SC-##` =
   scaffold concern, `Phase ##` → `.planning/phases/`), test expectations.
- **Acceptance:** a stranger can clone, build, and submit a compliant PR with no instructions
  beyond the two files; the repo flips public only after this lands.

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
  *(Still holds post-Phase-26.)*
- No Hilt, no module split — the manual graph is an asset at this size; revisit only past ~2×
  current scope. *(Still holds.)* ~~No Navigation-Compose~~ — **overruled by Phase 24, correctly:**
  once the destination count and back-stack semantics outgrew the `when(dest)` hub, NavHost was
  the right call (this audit's rationale was sized to the pre-redesign shell). Likewise
  ~~immutable collections not needed~~ — **overruled by Phase 22**; the original verdict was wrong
  (see TL;DR correction).
- No changes to the cadence layer (`DEFAULT_SAMPLE_MS = 250` — unchanged and re-validated through
  Phase 22), the subscribe set, or the render hot paths without a measured regression on the
  Nexus 7.
- No runtime gcode remap UI (R4 is build-time by decision); no WebRTC; no Play-services deps.
- No new top-level context-doc fan-out (PROJECT_BRIEF.md, DEVICE_TARGETS.md,
  PERFORMANCE_BUDGETS.md, …): the corpus audit diagnosed *triplicated* facts as the main drift
  disease — consolidate into fewer canonical files (R9 step 3), never more.
- No model-driven PR-review bot in CI; the deterministic gates (tests, lint baseline,
  token-purity rule, hygiene check) cover it for a solo-maintainer repo.

---

## Part 5 — Touch responsiveness (owner-reported 2026-06-11)

**Symptom (flox / Nexus 7 2013):** quick taps are hit-or-miss; press-and-hold reliably works.
Static analysis ranked the candidate mechanisms below. **Important:** the top-confidence items
are *silent intentional rejections that look like misses*, not lost events — which matches
"hold works" (by the time a hold settles, the busy/debounce window has expired and the retry
lands). Run R10 step 1's instrumentation before trusting any single cause.

### Ranked causes (evidence-backed first)

| # | Mechanism | Type | Evidence | Confidence |
|---|---|---|---|---|
| 1 | **Busy-lock silent swallow.** AdjusterPanel steppers stay `clickable` while disabled — the tap ripples, then dies inside `if (controlsEnabled)`; the 0.38-alpha dim + `semantics{disabled()}` never removes the click handler. | Intentional rejection, zero feedback | `designsystem/components/AdjusterPanel.kt` (~100–107, ~192/200); Phase-26 WR-07 added the dim but not true disablement | HIGH (verified from code) |
| 2 | **Dispatcher debounce + in-flight guard.** `CommandDispatcher.dispatch` silently returns if the key is in-flight OR within the 400 ms debounce — rapid stepper taps (the exact "quick taps" pattern) are dropped by design with no UI signal. | Intentional rejection, zero feedback | `command/CommandDispatcher.kt:108–116` | HIGH (verified from code) |
| 3 | **Shell swipe-up detector fragility.** The drawer gesture requires a *single pointer event* with `dragAmount > 80f` (`SWIPE_UP_THRESHOLD_PX`); the detector sits over all content via `pointerInput` on the shell. Per-event thresholding is already proven brittle — `FineTuneNavTest`'s `swipeUp()` (~800px over 12 events) can't open the drawer. Interaction with child taps on a slow event loop is plausible but unproven. | Gesture fragility; possible tap interference | `ui/shell/AppShell.kt` (~523–554); 17-08-SUMMARY's deferred test defect | MEDIUM (drawer part verified; tap-loss part is hypothesis) |
| 4 | **Press-indication delay in scrollables.** Compose intentionally delays press indication inside scrollable containers (tap-vs-scroll disambiguation). On a 20–30 fps Adreno 320, that delay + dropped frames reads as "nothing happened" even when the click fires. | Feedback latency (perceived miss) | ListRow/OutlinedControl instances inside `verticalScroll`/`LazyColumn` (Files, Console, Macros, Calibration) | MEDIUM |
| 5 | **Crossfade morph hit-testing.** The 150 ms home morph (`PrintStatusScreen` `Crossfade(tween(150))`) spans 3–7 frames on this GPU; taps mid-transition can hit the outgoing layer. | Possible event loss in a narrow window | `ui/printstatus/PrintStatusScreen.kt` (~471) | LOW-MEDIUM (hypothesis) |
| 6 | **Views/Compose interop edge** on Files/Console RecyclerViews. | Rare event loss | `ui/files/FileListView.kt`, `ui/console/ConsoleListView.kt` | LOW |

### Instrumentation recipes (R10 step 1 — prove it before fixing)

1. **Show taps + screen record** (Developer Options) on flox: 20 quick taps per control class
   (stepper, ListRow, foot button, scrubber, drawer tile). Tap indicator visible but no response
   = silent rejection (#1/#2). No tap indicator = real event loss (#3/#5/#6, or digitizer).
2. **Log the rejection paths:** one-line logs at `CommandDispatcher.dispatch`'s two early
   returns and AdjusterPanel's `controlsEnabled=false` branch — count how many "missed" taps
   were actually rejected on purpose. Cheapest, highest-signal test.
3. **Pointer logging** on the shell `pointerInput` and one stepper (DOWN/UP timestamps): UP
   arriving >100 ms after DOWN under jank implicates frame pacing, not gesture logic.
4. **Hardware sanity check:** this is an old digitizer on a LineageOS build — run a multitouch
   tester / `getevent -lt` session to rule out dropped DOWN events at the kernel/input layer
   before blaming Compose. (A worn touchscreen produces exactly this symptom profile.)

### Fix directions (detail for R10 steps 2–5)

- **True disablement:** `enabled: Boolean` param on `OutlinedControl`/stepper rows → no
  `clickable` (and no ripple) when disabled; keep the dim.
- **Visible rejection:** when dispatch is suppressed (busy/debounce), emit a UI event the
  control renders as a brief flash/shake of the pending value — "heard you, still settling" —
  instead of nothing. Keep the dispatcher semantics unchanged (they protect the printer and the
  SBC; do NOT shorten the debounce as a first move).
- **Swipe detector:** accumulate `dragAmount` per gesture against the 80px threshold; this fixes
  both the test and real-world slow swipes, and removes any tap-window ambiguity. Also closes
  the `shellpresencetest/FineTuneNavTest` harness defect (R9's todo list).
- **Indication:** explicit immediate indication on kit controls inside scrollables.
- The Crossfade guard only if instrumentation implicates it — don't pay the complexity blind.
