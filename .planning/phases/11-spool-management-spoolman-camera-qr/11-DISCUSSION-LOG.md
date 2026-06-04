# Phase 11: Spool Management — Spoolman + Camera QR - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-04
**Phase:** 11-spool-management-spoolman-camera-qr
**Areas discussed:** Print-start gate strictness, Spool navigation/IA, One-shot extra scope

> Most of this phase's product decisions (12 "Open Product Decisions" + the full
> API/workflow contract) were pre-resolved by staged research in
> `docs/view_specific_notes/spoolman*.md` and the 23 live `docs/commands/spoolman-live-*.json`
> fixtures. Only the three genuinely experience-shaping, user-facing decisions were
> surfaced. The user also issued a global delegation: build the whole view one-shot,
> present UAT only when done.

---

## Print-start gate strictness

| Option | Description | Selected |
|--------|-------------|----------|
| Block no-spool, warn rest (Rec) | Hard-block only on no active spool; mismatch/low/archived = amber warn | |
| Warn everything, never block | Even no-spool is a tap-past amber warning; tracking opt-in per print | ✓ |
| Strict: block no-spool + low-remaining | Block no-spool AND remaining-below-needed; rest warn | |

**User's choice:** Warn everything, never block.
**Notes:** Mounted printer control surface should never stop a print over inventory bookkeeping. Captured as D-01.

---

## Spool navigation / IA

| Option | Description | Selected |
|--------|-------------|----------|
| New 'Spool' drawer tile + Status card (Rec) | Dedicated Dest.Spool owns picker/scanner/detail; compact active-spool card on Status launches in; gate hooks Files confirm | ✓ |
| No drawer tile — Status card + Files only | No drawer entry; picker one level deeper, less discoverable | |

**User's choice:** New 'Spool' drawer tile + Status card.
**Notes:** Mirrors the Webcam-tile precedent (capability-greyed). Captured as D-02/D-03.

---

## One-shot extra scope (nice-to-haves)

| Option | Description | Selected |
|--------|-------------|----------|
| Gcode-aware picker prefilter | Pre-filter picker by selected file's material/color metadata | ✓ |
| Location shortcuts | "At this printer" / "No location" chips from /v1/location | ✓ |
| Archived-spool warning | Warn on archived active/scanned spool | ✓ |
| Measured gross-weight correction | Enter scale reading → PUT /spool/{id}/measure | ✓ |

**User's choice:** ALL FOUR folded into the one-shot.
**Notes:** Scope = the brief's full "Must have + Nice to have" Recommended First Cut; only the explicit Non-Goals defer. Captured as D-04.

## Claude's Discretion

- Low-remaining safety margin constant (warn-only).
- Default picker list/sort shape + which chips are row-1 vs behind "refine".
- Persist per-printer picker prefs vs derive each session (lean: derive).
- `SpoolmanClient` / facade shape (proxy client, not via CommandDispatcher early).
- Picker Focus/Field portrait↔landscape collapse per UI LAW.

## Deferred Ideas

Full CRUD admin UI; filament/vendor creation; bulk intake; manufacturer UPC/EAN
intake; NFC/OpenPrintTag/RFID; multi-lane/per-tool assignment; drying/calibration
`extra` badges (no agreed schema); "use last spool for this file" from history;
lot/article/extra server-side filters (empty on live install); WebRTC camera
(separate Phase-10 deferral). See CONTEXT.md `<deferred>`.
