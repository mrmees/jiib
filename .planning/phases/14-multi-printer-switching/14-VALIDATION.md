---
phase: 14
slug: multi-printer-switching
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-04
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `14-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + `kotlinx-coroutines-test` (virtual time) for host unit tests; AndroidX instrumented tests (`androidTest`) for on-device persistence |
| **Config file** | `app/build.gradle.kts` (existing `test` / `androidTest` source sets — no new framework install) |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileStoreTest"` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~30–60 seconds (host unit suite) |

> ⚠ **AGP glob trap (MEMORY):** `--tests 'works.mees.dinghy.*'` false-fails ("No tests found") on this AGP — list test CLASSES explicitly. Pipe output through `tr -d '\r'`; the process exit code is authoritative.

---

## Sampling Rate

- **After every task commit:** Run the relevant Wave-0 unit class (`ProfileStoreTest` / `ActiveConfigDerivationTest` / `ProfileThemeSeedTest`).
- **After every plan wave:** Run the full `:app:testDebugUnitTest` suite.
- **Before `/gsd-verify-work`:** Full unit suite green **+** the instrumented survival test **+** the live two-printer hands-on UAT.
- **Max feedback latency:** ~60 seconds (host unit run).

---

## Per-Task Verification Map

| Req ID | Behavior | Test Type | Automated Command | File Exists |
|--------|----------|-----------|-------------------|-------------|
| MULTI-01 | `ProfileStore.sanitize` drops malformed entries, recovers from corrupt blob, empty → empty list | unit (host, pure) | `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileStoreTest` | ❌ W0 |
| MULTI-01 | `delete` writer auto-picks another active (D-12); deleting last → active-id cleared (D-11/D-12) | unit (host) | same class | ❌ W0 |
| MULTI-01 | `activeConfig` derivation `combine→pick→map→distinctUntilChanged`: a name/theme-only edit does NOT re-emit a new `ConnectionConfig` (Pitfall 1) | unit (host, virtual time) | `--tests works.mees.dinghy.di.ActiveConfigDerivationTest` | ❌ W0 |
| MULTI-01 | Theme triple derivation: active-profile change → correct `(base, deltas, fs)` applied; corrupt theme primitives fail safe to default | unit (host) | `--tests works.mees.dinghy.theme.ProfileThemeSeedTest` | ❌ W0 |
| MULTI-01 | `runConfigLoop` cancels prior session before rebuild on a config change (the switch seam, D-03) | unit (host, EXISTING) | `--tests works.mees.dinghy.service.MoonrakerServiceTest` | ✅ exists |
| MULTI-01 | Switch survives process death: active-id persisted, re-read on cold start lands on the right printer (D-02/SC-3) | instrumented (on-device) | `connectedDebugAndroidTest` (`ProfileSurvivesRestartTest`) | ❌ W0 |
| MULTI-01 | **Live two-printer switch** (success criterion 4): E5+ ↔ E3, drive a control on each without re-entering details | manual on-device | hands-on UAT on `flox` + live printers (192.168.1.120:7125 / .121:7125) | n/a |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt` — pure `sanitize` + `upsert`/`delete`/D-12 auto-pick. **RED scaffold MUST compile day-one** ([[dinghy-wave0-red-scaffold-compile]]) — typed `fail()`/assertion stubs, no refs to unbuilt symbols beyond the new profile types.
- [ ] `app/src/test/java/works/mees/dinghy/di/ActiveConfigDerivationTest.kt` — `combine` + `distinctUntilChanged` proves a name/theme edit does NOT churn the config (Pitfall 1).
- [ ] `app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt` — active-profile → theme triple, fail-safe on corrupt primitives.
- [ ] `app/src/androidTest/java/works/mees/dinghy/service/ProfileSurvivesRestartTest.kt` — active-id survives process death (mirror existing `ServiceSurvivesRotationTest`).
- [ ] No new framework install — JUnit4 + coroutines-test + AndroidX test already wired.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live two-printer switch + drive each | MULTI-01 (SC-4) | Capabilities are server-derived; no-stale-state across a real spine rebind only emerges against live Moonraker. A fake session publishing a canned handle won't catch a stale-capability bug. | On `flox`: add E5+ (192.168.1.120:7125) + E3 (192.168.1.121:7125), switch via Devices tile, confirm each lands on its own Status + a control action works, with no re-entry of details. |
| Survival across process death | MULTI-01 (SC-3) | Host DataStore is **not** persistence-faithful on the Windows build host (a `FakeProfileStore` round-trips in memory and lies). Needs the real DataStore file + a real cold start. | Force-stop the app after selecting a printer; relaunch; confirm it reconnects to the same printer. (Automated by `ProfileSurvivesRestartTest` on-device.) |
| No reconnect on theme/name edit | MULTI-01 (Pitfall 1) | `distinctUntilChanged` suppression is best confirmed against the real flow + UI. | Edit the active printer's accent in Settings; eyeball that NO recovery Splash fires (the connection must not bounce). |

---

## Where a Fake would LIE (documented mock-vs-reality risk)

This project has a **documented history of green-unit-suite bugs that only on-device caught** (Phase 5 G1–G4, Phase 9 identify-`url`, Phase 11 measured-weight). Planner must respect:

1. **DataStore persistence is NOT host-faithful.** Survival-across-process-death CANNOT be unit-proven — it needs the instrumented `ProfileSurvivesRestartTest` (real DataStore file, real cold start).
2. **The real rebind / no-stale-state is only fully real on-device.** `runConfigLoop` is unit-proven for cancellation ordering, but "every screen reflects the NEW printer, capability detection re-runs, command registry rebinds" emerges from the real `SpineHandle` republish against live Moonraker. → **live two-printer UAT is mandatory.**
3. **`distinctUntilChanged` suppression** — verify on-device that editing the active theme does NOT reconnect (no Splash on an accent change).

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (4 new test files above)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
