---
phase: 28
slug: system-settings-cluster
status: planned
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-12
---

# Phase 28 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 host unit tests (Gradle `test` sourceset; no Robolectric) |
| **Config file** | `app/build.gradle.kts` (existing — no Wave 0 install needed) |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests <FilterClass> --no-daemon" \| tr -d '\r'` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" \| tr -d '\r'` |
| **Estimated runtime** | ~120–180 seconds (full suite, Windows-side Gradle) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command (targeted `--tests` filter for the touched area)
- **After every plan wave:** Run the full suite command
- **Before `/gsd-verify-work`:** Full suite must be green + `assembleDebug` compiles
- **Max feedback latency:** ~180 seconds

---

## Wave-0 posture (distributed, not a separate plan)

This phase is a deletion/migration phase, so the "Wave 0" test-infrastructure work is **folded into the
plan that owns each source change** — each test update lands ATOMICALLY with its source so the test
sourceset compiles at every commit ([[dinghy-wave0-red-scaffold-compile]]). Mapping:

- `HomeActionTest` count/order (8→11) → **28-03** (with the `buildIdleActions` extension)
- `NavDestRoundTripTest` + System → **28-02** (with `NavDest.System`)
- `PrintersModeToggleTest` (new pure state-machine test, written RED-first) → **28-06**
- `maxItems` test purge across the theme/config sourceset (verified `ProfileStore.Json` has
  `ignoreUnknownKeys=true` first) → **28-04** (with the schema deletion; full-suite gate)
- `AppDrawerOutputsGateTest` / `SwipeUpAccumulatorTest` (host) + `ShellPresenceTest` / `DrawerWebcamGatingTest` /
  `FineTuneNavTest` (androidTest, all drawer/swipe-dependent) deletion → **28-05** (same commit as source delete)
- `PrintStatusUiModelTest` `LauncherDest.Drawer` assertion removal → **28-05** (atomic with the enum variant deletion)
- `DinghyIconsTest` / `verify_ligatures.py` drift guard stays green on new tokens → **28-01**
- `@Preview` 6-combo + fsL matrices → each rebuild plan (28-02/06/07/08)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 28-01-T1 | 28-01 | 1 | — (D-21 icon law) | T-28-01-01 | Icon drift guard (unique alternates + all-membership) holds | unit | `gw.bat :app:testDebugUnitTest --tests *DinghyIconsTest` | DinghyIcons.kt / verify_ligatures.py | ⬜ pending |
| 28-01-T2 | 28-01 | 1 | — (D-09/D-12) | — | dense flags default-false, no call-site break | compile | `gw.bat :app:compileDebugKotlin` | ListRow.kt / TokenTextField.kt / strings.xml | ⬜ pending |
| 28-02-T1 | 28-02 | 2 | — (D-01) | — | System round-trips; absent from FOOT_GUN_DESTS | unit | `gw.bat :app:testDebugUnitTest --tests *NavDestRoundTripTest --tests *FootGunDestsTest` | NavDest.kt | ⬜ pending |
| 28-02-T2 | 28-02 | 2 | — (D-02/D-03/D-08) | T-28-02-02 | shell owns e-stop (no FloatingEStop in screen); static Focus | compile | `gw.bat :app:compileDebugKotlin` | SystemPageScreen.kt | ⬜ pending |
| 28-02-T3 | 28-02 | 2 | — (SC-1 preview) | — | 6-combo + fsL preview matrix compiles | compile | `gw.bat :app:assembleDebug` | SystemPagePreviews.kt | ⬜ pending |
| 28-03-T1 | 28-03 | 2 | — (D-05) | T-28-03-01 | three orphan rows always present; OpenDrawer intact | compile | `gw.bat :app:compileDebugKotlin` | HomeAction.kt | ⬜ pending |
| 28-03-T2 | 28-03 | 2 | — (SC-5 host) | T-28-03-01 | exact 11-row count + order asserted | unit | `gw.bat :app:testDebugUnitTest --tests *HomeActionTest` | HomeActionTest.kt | ⬜ pending |
| 28-04-T1 | 28-04 | 1 | — (D-17) | T-28-04-01/02 | maxItems axis deleted; 4-slot default at Palette boundary; migration-safe | compile | `gw.bat :app:compileDebugKotlin` | ThemePrefs/ThemeResolver/Profile/AppContainer | ⬜ pending |
| 28-04-T2 | 28-04 | 1 | — (D-17) | — | whole test sourceset compiles + passes after deletion | unit | `gw.bat :app:testDebugUnitTest` (full) | theme/config test files | ⬜ pending |
| 28-05-T1 | 28-05 | 3 | — (D-04) | T-28-05-01 | 7 drawer/swipe files (4 core + 3 androidTest) deleted atomically; OpenDrawer + LauncherDest.Drawer (+ test assertions) removed; androidTest has zero drawer/swipe hits | shell | `test ! -f AppDrawer.kt && ... && grep -rilE 'drawer\|swipeup' app/src/androidTest/` | (deletions) | ⬜ pending |
| 28-05-T2 | 28-05 | 3 | — (D-04/D-06) | T-28-05-02 | AppShell drawer-free; System wired; e-stop applies mid-print | compile | `gw.bat :app:compileDebugKotlin` | AppShell.kt | ⬜ pending |
| 28-05-T3 | 28-05 | 3 | — (D-01/D-06) | T-28-05-02 | System reachable from idle foot + standby tile + printing grid; onOpenDrawer retired at ALL callsites (`grep -r onOpenDrawer app/src/` = 0) | unit | `gw.bat :app:testDebugUnitTest` (full) | PrintStatusField.kt / PrintStatusScreen.kt | ⬜ pending |
| 28-06-T1 | 28-06 | 4 | — (D-13) | T-28-06-03 | mode-toggle state machine (arm/disarm/confirm) pure-tested | unit | `gw.bat :app:testDebugUnitTest --tests *PrintersModeToggleTest` | PrintersModeToggleTest.kt | ⬜ pending |
| 28-06-T2 | 28-06 | 4 | — (D-13/D-15) | T-28-06-01/02 | writeScope-only; ConfirmGuard delete; masked key; dense | compile | `gw.bat :app:compileDebugKotlin` | PrintersScreen.kt | ⬜ pending |
| 28-06-T3 | 28-06 | 4 | — (SC-1 preview) | — | Normal/Edit/Delete/Empty preview matrix compiles | compile | `gw.bat :app:assembleDebug` | PrintersPreviews.kt | ⬜ pending |
| 28-07-T1 | 28-07 | 4 | — (D-11/D-12) | T-28-07-01/02 | Settings one-page dense; writeScope-only; 15sp floor | compile | `gw.bat :app:compileDebugKotlin` | SettingsScreen.kt | ⬜ pending |
| 28-07-T2 | 28-07 | 4 | — (D-11) | T-28-07-02 | SysInfo dense scroll; About one-page + dev-enable + brandTint | compile | `gw.bat :app:compileDebugKotlin` | SystemInformationScreen.kt / AboutScreen.kt | ⬜ pending |
| 28-07-T3 | 28-07 | 4 | — (SC-1 preview) | — | three preview matrices compile | compile | `gw.bat :app:assembleDebug` | Settings/SysInfo/About Previews | ⬜ pending |
| 28-08-T1 | 28-08 | 4 | — (D-19) | T-28-08-03 | idle seed reactive; firstOrNull one-shot removed | compile | `gw.bat :app:compileDebugKotlin` | ThemeEditorScreen.kt | ⬜ pending |
| 28-08-T2 | 28-08 | 4 | — (D-16/D-18) | T-28-08-01/02 | S/V full-color persist + ARGB→HSV restore; dense scroll | unit | `gw.bat :app:testDebugUnitTest` (full) | ThemeEditorScreen.kt | ⬜ pending |
| 28-08-T3 | 28-08 | 4 | — (D-20 + preview) | — | dev cycler drag region restored; editor preview matrix | compile | `gw.bat :app:assembleDebug` | DevThemeCyclerOverlay.kt / ThemeEditorPreviews.kt | ⬜ pending |
| 28-09-T1 | 28-09 | 5 | SC-1/SC-5 | T-28-09-01 | verified-fresh release build; pre-gate green | release | `gw.bat :app:testDebugUnitTest :app:assembleRelease --rerun-tasks` | 28-UAT.md | ⬜ pending |
| 28-09-T2 | 28-09 | 5 | SC-1/SC-5 | T-28-09-02 | owner on-device UAT walk (manual gate) | manual | flox UAT | — | ⬜ pending |
| 28-09-T3 | 28-09 | 5 | SC-1/SC-5 | — | UAT results persisted per-item | doc | `grep -c 'resolved\|deferred\|failed' 28-UAT.md` | 28-UAT.md | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `HomeActionTest` count/order assertions updated alongside D-05 (8 → 11 idle rows) — research Pitfall 2 → **28-03**
- [x] Grep `maxItems` across the ENTIRE test sourceset before D-17 deletion lands (ThemePrefsFallbackTest / PaletteGoldenTest golden paths) — research Pitfall 1; whole-sourceset compile gate ([[dinghy-wave0-red-scaffold-compile]]) → **28-04**
- [x] Ligature drift guard (`DinghyIconsTest` / `verify_ligatures.py`) must stay green if any new icon tokens register (LauncherFineTune, System-page row tokens) → **28-01**
- [x] Drawer test deletion (`AppDrawer`/`SwipeUpAccumulator` host tests + `ShellPresenceTest`/`DrawerWebcamGatingTest`/`FineTuneNavTest` androidTests) lands in the same commit as the source deletion so BOTH the host AND androidTest sourcesets compile post-deletion (no surviving drawer/swipe assertion) → **28-05**
- [x] `LauncherDest.Drawer` enum variant + its `PrintStatusUiModelTest` assertions removed atomically; `onOpenDrawer` retired at all PrintStatusScreen/Field callsites (`grep -r onOpenDrawer app/src/` = 0) — Codex SS-1/SS-2/WR-1 → **28-05**
- [x] Verify `ProfileStore.Json` carries `ignoreUnknownKeys = true` BEFORE the maxItems persistence-axis deletion (stored-profile migration safety) — CONFIRMED present at `ProfileStore.kt:128`; re-checked in **28-04** Task 1

*Existing infrastructure covers host-test needs; no framework install required.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Restyled cluster (Settings/Theme/Printers/SysInfo/About + System page) owner approval, both orientations | SC-1 | Visual judgment on real hardware | flox on-device UAT, portrait + landscape (28-09 T2 step 1) |
| C6 densification reads well in-hand; Settings + About fit one page at M | SC-2/SC-3 | Density/legibility judgment | flox UAT at S/M/L text sizes (28-09 T2 step 2) |
| fsSp S/M/L + rotation correctness | SC-4 | Visual + rotation behavior | flox UAT, rotate on each screen (28-09 T2 step 3) |
| On-device smoke: connection edit, theme apply (incl. S/V restore), printer add/remove/switch, sysinfo read | SC-5 | Live Moonraker round-trips | flox + E3/E5, owner drives (28-09 T2 step 4) |
| Swipe-up gesture fully dead; all former drawer destinations reachable (home idle list / System page / shortcut grid) | D-04/D-05 | Gesture absence + nav coverage on hardware | flox: attempt swipe-up everywhere; walk DRAWER_TILES checklist (28-09 T2 step 5) |
| Mid-print e-stop reachable on the System cluster | D-06 | Live-print judgment | flox during a live print, navigate into System cluster (28-09 T2 step 6) |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (distributed into owning plans)
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planned
