# Phase 26.5 — Overnight Hardening Report

**Run started:** 2026-06-11 (overnight, unattended)
**Branch:** `gsd/phase-26.5-overnight-hardening`
**Work order:** `docs/top-down-audit-roadmap.md` Part 4 (overnight-safe subset)

## Run Summary

_(placeholder — plan 26.5-07 finalizes this from the per-plan SUMMARYs at end of night)_

## Per-Package Status

| Package | Scope tonight | Status | Plan |
|---|---|---|---|
| R5a | Guardrail infra: gradlew +x, CI, lint baseline enforced, StrictMode | pending | 26.5-01 |
| R9 | Memory/docs delint steps 1–3 + 5 (step 4 = script only) | pending | 26.5-02 |
| R10 | Touch responsiveness steps 2–4 + debug-flag instrumentation | pending | 26.5-03 |
| R1 | Install & display correctness (code-only) | pending | 26.5-04 |
| R2 | Always-on (code-only): keep-screen-on + battery exemption | pending | 26.5-05 |
| R4 | Build-time command map (CommandMap.kt + rewires) | pending | 26.5-06 |
| R7 | Network posture: useSecure wss/https plumbing | pending | 26.5-06/07 |

_(plan 26.5-07 updates each row to done / failed / partial from the SUMMARYs)_

## DECISIONS-NEEDED

### LICENSE choice (R5a step 2 — recommend-only; NO file was created pending the owner's call)

**GPLv3** is the Klipper-ecosystem norm — Klipper, Moonraker, and Fluidd are all GPL, so a GPLv3
jiib signals ecosystem citizenship and guarantees that any fork of the fork-and-edit `CommandMap`
customization point stays open. **MIT/Apache-2.0** maximizes adoption and lets anyone (including
commercial printer vendors) embed or redistribute without copyleft obligations — Apache-2.0 adds
an explicit patent grant over MIT. **Recommendation: GPLv3** — this app exists inside and because
of the GPL Klipper ecosystem, the sideload-an-APK audience loses nothing to copyleft, and
"vendor embeds it in a closed product" is exactly the case you'd probably want to prevent.
No LICENSE file exists in the repo; R5b (README/CONTRIBUTING, the go-public gate) is blocked on
this decision.

## R9 Archive Script

_(placeholder — plan 26.5-02 writes the reviewed, protected-list-guarded bulk-archive script to
`26.5-r9-archive-script.sh` in this phase directory; review then run it yourself. NOT executed
tonight per CONTEXT rails.)_

## Morning UAT Checklist

Ordered for ONE sitting — flox (Nexus 7, LineageOS 18.1/API 30) first, then S25 Ultra, then CI.

### flox group

- [ ] **R10 tap torture:** 20 rapid taps per control class (stepper +/− on AdjusterPanel, ListRow,
      OutlinedControl command buttons) — ≥95% register with visible same-frame feedback; every tap
      either acts or shows visible rejection feedback; zero silent swallows. Use a DEBUG build
      (instrumentation is `BuildConfig.DEBUG`-gated — no flag to flip, just install debug; force-
      rebuild + check APK mtime per [[dinghy-stale-apk-uat-gate]]). Logcat tags: `Dispatcher`
      (reject: key=… reason=in_flight|debounce) and `SwipeDetector` (per-event delta + running total).
- [ ] **R10 rejection flash:** rapid-tap a stepper during a busy window (e.g. while a heater command
      is settling) — the adjuster's hero value flashes amber (one-shot ~200ms), NOT nothing.
- [ ] **R10 disabled stepper = no ripple:** with a dimmed (busy-locked) stepper, a tap produces NO
      ripple at all (the clickable is gone, not guarded).
- [ ] **R10 drawer swipe:** swipe-up opens the App Drawer reliably with slow AND fast gestures
      (drag accumulation fix); no more per-event 80px threshold misses.
- [ ] **R10 on-device FineTuneNavTest:** run the instrumented test — the swipe accumulation fix
      should also clear the harness defect ([[dinghy-instrumented-swipe-threshold]]).
- [ ] **R2 doze survival:** leave the app foregrounded 20+ min with battery-optimization exemption
      granted — websocket stays connected (no doze disconnect).
- [ ] **R2 keep-awake toggle:** Settings toggle ON keeps the screen awake; OFF lets the system
      timeout apply; default is ON.
- [ ] **R1 Nexus-7-unchanged regression:** install the armeabi-v7a APK — app behaves exactly as
      before (no edge-to-edge/inset regressions on API 30, no permission-prompt surprises).

### S25 Ultra group

- [ ] **R1 arm64 install:** the arm64-v8a APK installs and runs (previously no arm64 slice existed).
- [ ] **R1 edge-to-edge:** both orientations — content respects status bar / nav bar / cutout
      (safeDrawingPadding), no drawn-under or clipped UI.
- [ ] **R1 predictive back:** back-gesture preview animates (enableOnBackInvokedCallback).
- [ ] **R1 POST_NOTIFICATIONS one-shot:** first connect triggers exactly one notification-permission
      dialog; denial doesn't break the FGS.
- [ ] **R7 wss connect:** point at a TLS-fronted Moonraker — `useSecure` ON connects over wss/https.
- [ ] **R7 cert-failure message:** a bad/self-signed cert surfaces a readable cert-failure message,
      not a silent spinner.

### CI

- [ ] **R5a CI green:** GitHub Actions run on the `gsd/phase-26.5-overnight-hardening` push is GREEN
      (this also proves the fresh-clone `./gradlew assembleDebug` acceptance — the ubuntu runner IS
      a fresh clone with +x gradlew). If red: triage the failing step (likely action-version or
      SDK-presence assumptions, RESEARCH A2/A3).
