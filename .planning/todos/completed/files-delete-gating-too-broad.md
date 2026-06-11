---
id: files-delete-gating-too-broad
created: 2026-06-02
source: 07-UAT.md (Deferred Defects) — found on-device 2026-06-02, deferred per Matthew
priority: medium
resolves_phase: 09
fold_into: 09-UAT
---

# Files Delete gating is too broad — blocks ALL files during any print

**Folded into Phase 9 UAT** (per Matthew, 2026-06-02). Fix during Phase 9 and add the
verification check below to Phase 9's UAT round.

## Defect
`FilesScreen` computes `deleteEnabled = selected != null && !printingActive`, where
`printingActive` is the GLOBAL print state. So while *any* print runs, **Delete is disabled
for EVERY file** — not just the one being printed.

## Desired behavior
Only block deleting the file that is **currently printing** (the active
`print_stats.filename`); every other idle file stays deletable during a print.

**NOTE — UI-SPEC rule change:** this relaxes the original "Delete is idle-only" rule from the
Files UI-SPEC. Confirm/record the rule change when fixing (update `docs/ui_design/` or the
relevant UI-SPEC note so the design LAW and code agree).

## Fix location
- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` (the `deleteEnabled` predicate) —
  gate on `selected.path == activePrintFilename` instead of the global `printingActive`.
- Verify the active-filename comparison matches Moonraker's `print_stats.filename` path form
  (relative vs leading `gcodes/` — check against the live shape already recorded in
  `docs/moonraker-capabilities.md`).

## Phase 9 UAT check (add to 09-UAT.md when Phase 9 is verified)
- **Delete-during-print scoping:** With a print ACTIVE, the Files list Delete action is
  **disabled only for the file being printed**; selecting any *other* (idle) file enables Delete
  and that file can be removed mid-print. While idle, Delete works for any selected file as before.
  (Use a safe throwaway file.)

## Resolution (2026-06-10, Phase 25)
Fixed by the 25-03 FilesScreen rebuild (DeleteGate scopes Delete to the printing file only); confirmed by UAT row D-08 on flox (25-UAT.md).
