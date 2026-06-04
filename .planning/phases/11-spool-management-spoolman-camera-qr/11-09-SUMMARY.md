---
phase: 11
plan: 09
title: On-device end-to-end Spool UAT
status: complete
verified: on-device (flox + live E3/E5 + Spoolman 0.22.1)
date: 2026-06-04
---

# 11-09 — On-device Spool UAT (SUMMARY)

The Spoolman + Camera-QR view was UAT'd hands-on on the real flox (LineageOS 18.1 /
Adreno 320 / 2GB) against the live Ender 3 Pro (`192.168.1.121`) + Ender 5 Plus
(`192.168.1.120`), both on one Spoolman (`192.168.1.253:7912`, v0.22.1). Driven by
Matthew; server-side state changes verified via the Moonraker/Spoolman API each step.

## UAT result: PASS

| # | Check | Result |
|---|-------|--------|
| 1 | Capability gate + active-spool card (SC-1) | PASS |
| 2 | Picker + filters (Type/Color/MFG, sort, color grid) (SPOOL-03) | PASS |
| 3 | **Scan-to-assign (SC-2/SC-4)** — scanned "Sky Blue / PLA High Speed Matte" label → confirm → Load → **E3 active flipped 12→2 in Spoolman, verified server-side** | **PASS** |
| 4 | Permission / no-camera degrade (SC-3) | PASS |
| 5 | Camera release on exit (D-14) | PASS |
| 6 | Warn-only print-start gate (D-01) | PASS |
| 7 | Change-during-print (live filament swap) | DEFERRED (user's call — set-active path + no-print-gating logic verified; live-print swap not run) |
| 8 | External-change reconciliation (D-10) — API-flipped E3 2→5, card reconciled with no app interaction | PASS |
| 9 | Measured-weight correction (D-04) | PASS |

Headline (SC-4) proven end-to-end live: load filament → scan its label → active spool
flips in Spoolman.

## Gaps the hands-on UAT surfaced (all fixed live)

Hands-on testing caught three defects green unit suites + on-device launch could not —
the recurring "build-it-AND-wire-it-AND-prove-the-write" lesson (Nth mock-vs-reality strike):

- **G1 — measured-weight page unreachable.** `MeasuredWeightPage` (built in 11-07) had
  **no entry point** — wired it to a tap on the detail weight line (`cb10d55`).
- **G2 — measured-weight UX rework.** Header now shows the **spool (tare) weight** + the
  **current gross total** (spool + remaining) so the deduction is obvious; replaced the
  custom NumpadPage with the **system numeric keyboard** for this one inventory field;
  input moved above the info pane (`419c5c1`, `4945ad8`, `cf8d027`, `f14e6b7`).
  Clarified there is **no `measured_weight` field** in Spoolman 0.22.1 — `/measure` is an
  action that recomputes used/remaining from `gross − spool_weight`.
- **G3 — measured-weight never wrote.** `measureSpool` sent the gross as a **query param**
  (`weight=N`); Spoolman's `PUT /spool/{id}/measure` reads `{"weight": N}` from the **body**.
  Added a `body` (JsonObject) to `SpoolmanProxyArgs` + the `spoolman.proxy` spec; validated
  live (measure 1210 g on spool 3 → remaining 1000 g, 210 g spool deducted) (`fec00e9`).

## Also delivered this UAT/polish pass (post-execution, on Matthew's direction)

Filters → bottom of Focus; fuzzy material **family** chips; Name/Date/Remaining sort with
direction toggle; **Name sort fixed** to order by displayed title (material→name, not color
name); **Dest.Spool** detail border tinted to the spool color; unload via **long-press** of
Load (OutlinedControl `onLongClick`); icon-only filter/gutter buttons sized to ~78% of cell;
Color selector as a fill-to-fit 3×4 grid + Multi-color tile; detail pane reduced to two type
sizes (header + body); front-camera flip with FILL_CENTER/TextureView fix.

## Restores
Printers intentionally left as-is per Matthew (E3 active = 5; spool 3 = 579 g).
Save→change→restore protocol exercised throughout (spool 3 measured then restored to 579 g).

## Follow-ups (optional, deferred)
- Formal `gsd-verifier` goal-backward pass (live UAT already proves the goal).
- Codex review of the spool feature (Codex runtime crashed mid-session; rerun when healthy).
- Check 7 live-print filament swap (logic verified; live print not run).
