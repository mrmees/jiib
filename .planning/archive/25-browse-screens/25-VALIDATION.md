---
phase: 25
slug: browse-screens
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-10
---

# Phase 25 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Authoritative validation design: see `25-RESEARCH.md` → **## Validation Architecture**.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 host unit tests (Robolectric where a Context is needed); Kotlin `kotlinx-coroutines-test` for Flow logic; Compose `@Preview` compile gates |
| **Config file** | `app/build.gradle.kts` (test source set); runs Windows-side via `E:\Android\gw.bat` |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.<changed-class>* --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~60–180 seconds (host JVM; no device) |

> ⚠ Gradle does NOT run from WSL `./gradlew` — drive via `E:\Android\gw.bat` (see CLAUDE.md build env). Guard hangs with `timeout` + taskkill (see [[dinghy-display-gradle-hang-interop]]).
> ⚠ Stale-APK gate: before any on-device UAT, force-rebuild (`--rerun-tasks` if needed) and verify the APK mtime postdates the latest fix commit ([[dinghy-stale-apk-uat-gate]]).

---

## Sampling Rate

- **After every task commit:** Run the quick command scoped to the changed class.
- **After every plan wave:** Run the full host suite.
- **Before `/gsd-verify-work`:** Full host suite green + the on-device flox gates (D-01/D-02 gfxinfo spike, D-17 crash fix, D-18 gating verification, per-screen owner approval in both orientations) complete.
- **Max feedback latency:** ~180 seconds (host); on-device gates are batched at the Wave-0 spike checkpoint and the per-screen UAT checkpoints.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (filled by planner) | | | | | | | | | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] D-01 spike harness (throwaway Compose ListRow builds of Files + Console list surfaces) + release-mode gfxinfo capture on flox vs the Phase-22 Views baselines (Files p90≈41.95ms, Console p90≈9.26ms; bar = 0 frozen frames + p90 parity per ADR-0001 Addendum-2)
- [ ] D-17 logcat capture of the webcam-screen crash stack trace BEFORE fixing (then verify/fix the WR-02 `connectionStore` → `activeConfig` resolver read at AppShell.kt:214)
- [ ] Existing host test infrastructure covers regression assertions (deleteAllowed predicate, MacroInvocation sanitizer, ConsoleFilters render-time view) — extend, don't install

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Per-screen owner approval (SC-1) | SC-1 | Visual/UX judgment on real hardware | Owner eyeballs each migrated screen on flox, portrait + landscape |
| gfxinfo spike numbers (D-01/D-02) | SC-1/ADR-0001 | Release-mode frame metrics need the real Adreno 320 | `adb shell dumpsys gfxinfo works.mees.dinghy framestats` while scrolling each list surface |
| Webcam crash fix + playback (D-17, SC-5) | SC-5 | Crash only reproduces on-device | Open Webcam tile on flox; capture logcat; after fix, verify H.264 playback |
| Idle-list webcam HIDE gating (D-18) | SC-5 | Cross-profile toggle behavior on-device | Toggle webcam per-profile, switch printers, verify idle-list row appears/disappears independently |
| Files delete-scoping regression (D-08) | SC-5 | On-device UAT item from files-delete-gating-too-broad.md | During an active print: delete blocked ONLY for the printing file; others deletable |
| fsSp S/M/L + rotation conformance (D-19) | SC-3 | Visual clipping judgment | Owner checks each screen at S/M/L and across rotation |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
