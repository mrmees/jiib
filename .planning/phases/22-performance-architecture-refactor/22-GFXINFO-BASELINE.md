# 22 — gfxinfo Release-Mode Baseline (flox)

**The SC1 before-anchor.** Release-mode `dumpsys gfxinfo … framestats` capture across the D-06
sweep set, taken on flox BEFORE any Phase-22 source edit (hard pre-edit gate, D-05/D-06). This
cannot be reconstructed after code changes — every after-measurement (Plan 06 graph, Plan 07
final sweep) compares back to these numbers.

Metrics are from `tools/gfxinfo-parser/parse_framestats.py` (the system of record): p50/p90/p95
frame time (ms, deduped by IntendedVsync) + count of frozen frames (>700 ms). ADR-0001
Addendum-2 gate = **zero frozen frames + responsive**; p95 is a sanity check, not a hard ms target.

> Capture method per RESEARCH §7: navigate → `gfxinfo … reset` → exercise → dump framestats
> IMMEDIATELY (the ~120-frame ring buffer wraps fast on 60 Hz screens), repeated as multiple
> short windows per screen, concatenated through the parser (it dedupes by IntendedVsync).
> Captures driven via `adb input` (tile taps / swipes) by Claude; owner present.

> **Warmup note:** the parser default `--warmup 20` is tuned for 60 Hz scroll. Idle/steady screens
> redraw at ~4 Hz (PrinterState emission), so a window holds far fewer frames; those screens were
> parsed with a reduced `--warmup` (5–8) so the steady-state 4 Hz redraw cost isn't warmup-excluded.
> The per-screen `--warmup` used is recorded in each section.

---

## Reproducibility Header

| Field | Value |
|-------|-------|
| Commit SHA (HEAD at capture) | `da363ee` (da363eedfd697213e7df67a6aa33961803b48c40) — **no source edits at capture** (hard pre-edit gate honored) |
| Build timestamp (APK mtime, UTC) | 2026-06-09T00:29:23Z |
| APK variant | release · R8/minify on · `armeabi-v7a` ABI split · zipalign + **debug-signed** (`sign-release.bat`, debug keystore — matches the installed signature so `install -r` preserved DataStore) |
| Device | flox — Nexus 7 2013, Adreno 320, 2 GB, 1920×1200, LineageOS 18.1 / **Android 11 / API 30** |
| Orientation | **Landscape** (the natural tablet orientation; all captures landscape) |
| Theme | **Dark** |
| Text size (`--fs`) | M (default — not changed this session) |
| Printer connection / state | **Ender 3 Pro — 192.168.1.121:7125** (SKR board; `ender3.local`), Klipper connected. Idle captures at ambient (Nozzle ~25 °C / Bed ~26 °C); mid-print capture during an active print (`slide test_PLA_29m35s.gcode`, nozzle 195/195 °C, bed 65/65 °C, ~1–2 %). |
| GPU profiling | `dumpsys gfxinfo … framestats` collects per-frame stats directly for the running app (no extra dev-toggle needed on API 30); confirmed working. |

---

## Sweep Screens (D-06)

Per-window data is concatenated through `parse_framestats.py`; the row below is the **combined**
result across that screen's windows. `frames` = deduped frames measured (post-warmup).

| # | Screen | windows | warmup | frames | p50 | p90 | p95 | max | **frozen (>700ms)** |
|---|--------|--------:|-------:|-------:|----:|----:|----:|----:|:---:|
| 1 | PrintStatus — idle | 3 × ~12 s | 5 | 106 | **43.02** | 56.76 | 58.50 | 71.42 | **0** |
| 2 | PrintStatus — mid-print | 3 × ~12 s | 5 | 134 | **54.16** | 62.35 | 67.62 | 82.52 | **0** |
| 3 | App Drawer — open/close | 3 | 5 | 254 | **21.38** | 39.12 | 39.42 | 75.07 | **0** |
| 4 | Files — list scroll | 3 | 10 | 301 | **8.91** | 41.95 | 42.96 | 43.96 | **0** |
| 5 | Console — live + scroll | 3 | 8 | 306 | **7.79** | 9.26 | 10.29 | 17.35 | **0** |
| 6 | Temperature — multi-trace graph | 3 × ~10 s | 5 | 84 | **54.97** | 65.79 | 69.47 | 76.37 | **0** |
| 7 | Webcam — live H.264 | 4 × ~4 s | — | 0† | — | — | — | — | **0** |
| 8 | Move — scrubber/control | 2 | 5 | 10‡ | **17.42** | 23.79 | 25.06 | 25.06 | **0** |

