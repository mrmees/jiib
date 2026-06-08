# 19-08 SUMMARY — On-Device UAT (SC-4)

**Plan:** 19-08 (Task 2 — `checkpoint:human-verify`, blocking) · **Status:** COMPLETE (owner-approved)

## Outcome

SC-4 met: the Outputs feature is proven live on the real printers (rich E5P + sparse E3P) with state-flip
confirmation, required fan/LED/pin/servo `/server/gcode_store` wire evidence, and owner sign-off — recorded in
`19-UAT.md`.

## Path

- **Task 1 (auto):** full host suite GREEN (848→854 post-gap), `verify_ligatures.py` exit 0, debug APK installed
  on flox; `19-UAT.md` scaffolded (commit `955357b`).
- **Round-1 UAT (owner):** fans / output_pins / servo functional; **2 gaps** filed (commit `f1a4f8e`):
  - GAP-A — Off button overlaid the scrubber on every scrubber detail page.
  - GAP-B — LED page ignored channel capability (RGB wheel shown on a white-only light; value changes no-op).
- **Gap closure:** `/gsd-plan-phase 19 --gaps` → plans 19-09 (GAP-A) + 19-10 (GAP-B), plan-checker PASS + Codex
  review (SS-1 PCA9533/9632 derivation + N-1 wording fixed). Executed via `--gaps-only`.
- **Round-2 re-UAT:** found a regression in 19-09's extracted `LedBrightnessControl` (fill grew from center;
  settled value didn't stick — stale `pointerInput` closure). Fixed in `fa97efb` (internal `working` state +
  left-anchored fill, mirroring ScrubberPage). Host tests can't catch either (gesture-closure + visual) — the
  mock-vs-reality lesson; caught on-device.
- **Owner sign-off:** all checks PASS on flox; wire evidence captured for fan (`FILTER_fan`), pin
  (`bed_safety_switch`), LED white-only (`chamber_light WHITE=…`) + RGB (`expanderPixel`), servo
  (`camera_servo ANGLE/WIDTH=0`).

## Accepted exclusion

`heater_generic` — no dev printer exposes one; covered by unit tests only (automated-only, not a gap).

## Future polish (non-blocking)

RGB LED detail page aesthetics — owner: "good enough for now." Candidate for a later visual pass; the white-only
path (the owner's actual hardware) is clean.
