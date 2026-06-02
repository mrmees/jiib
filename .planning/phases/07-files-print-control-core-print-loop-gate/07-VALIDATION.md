---
phase: 07
slug: files-print-control-core-print-loop-gate
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-02
---

# Phase 07 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Android JVM unit tests + Android instrumented/manual live-printer checks |
| **Config file** | `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon"` |
| **Estimated runtime** | ~3-8 minutes on the Windows Gradle helper |

---

## Sampling Rate

- **After every task commit:** Run the narrowest affected JVM tests plus `:app:compileReleaseKotlin`.
- **After every plan wave:** Run `:app:testReleaseUnitTest --no-daemon`.
- **Before `$gsd-verify-work`:** Full release unit suite and compile gate must be green.
- **Max feedback latency:** 10 minutes for automated checks; live-printer UAT is manual checkpoint-gated.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-registry | TBD | 1 | FILE-01, FILE-03, FILE-04, JOB-03..05 | T-07-04 | Typed registry specs prevent raw string drift and keep availability predicates live | unit | `:app:testReleaseUnitTest --tests works.mees.dinghy.command.CommandCatalogDriftTest --no-daemon` | pending | pending |
| 07-file-models | TBD | 1 | FILE-01, FILE-02 | T-07-04 | Relative/root-prefixed/URL paths are modeled separately | unit | `:app:testReleaseUnitTest --tests works.mees.dinghy.state.*File* --no-daemon` | pending | pending |
| 07-holder | TBD | 1-2 | FILE-01..04 | T-07-01, T-07-02, T-07-05 | File browsing, preview, delete, and print start clear pending actions on state/session changes | unit | `:app:testReleaseUnitTest --tests works.mees.dinghy.ui.files.* --no-daemon` | pending | pending |
| 07-files-ui | TBD | 2 | FILE-01, FILE-02, FILE-03, FILE-04 | T-07-01, T-07-02 | Delete/start actions require Focus-level ConfirmGuard, never row-level destructive affordances | unit + manual | `:app:compileReleaseKotlin --no-daemon` | pending | pending |
| 07-status-gutter | TBD | 2 | JOB-03, JOB-04, JOB-05 | T-07-03 | Graceful cancel is distinct from emergency stop and confirm-gated | unit | `:app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.* --no-daemon` | existing screen | pending |
| 07-live-loop | TBD | 3 | FILE-01..04, JOB-01..05 | T-07-01..05 | Core print-loop is proven on Ender 5 Plus without browser fallback | manual | `manual Ender 5 Plus UAT` | N/A | pending |

*Status: pending · green · red · flaky*

---

## Wave 0 Requirements

- [ ] Registry/catalog drift tests name every new Phase 7 command ID.
- [ ] File parser fixtures cover directory, metadata, thumbnails-empty, delete response, and malformed data.
- [ ] Files holder tests cover folder navigation, row selection, preview fetch, delete clear-state, and state-confirmed print start.
- [ ] Print Status gutter tests cover printing, paused, and terminal states.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Core print-loop on Ender 5 Plus | FILE-01..04, JOB-01..05 | Requires real printer, real Moonraker state flips, and destructive/cancel behavior | Connect, browse files, start a safe print, monitor Status, pause, resume, graceful cancel |
| Delete gate while printing/paused | FILE-04 | Requires active print states and a safe throwaway gcode file | Confirm delete is blocked while printing/paused; confirm delete works only when idle |
| Large folder thumbnail scroll on flox | FILE-02 | Old GPU/list jank and OOM cannot be proven in JVM tests | Scroll a folder with mixed thumbnail/no-thumbnail files; check no frozen frames, OOM, or layout jump |

---

## Validation Sign-Off

- [ ] All plans have automated verify steps or explicit manual checkpoint tasks.
- [ ] Sampling continuity: no three consecutive tasks without automated verify.
- [ ] Wave 0 covers parser, registry, holder, and gutter test seams.
- [ ] No watch-mode flags.
- [ ] Feedback latency under 10 minutes for automated checks.
- [ ] `nyquist_compliant: true` set after task-level coverage exists in plans.

**Approval:** pending
