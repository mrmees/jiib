---
phase: 8
slug: macros-console-functional-core-complete
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-02
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 (host) + kotlinx-coroutines-test (`runTest`) + androidx instrumented / Compose ui-test-junit4 (on-device, flox) |
| **Config file** | `gradle/libs.versions.toml` (pinned); no separate test-runner config |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:assembleRelease --no-daemon"` (+ `connectedAndroidTest` for instrumented gates on flox) |
| **Estimated runtime** | ~60–120 seconds (unit suite); release assemble adds ~minutes; build is Windows-side via the gw.bat helper (`./gradlew` does NOT run from WSL — see CLAUDE.md) |

---

## Sampling Rate

- **After every task commit:** Run `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` (scoped `--tests *<TestName>*` per the task's `<verify>` for speed; pure parsers/classifiers/holders are all host-testable)
- **After every plan wave:** Run `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:assembleRelease --no-daemon"`
- **Before `/gsd-verify-work`:** Full unit suite green + `:app:assembleRelease` BUILD SUCCESSFUL + on-device UAT on flox + live Ender 5 (08-07 Task 2)
- **Max feedback latency:** ~120 seconds (unit suite). Build output prints CR progress bars — pipe through `tr -d '\r'`; the process exit code is authoritative.

> **Live/on-device gate is non-negotiable here.** Two prior phases (2 and 5) shipped green unit suites that hid live Moonraker protocol bugs. The 08-01 live probe (RED fixtures from the real printer) + the 08-07 on-device UAT are mandatory backstops.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 0 | CONS-02 / MACRO-02 | T-08-01-I | Read-only LAN probe; recorded shapes carry no secrets | manual (read-only curl) + recorded | `curl …/server/gcode_store` + `…/objects/query?configfile` → docs/moonraker-capabilities.md | ❌ W0 | ⬜ pending |
| 08-01-02 | 01 | 0 | CONS-02 / MACRO-02 | T-08-01-T | Parsers skip-bad-field tolerant; RED until impl | unit (RED scaffold) | `:app:testReleaseUnitTest` (expect RED unresolved-refs) | ❌ W0 | ⬜ pending |
| 08-02-01 | 02 | 1 | CONS-02 | T-08-02-T | `classify` total — garbage prefix → NORMAL, never throws | unit (pure) | `:app:testReleaseUnitTest --tests *ConsoleSeverityTest* --tests *ConsoleFiltersTest*` | ❌ W0 | ⬜ pending |
| 08-02-02 | 02 | 1 | CONS-02 | T-08-02-D | Object-typed bounded ring; fixed regex set only | unit | `:app:testReleaseUnitTest --tests *ConsoleScrollbackTest*` | ❌ W0 | ⬜ pending |
| 08-03-01 | 03 | 1 | MACRO-02 | T-08-03-T3 | `parseMacroParams` total — never throws on garbage body | unit (pure) | `:app:testReleaseUnitTest --tests *MacroParamParserTest*` | ❌ W0 | ⬜ pending |
| 08-03-02 | 03 | 1 | MACRO-02 | T-08-03-T1 (block_on:high) | String-param injection (newline/M112) neutralized before scriptParams | unit (security) | `:app:testReleaseUnitTest --tests *MacroInvocationTest*` | ❌ W0 | ⬜ pending |
| 08-03-03 | 03 | 1 | MACRO-03 | — | Fail-safe DataStore read → defaults, never throws | unit | `:app:testReleaseUnitTest --tests *MacroPrefsTest*` | ❌ W0 | ⬜ pending |
| 08-04-* | 04 | 2 | CONS-02 / MACRO-02 | T-08-01-T | gcode_store backfill + macro-body reads skip-bad-field tolerant | unit | `:app:testReleaseUnitTest --tests *GcodeStoreParseTest*` (+ handshake-read tests) | ❌ W0 | ⬜ pending |
| 08-05-01 | 05 | 3 | CONS-02 | T-08-05-T | Holder stores RAW (no filter, D-04); replace-backfill recovers disconnect-window lines | unit | `:app:testReleaseUnitTest --tests *ConsoleHolderTest*` | ❌ W0 | ⬜ pending |
| 08-05-02 | 05 | 3 | CONS-02 | T-08-05-T | Printer text rendered plain-text (no markup surface) | build (assemble) | `:app:assembleRelease` | n/a (build) | ⬜ pending |
| 08-05-03 | 05 | 3 | CONS-02 | T-08-05-I | Read-only — no keyboard/send (D-01); filters at render only (D-04) | build (assemble) | `:app:assembleRelease` | n/a (build) | ⬜ pending |
| 08-06-01 | 06 | 3 | MACRO-01 / MACRO-03 | — | Capability-gated; underscore-default-hide | unit | `:app:testReleaseUnitTest --tests *MacroHolderTest*` | ❌ W0 | ⬜ pending |
| 08-06-02 | 06 | 3 | MACRO-01 / MACRO-02 | T-08-06-T1 (block_on:high) | Execute routes raw param → MacroInvocation.build → scriptParams (sanitizer in path) | build (assemble) | `:app:assembleRelease` | n/a (build) | ⬜ pending |
| 08-06-03 | 06 | 3 | MACRO-01 / MACRO-03 | — | Selected-state accent; no dead tiles when capability absent | build (assemble) | `:app:assembleRelease` | n/a (build) | ⬜ pending |
| 08-07-01 | 07 | 4 | MACRO-03 | — | [B1] macros.preferences_pb DataStore wired (DinghyApp→AppContainer.macroPrefs→MacroHolder); bookmarks persist across restart | build (assemble) | `:app:assembleRelease` | n/a (build) | ⬜ pending |
| 08-07-02 | 07 | 4 | MACRO-01..03 / CONS-02 | — | Drawer-swipe suppressed on scroll-Fields; session-owned holders; nav wiring | build (assemble) | `:app:assembleRelease` | n/a (build) | ⬜ pending |
| 08-07-03 | 07 | 4 | MACRO-01..03 / CONS-02 | T-08-07-T (block_on:high) / T-08-07-A | On-device: injection neutralized live; console backfills/recovers; scroll holds Adreno-320 floor | manual (on-device UAT + gfxinfo perf) | flox + live Ender 5; `adb shell dumpsys gfxinfo works.mees.dinghy framestats` | n/a (manual) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

RED scaffolds are created in **08-01 Task 2** (pure parsers/classifiers/holders) referencing not-yet-built production symbols so they compile RED, then turned GREEN by the implementation tasks:

- [ ] `app/src/test/java/works/mees/dinghy/ui/macros/MacroParamParserTest.kt` — REQ-MACRO-02 (fixtures from REAL probed macro bodies, `macro_bodies_e5.json`, not invented)
- [ ] `app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt` — REQ-MACRO-02 string-param injection sanitization (security, block_on:high)
- [ ] `app/src/test/java/works/mees/dinghy/ui/console/ConsoleSeverityTest.kt` — REQ-CONS-02 prefix→tier classifier
- [ ] `app/src/test/java/works/mees/dinghy/ui/console/ConsoleFiltersTest.kt` — REQ-CONS-02 built-in filter rules + D-04 raw-survives
- [ ] `app/src/test/java/works/mees/dinghy/ui/console/GcodeStoreParseTest.kt` — REQ-CONS-02 wire-shape walk (fixture = the confirmed probe shape, `gcode_store_e5.json`)
- [ ] `app/src/test/java/works/mees/dinghy/state/ConsoleScrollbackTest.kt` — object-typed bounded ring eviction/replaceAll/defensive-snapshot (turned GREEN by 08-02 Task 2)
- [ ] `app/src/test/java/works/mees/dinghy/ui/console/ConsoleHolderTest.kt` — collect live + replace-backfill; raw-store (D-04) (turned GREEN by 08-05 Task 1)
- [ ] `app/src/test/java/works/mees/dinghy/ui/macros/MacroHolderTest.kt` — combine capabilities/prefs/bodies; underscore-default-hide (turned GREEN by 08-06 Task 1)
- [ ] `app/src/test/java/works/mees/dinghy/ui/macros/MacroPrefsTest.kt` — bookmarks/revealHidden round-trip (turned GREEN by 08-03 Task 3)
- [ ] Test fixtures `app/src/test/resources/fixtures/gcode_store_e5.json` + `macro_bodies_e5.json` — REAL probed shapes from the 08-01 live probe (NOT invented strings)
- [ ] Live read-only probe of `server.gcode_store` + `objects/query?configfile` recorded into `docs/moonraker-capabilities.md` (08-01 Task 1)
- [ ] Instrumented console-scroll perf capture on flox (FrameTimingMetric may not work on API 23 → fall back to `gfxinfo framestats` per CLAUDE.md) (08-07 Task 2)

*Framework already present — no install needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live `server.gcode_store` + `configfile` macro-body JSON shape confirmed | CONS-02 / MACRO-02 | Requires the live printer; the recurring mock-vs-reality bug class (Phases 2 & 5) demands real-shape fixtures | `curl -s "http://192.168.1.120:7125/server/gcode_store?count=20" \| python3 -m json.tool`; `curl -s "http://192.168.1.120:7125/printer/objects/query?configfile" \| python3 -m json.tool \| grep -A4 '"gcode_macro'` → record verdict (string vs array) into docs (08-01 Task 1) |
| Console backfills from real gcode_store + recovers disconnect-window lines on reconnect (SC #3) | CONS-02 | Reconnect behavior + real server buffer can only be exercised against a live Moonraker | 08-07 Task 2 step 1/4: open Console populates from history; yank Wi-Fi / FIRMWARE_RESTART then restore → history backfills, no silent drop |
| Severity coloring + filter toggle ON→OFF re-reveal (D-04) | CONS-02 | Visual + requires live temp/echo lines | 08-07 Task 2 steps 2–3: `!!` red / `// ` amber / plain text; Hide-temps ON hides → OFF re-reveals |
| A real macro WITH params executes; rejection → toast + console line | MACRO-01 / MACRO-02 | Real printer must accept/reject the gcode | 08-07 Task 2 step 5: bookmark → run a parametered macro → printer executes; a bad macro shows SeverityToast + console line |
| String-param injection (newline / M112) does NOT fire extra gcode (security gate) | MACRO-02 | On-device backstop to the `MacroInvocationTest` unit gate (block_on:high) | 08-07 Task 2 step 6: type a value with a newline / `M112` → confirm NO emergency-stop/extra-command fires |
| Console scroll p95 / frozen-frame count on the Adreno-320 floor | CONS-02 | Emulators lie about old-GPU perf; must measure on real flox | 08-07 Task 2 step 7: `adb shell dumpsys gfxinfo works.mees.dinghy framestats` during live line flow; record p95/max/frozen vs the Files gate (07-06: ~15ms p95, 0 frozen) |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (RED scaffolds in 08-01 Task 2; UI/build tasks gated by `:app:assembleRelease`; the two genuinely manual gates are the live probe and on-device UAT)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every wave has unit and/or assemble gates)
- [x] Wave 0 covers all MISSING references (9 RED test files + 2 fixtures + the live probe + the perf capture)
- [x] No watch-mode flags (`--no-daemon`, one-shot runs)
- [x] Feedback latency < 120s (scoped `--tests` per task)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-06-02
