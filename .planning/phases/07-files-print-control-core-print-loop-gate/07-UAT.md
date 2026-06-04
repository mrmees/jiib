# Phase 07 Ender 5 Plus Core Print-Loop UAT

## Status

**CLOSED — owner-attested (2026-06-04).** Matthew (project owner, who has been driving real prints
through the app across multiple phases) attests the full core print loop works in real-world use:
browse → start from Files → live Status monitoring → pause → resume → graceful cancel → restart →
delete. A formal, transition-by-transition recorded UAT was **waived by the owner** ("all of it seems
to work; anything else will come up in future patch work"). The print-control actions are confirmed
WIRED in code (not placeholders): `PrintStatusControlModel` (Pause/Resume/GracefulCancel/RestartPrint/
Tune + pending states), `CommandRegistry.printStart`/`PRINT_PAUSE`/`PRINT_RESUME`, dispatched from
`FileBrowserClient`/`PrintStatusScreen`. The results below are therefore owner-attestation (¹), NOT a
Claude-witnessed step-by-step capture — residual edge cases are accepted as future patch work.

## Preconditions

- `07-VERIFICATION.md` automated release gate is PASS.
- `07-PERF.md` flox route-render and large-library perf gate is PASS.
- Browser remains closed during the core workflow.
- Use a safe gcode file and safe operating conditions.
- Use a safe throwaway file for delete testing.

## Environment

- **Timestamp:** 2026-06-04 (owner attestation; informal real-world use accrued over prior sessions)
- **Printer:** Ender 5 Plus (192.168.1.120) + Ender 3 (192.168.1.121, the 417-file library used for Files perf)
- **Device/tablet:** flox (Nexus 7 2013 / LineageOS 18.1 / API 30) — current signed release build installed
- **App commit/build:** post-Phase-13 master (all Phase-7 plans 07-01..07-05 + the Phase-9 D-15 delete fix landed)
- **Moonraker host:** E5 192.168.1.120:7125 / E3 192.168.1.121:7125
- **Safe start/delete file:** owner-selected throwaway gcode (not separately logged)

## Checklist

| Step | Requirement | Expected Evidence | Result | Notes |
|---|---|---|---|---|
| Connect | FILE-01/JOB-01 | App connects without browser fallback; Print Status shows live state | PASS¹ | owner-attested |
| Open Files | FILE-01 | Files route opens from drawer or terminal Files action | PASS¹ | owner-attested |
| Browse folders | FILE-01/FILE-02 | Compact rows, Up row, path chip, stable scrolling | PASS¹ | owner-attested |
| Thumbnail fallback | FILE-02 | Thumbnail-less gcode rows render fallback visuals and stay selectable | PASS¹ | owner-attested |
| Select file | FILE-03 | Focus preview shows selected filename and available metadata | PASS¹ | owner-attested |
| Start print | FILE-03 | `Print file` ConfirmGuard appears; confirming starts selected filename | PASS¹ | owner-attested |
| Monitor Status | JOB-01/JOB-02 | Print Status shows progress, temps, Z/layer/elapsed/remaining as available | PASS¹ | owner-attested |
| Pause | JOB-03 | Pause dispatches; pending clears only when printer reports paused | PASS¹ | owner-attested |
| Resume | JOB-03 | Resume dispatches; pending clears only when printer reports printing | PASS¹ | owner-attested |
| Graceful cancel | JOB-04 | Long press/accessibility `Cancel print`; ConfirmGuard `Cancel print` / `Keep printing`; terminal state observed | PASS¹ | owner-attested |
| Emergency Stop separation | JOB-04 | Stop remains separate emergency path and is not reused for graceful cancel | PASS¹ | owner-attested |
| Return to Files | FILE-01/FILE-04 | Files remains usable after terminal/cancel state | PASS¹ | owner-attested |
| Delete idle-only | FILE-04 | `Delete file` only selected-file Focus action; disabled while active; throwaway file removed only while idle | PASS¹ | owner-attested |
| Terminal Files | JOB-05 | Terminal state with filename shows Files action and routes to Files | PASS¹ | owner-attested |
| Restart current filename | JOB-05/FILE-03 | Restart uses current `print_stats.filename` when terminal state retains it | PASS¹ | owner-attested |
| Restart last-job fallback | JOB-05/FILE-03 | Restart uses LastJob filename if terminal state clears current filename | PASS¹ | owner-attested |

## Failure Recording

If any item fails:

- Record exact observed app state.
- Record printer state and filename.
- Record whether browser was used.
- Record reproduction steps.
- Do not mark the item passed.

## Deferred Defects (fix + verify in a future UAT round)

- **Delete gating is too broad.** → ✅ **RESOLVED in Phase 9 (plan 09-06, D-15 scoped delete).**
  The fix replaced the global `!printingActive` gate with a `deleteAllowed` helper that blocks ONLY
  the actively-printing `print_stats.filename` (in both `FilesScreen.deleteEnabled` and
  `FileBrowserHolder.requestDeleteSelected`); every other idle file stays deletable during a print.
  Verified in the Phase-9 UAT (delete-during-print check). The "Delete is idle-only" UI-SPEC rule was
  relaxed accordingly in `docs/ui_design/CLAUDE.md`. Original tracking todo closed.

## Gate Result

**CLOSED (owner-attested, 2026-06-04).** ¹ Owner attestation, not a Claude-witnessed recorded run. The
core browser-free print loop (FILE-01..04, JOB-01..05) is confirmed working in real-world use by the
owner; the print-control wiring is code-verified present; the Files large-library perf concern was
already proven on-device during the Files polish pass (smallest-thumb-per-row, see `07-PERF.md`).
Formal transition-by-transition UAT waived by the owner; residual edges → future patch work.
