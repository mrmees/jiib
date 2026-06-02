# Phase 7: Files & Print Control - Core Print-Loop Gate - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-02
**Phase:** 7-Files & Print Control - Core Print-Loop Gate
**Areas discussed:** File Library Browsing Model, Preview & Print Confirmation, Delete Entry Point & Safety, Print Status Control Gutter

---

## Pending Todos

| Option | Description | Selected |
|--------|-------------|----------|
| None | Do not fold matched pending todos into this context. | |
| `macrobenchmark-module-wiring.md` | Include benchmark profileability issue only. | |
| `benchmark-harness-fairness-fixes.md` | Include benchmark fairness fixes only. | |
| Both | Include both pending todo notes as Phase 7 verification/perf context. | ✓ |

**User's choice:** Both.
**Notes:** These are folded as benchmark/performance context, not Files/Print feature scope.

---

## File Library Browsing Model

| Decision | Options Considered | Selected |
|----------|--------------------|----------|
| Default ordering | Recent first; Name first; Agent discretion | Recent first |
| Row content | Compact essentials; Rich rows; Minimal rows | Compact essentials |
| Folder navigation | Breadcrumb chip + Up row; Breadcrumb chips only; In-list folders only | Breadcrumb chip + Up row |
| Missing thumbnails/folders | Distinct placeholders; Empty thumbnail slot; Hide thumbnail slot | Distinct placeholders |

**User's choice:** Recent-first ordering, compact rows, breadcrumb/path chip plus Up row, and distinct placeholders.
**Notes:** Android/system Back returns to the caller screen rather than walking folders.

---

## Preview & Print Confirmation

| Decision | Options Considered | Selected |
|----------|--------------------|----------|
| Selected-file Focus emphasis | Print-readiness snapshot; Visual preview only; Full metadata panel | Print-readiness snapshot |
| Start-print guard | Confirm with thumbnail + details; Confirm plus filename match; No second guard | Confirm with thumbnail + details |
| Start success basis | State flip only; Ack then optimistic state; Ack is enough | State flip only |
| Missing metadata/thumbnail | Allow print with degraded details; Require metadata before print; Retry-only state | Allow print with degraded details |

**User's choice:** Preview should show a print-readiness snapshot; start print uses ConfirmGuard; success is confirmed by printer state; metadata/thumbnail failures do not block printing.
**Notes:** Ack may show transient starting state, but not final success.

---

## Delete Entry Point & Safety

| Decision | Options Considered | Selected |
|----------|--------------------|----------|
| Delete location | Selected-file Focus action; Long-press row action; Per-row delete icon | Selected-file Focus action |
| Delete during print | Block active-print delete; Only block current file; Allow with confirmation | Block active-print delete |
| Delete guard content | Filename/path + details; Filename only; Thumbnail + all available details | Filename/path + details |
| After delete success | Stay in current folder and clear selection; Navigate up one folder; Return to Status | Stay in current folder and clear selection |

**User's choice:** Delete appears only in selected-file Focus, is idle-only, confirms filename/path with size/date when known, and keeps the picker in the current folder after success.
**Notes:** Rows remain tap-to-select only.

---

## Print Status Control Gutter

| Decision | Options Considered | Selected |
|----------|--------------------|----------|
| Printing gutter | Tune / Pause / Stop; Pause / Cancel / Stop; Files / Pause / Stop | Tune / Pause / Stop |
| Graceful cancel location | Stop confirm offers two actions; Pause opens controls; Add Cancel elsewhere; Freeform long-press Pause | Long-press Pause |
| Paused gutter | Tune / Resume / Stop; Cancel / Resume / Stop; Files / Resume / Stop | Tune / Resume / Stop |
| Cancel while paused | Long-press Resume opens cancel; Cancel only on Pause | Long-press Resume opens cancel |
| Terminal gutter | Files / Restart / Stop; Files / Retry / Back; Files / Restart only | Files / Restart / Stop |

**User's choice:** Preserve existing gutter structure. Tap Pause/Resume performs pause/resume; long-press Pause/Resume opens graceful Cancel Print; terminal states show Files/Restart/Stop.
**Notes:** Stop remains emergency stop.

---

## the agent's Discretion

- Exact code shape, holder/repository boundaries, cache sizes, and row implementation details.
- Exact placeholder artwork and transient copy.

## Deferred Ideas

- Tune panel stays deferred.
- Typed search/filtering, upload/slicing, and deep print-loop robustness remain out of Phase 7 scope.
