---
phase: 07
slug: files-print-control-core-print-loop-gate
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-02
updated: 2026-06-02
---

# Phase 07 - Validation Strategy

Per-phase validation contract for Files and Print Status print-control execution.

## Test Infrastructure

| Property | Value |
|---|---|
| Framework | Android JVM unit tests, release Kotlin compile, Android instrumented/manual live-printer checks |
| Config files | `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon"` |
| Estimated runtime | ~3-8 minutes on the Windows Gradle helper |

## Sampling Rate

- After every task: run the narrowest affected JVM tests listed in that task.
- After every UI-touching task: include `:app:compileReleaseKotlin`.
- After every wave: run `:app:testReleaseUnitTest --no-daemon`.
- Before `$gsd-verify-work`: run the full release unit suite and compile gate.
- Live-printer UAT is a manual checkpoint after automated verification is green.
- Max automated feedback latency target: 10 minutes.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Status |
|---|---|---:|---|---|---|---|---|---|
| 07-registry-specs | 07-01 | 1 | FILE-01, FILE-02, FILE-03, FILE-04, JOB-03, JOB-04, JOB-05 | T-07-01, T-07-02, T-07-03, T-07-04 | Typed registry specs prevent raw string drift and keep destructive/print-control operations distinct | unit | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandRegistryFilesTest --no-daemon"` | planned |
| 07-registry-sidecars | 07-01 | 1 | FILE-01, FILE-02, FILE-03, FILE-04, JOB-03, JOB-04, JOB-05 | T-07-04 | Every runtime command has catalog and matrix evidence | unit | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandCatalogDriftTest --tests *CommandRegistryFilesTest --no-daemon"` | planned |
| 07-file-models | 07-02 | 1 | FILE-01, FILE-02 | T-07-01, T-07-04, T-07-05 | Relative/root-prefixed/display/directory paths are modeled separately | unit | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.state.FileBrowserModelsTest --no-daemon"` | planned |
| 07-preview-parser | 07-02 | 1 | FILE-02 | T-07-02, T-07-05 | Metadata and thumbnail failures degrade safely | unit | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.state.FileBrowserModelsTest --tests works.mees.dinghy.state.PrintMetadataParseTest --no-daemon"` | planned |
| 07-session-client | 07-03 | 2 | FILE-01, FILE-02, FILE-03, FILE-04, JOB-03 | T-07-04, T-07-05 | UI receives a narrow session client, not raw RPC transport | unit | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.service.MoonrakerServiceTest --no-daemon"` | planned |
| 07-files-holder | 07-03 | 2 | FILE-01, FILE-02, FILE-03, FILE-04 | T-07-01, T-07-02, T-07-05 | Browse, preview, delete, and print start use holder state and state-confirmed pending behavior | unit | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.files.FileBrowserHolderTest --tests works.mees.dinghy.service.MoonrakerServiceTest --no-daemon"` | planned |
| 07-route-guard | 07-04 | 3 | FILE-01, FILE-03, FILE-04 | T-07-01, T-07-02 | Files route is live and ConfirmGuard safe-dismiss labels are supported | unit + compile | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.route.TopRouteTest :app:compileReleaseKotlin --no-daemon"` | planned |
| 07-files-ui | 07-04 | 3 | FILE-01, FILE-02, FILE-03, FILE-04 | T-07-01, T-07-02, T-07-05 | Files UI avoids row-level destructive actions and uses selected-file guards | unit + compile | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.files.FileBrowserHolderTest :app:compileReleaseKotlin --no-daemon"` | planned |
| 07-status-model | 07-05 | 4 | FILE-03, JOB-03, JOB-04, JOB-05 | T-07-02, T-07-03, T-07-05 | Print Status control states are pure and tested across printing/paused/terminal cases | unit | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.PrintStatusControlModelTest --no-daemon"` | planned |
| 07-status-gutter | 07-05 | 4 | FILE-03, JOB-03, JOB-04, JOB-05 | T-07-02, T-07-03, T-07-04, T-07-05 | Pause/resume/cancel/restart dispatch through registry specs and clear pending from state | unit + compile | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.PrintStatusControlModelTest :app:compileReleaseKotlin --no-daemon"` | planned |
| 07-automated-final | 07-06 | 5 | FILE-01, FILE-02, FILE-03, FILE-04, JOB-01, JOB-02, JOB-03, JOB-04, JOB-05 | T-07-01..T-07-05 | Full automated gate is green before live-printer UAT | full | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon"` | planned |
| 07-live-loop | 07-06 | 5 | FILE-01, FILE-02, FILE-03, FILE-04, JOB-01, JOB-02, JOB-03, JOB-04, JOB-05 | T-07-01..T-07-05 | Core print-loop is proven on Ender 5 Plus without browser fallback | manual + full gate | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon"` | planned |

## Wave Coverage

| Wave | Plans | Coverage |
|---:|---|---|
| 1 | 07-01, 07-02 | Registry, sidecars, path discipline, parser fixtures, thumbnail/metadata safety |
| 2 | 07-03 | Session file-browser surface and Files holder state machine |
| 3 | 07-04 | Live Files route, ConfirmGuard labels, hybrid Files UI |
| 4 | 07-05 | Print Status state-adaptive gutter and pending-state behavior |
| 5 | 07-06 | Full automated gate and Ender 5 Plus UAT |

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|---|---|---|---|
| Core print-loop on Ender 5 Plus | FILE-01..04, JOB-01..05 | Requires real printer, Moonraker state flips, and destructive/cancel behavior | Connect, browse files, start a safe print, monitor Status, pause, resume, graceful cancel |
| Delete gate while printing/paused | FILE-04 | Requires active printer states and a safe throwaway file | Confirm delete is blocked while printing/paused; confirm delete works only when idle |
| Large folder thumbnail scroll on flox | FILE-02 | Old GPU/list jank and OOM cannot be proven in JVM tests | Scroll mixed thumbnail/no-thumbnail folders; check no freeze, OOM, or layout jump |

## Validation Sign-Off

- [x] Every plan has automated verify steps.
- [x] Sampling continuity: no wave lacks automated verification.
- [x] Parser, registry, holder, UI route, and gutter model seams have targeted tests.
- [x] No watch-mode flags.
- [x] Full suite command uses the Windows Gradle helper required by project notes.
- [x] `nyquist_compliant: true` set because task-level automated verification exists before manual UAT.

**Approval:** approved
