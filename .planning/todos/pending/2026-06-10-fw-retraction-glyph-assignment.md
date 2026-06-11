---
created: 2026-06-11T01:05:00Z
title: Assign four distinct glyphs to the FW-retraction Fine-Tune rows (WR-08)
area: ui
target_phase: 28
files:
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - img/material-icon-bucket.json
---

## Problem

Phase-26 code review finding WR-08 (deliberately skipped at fix time, owner deferred at
26-HUMAN-UAT): on the Fine-Tune flat list, the four FW-retraction rows reuse glyphs already
assigned to other rows — `OutputCircle`, `MaxVelocity`, and `MaxAccel` each appear twice when
a fw-retraction-enabled printer is connected.

## Required

**Matthew must assign** four distinct registry glyphs (Material Symbols ligatures or
owner-made vectors) for:

1. Retract Length
2. Retract Speed
3. Unretract Extra Length
4. Unretract Speed

Hard law: Claude must NOT pick these glyphs ([[dinghy-never-pick-icons-ask]]). Once chosen,
register in `DinghyIcons.kt` + `img/material-icon-bucket.json` (verify_ligatures.py +
DinghyIconsTest drift guard) and swap the row descriptors in `FineTuneParams.kt`.

## Acceptance

- Four unique glyphs on the fw-retraction rows; no duplicate icon pairs on the Fine-Tune list
- DinghyIconsTest + verify_ligatures.py green

Triage 2026-06-11: target Phase 28 (as filed) — glyph assignment is owner-gated (icon law: never pick glyphs independently); rides the System-cluster pass.
