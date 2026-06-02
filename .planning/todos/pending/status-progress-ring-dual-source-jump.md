---
id: status-progress-ring-dual-source-jump
created: 2026-06-02
source: on-device observation (Matthew, short ~10-min print on Ender 5 Plus)
priority: medium
resolves_phase: null
---

# Status progress ring jumps DOWN then back up on a short print

## Symptom
On a small (~10-minute) print, the Print Status focus ring's "percent complete" briefly
**decreases** before continuing upward. Observed live on flox + Ender 5 Plus, 2026-06-02.

## Root-cause hypothesis (refines the original "filament retraction" guess)
Progress is **NOT** derived from extruded filament length, so a retraction can't be the cause.
`PrinterStateReducer.kt:113-115` sources it from TWO places and lets the **last writer win per frame**:

```kotlin
// Progress can arrive on either virtual_sdcard or display_status; last writer wins per frame.
status.objectOrNull("virtual_sdcard")?.doubleOrNullAt("progress")?.let { s = s.copy(progress = it) }
status.objectOrNull("display_status")?.doubleOrNullAt("progress")?.let { s = s.copy(progress = it) }
```

- `virtual_sdcard.progress` = file byte-position ÷ file size (monotonic).
- `display_status.progress` = M73 / slicer-driven (time-based when M73 present; mirrors vsd otherwise).

These two **disagree**, especially early in a short print. A partial `notify_status_update` delta
that carries only one source overwrites the value set by the other → the ring snaps between two
different numbers (down, then up). The dual unconditional writes are the prime suspect.

## Fix direction (to investigate, not yet decided)
- Pick ONE authoritative source for the ring, OR
- Define precedence (e.g. prefer `display_status`/M73 when present, else `virtual_sdcard`) AND don't
  let a delta carrying only the lagging source clobber the better value, OR
- Clamp progress to be monotonic within a single job (guard against backward jumps), being careful
  not to mask a legitimate reset on restart.
- Verify on a short print (the regime where the two sources diverge most) AND a long print.

## Notes
- Status screen is Phase-4 work (print-data layer); deferred per Matthew 2026-06-02.
- The ring also multiplies `progress` for the centered "NN%" label and the arc — one fix covers both.
