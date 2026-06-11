---
created: 2026-06-11T01:05:00Z
title: Temperature adjuster — fire nudges without waiting for echo (drop per-command verification)
area: ui
target_phase: 28
files:
  - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
---

## Problem

Phase-26 UAT note (owner, 2026-06-10, test #3): rapidly pressing the Temperature adjuster
± buttons drops presses because the screen waits for the Moonraker echo/response before
accepting the next nudge. Owner: "I don't hate that behavior for the moment", but —

> "We could probably drop the command verification on these and just fire the command
> without waiting for the previous response. This screen is more relaxed than the fine
> tuning screen."

## Direction

Temperature targets are absolute `SET_HEATER_TEMPERATURE` dispatches (idempotent,
last-write-wins) — unlike Fine-Tune's clamp/busy-lock-protected relative tuners, there is
no wedge risk class here. Candidate approach: keep clamp authority
(`PrinterCommands.clampHeaterTarget`) but allow optimistic local accumulation and dispatch
on every tap (or debounce-coalesce to the latest target) instead of gating ± on the
in-flight echo.

## Acceptance

- Rapid ± taps all register (target accumulates locally and the final dispatched target
  matches the displayed value)
- Clamp authority preserved; no busy-lock regression on Fine-Tune (unchanged)
- Owner re-checks feel on flox