† **Webcam:** the live feed renders on a dedicated **SurfaceView** (H.264 HW decode, OMX.qcom,
composited by SurfaceFlinger — confirmed: `dumpsys SurfaceFlinger --list` shows a `SurfaceView`
for `MainActivity`, and `dumpsys gfxinfo` reports **"Total frames rendered: 0"** during steady
playback). The video bypasses the app's HWUI pipeline, so app-level framestats is structurally
**0 frames** — there is no app-pipeline jank to measure during steady playback, and **nothing the
Phase-22 Compose/collection refactor touches affects it** (no before/after delta expected). The
overlay (camera chip + Back) is static. The one real webcam perf risk — rotation/surface
re-prepare blanking — is a *transition* event tracked separately (Phase-21 CR-01, deferred). Feed
was `c270_hd_webcam` (Logitech C270) over ravens-perch/MediaMTX.

‡ **Move:** thin sample (10 frames) — the control screen only redraws on interaction (step-size
taps, no printer motion commanded). Cheap (~17 ms p50), 0 frozen; representative of a static
control surface.

### Per-screen exercise steps (reproducibility)

1. **PrintStatus idle** — sit on the home/PrintStatus screen, printer idle, let the 4 Hz state
   stream drive redraws. 3 × ~12 s windows.
2. **PrintStatus mid-print** — sit on PrintStatus while the E3 actively prints (`slide
   test_PLA_29m35s.gcode`, ~1–2 %, nozzle 195/195 °C, bed 65/65 °C): progress ring, layer
   counter, live nozzle/bed temps, ETA, spool-remaining, and the model thumbnail all updating at
   4 Hz. 3 × ~12 s windows. This is the worst-case recomposition window.
3. **App Drawer** — swipe up from bottom (≈960,1120 → 960,250, 180 ms) to open, hold ~1 s, swipe
   down to close; repeat per window. (Drawer open confirmed via screenshot.)
4. **Files** — open Files tile, swipe the gcode list up/down (≈x1400, y950↔350) ~4× per window.
5. **Console** — open Console tile, swipe the scrollback up/down, let live gcode responses stream.
6. **Temperature** — open Temperature tile, let the live multi-trace graph (nozzle + bed, with
   gradient area fills) redraw. 3 × ~10 s windows.
7. **Webcam** — open Webcam tile, live C270 feed streaming. 4 × ~4 s windows.
8. **Move** — open Move tile, tap the step-size selector buttons (0.1/1/10/25/50/100) — UI
   recomposition only, **no jog/motion commanded** (axes unhomed). 2 windows.

---

## Reading the baseline (what it tells us going in)

- **Zero frozen frames (>700 ms) on every captured screen** → the app already passes the ADR-0001
  Addendum-2 hard gate (no frozen frames + responsive) *before* the refactor. Phase 22 is about
  per-frame cost / wide-recomposition headroom, not rescuing a frozen UI.
- **PrintStatus mid-print p50 = 54 ms, idle p50 = 43 ms, Temperature p50 = 55 ms are the standout
  costs.** The whole PrintStatus recomposes on every 4 Hz emission (unstable `PrinterState`); at
  idle each redraw costs ~43 ms and **mid-print climbs to ~54 ms (p95 68 ms)** because the extra
  live fields (progress %, layer, ETA, spool, model thumbnail) widen the recomposition — all well
  over the 16.6 ms/60 Hz budget. These are the screens the Phase-22 work (22-02 `PrinterState`
  `@Immutable`, 22-04 god-component split, 22-06 graph overdraw, 22-07 collection push-down) is
  expected to move. **Mid-print and idle PrintStatus + Temperature are the primary SC1 before/after
  comparison points.**
- **Console (7.8 ms) and Files (8.9 ms p50) are already excellent** — the Views RecyclerView paths
  chosen in ADR-0001 are doing their job. Low refactor expectation here (regression-watch only).
- **Webcam is outside the measurable pipeline** (SurfaceView) — exclude from before/after deltas.

---

## Owner approval

- [x] **All 8 D-06 sweep screens** captured: PrintStatus idle + mid-print, App Drawer, Files,
      Console, Temperature, Move (multi-window each) + Webcam characterized (SurfaceView → not
      HWUI-measurable).
- [x] Reproducibility header complete (SHA / variant / orientation / theme / --fs / printer state).
- [x] **PrintStatus mid-print** captured during an active E3 print (worst-case 4 Hz recomposition).
- [x] No source file edited at capture (pre-edit gate) — **confirmed** (`git status` clean of source).
- [x] Owner replies **"approved"** (2026-06-08).
