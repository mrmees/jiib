---
phase: 28
slug: system-settings-cluster
status: draft
nyquist_compliant: false
wave_0_complete: false
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

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (filled by planner) | — | — | — (UX migration) | — | — | unit | — | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `HomeActionTest` count/order assertions updated alongside D-05 (8 → 11 idle rows) — research Pitfall 2
- [ ] Grep `maxItems` across the ENTIRE test sourceset before D-17 deletion lands (ThemePrefsFallbackTest / PaletteGoldenTest golden paths) — research Pitfall 1; whole-sourceset compile gate ([[dinghy-wave0-red-scaffold-compile]])
- [ ] Ligature drift guard (`DinghyIconsTest` / `verify_ligatures.py`) must stay green if any new icon tokens register (LauncherFineTune, System-page row tokens)
- [ ] Drawer test deletion (`AppDrawer`/`SwipeUpAccumulator` test files) lands in the same commit as the source deletion so the sourceset never breaks
- [ ] Verify `ProfileStore.Json` carries `ignoreUnknownKeys = true` BEFORE the maxItems persistence-axis deletion (stored-profile migration safety)

*Existing infrastructure covers host-test needs; no framework install required.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Restyled cluster (Settings/Theme/Printers/SysInfo/About + System page) owner approval, both orientations | SC-1 | Visual judgment on real hardware | flox on-device UAT, portrait + landscape |
| C6 densification reads well in-hand; Settings + About fit one page at M | SC-2/SC-3 | Density/legibility judgment | flox UAT at S/M/L text sizes |
| fsSp S/M/L + rotation correctness | SC-4 | Visual + rotation behavior | flox UAT, rotate on each screen |
| On-device smoke: connection edit, theme apply, printer add/remove/switch, sysinfo read | SC-5 | Live Moonraker round-trips | flox + E3/E5, owner drives |
| Swipe-up gesture fully dead; all former drawer destinations reachable (home idle list / System page / shortcut grid) | D-04/D-05 | Gesture absence + nav coverage on hardware | flox: attempt swipe-up everywhere; walk DRAWER_TILES checklist |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
