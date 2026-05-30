# Toolkit benchmark — raw rounds (on-device, release mode)

Device: real Nexus 7 2013 (`flox`), **LineageOS 18.1 / Android 11 / API 30**, `armeabi-v7a`,
1200×1920 @ density 320. Build: `:app` release (R8/minify on), debug-signed for install.
Drive: identical per scene — 3 s dwell → `dumpsys gfxinfo reset` → 6 down-flings + 6 up-flings
(`input swipe`, 500 ms settle), `dumpsys gfxinfo <pkg> framestats` dumped after each fling to beat
the ~120-frame ring buffer. Parser: `tools/gfxinfo-parser/parse_framestats.py` (warmup=20, jank=700 ms).
System of record per D-07 = gfxinfo framestats.

All frame times in ms. `frozen` = count(frames > 700 ms).

| Scene   | Round | p50  | p90  | p95  | max   | frozen |
|---------|-------|-----:|-----:|-----:|------:|-------:|
| Compose | r1    | 9.97 | 65.78| 72.94| 133.10|   0    |
| Compose | r2    |10.12 | 72.50| 86.01| 115.41|   0    |
| Compose | r3    | 9.48 | 59.63| 71.24| 121.07|   0    |
| Views   | r1    | 7.57 | 36.02| 43.82| 70.12 |   0    |
| Views   | r2    | 8.16 | 36.68| 41.92| 72.19 |   0    |
| Views   | r3    | 6.44 | 34.96| 38.81| 75.17 |   0    |

**Medians:** Compose p50 9.97 / p90 65.8 / p95 72.9 / max 121 — Views p50 7.57 / p90 36.0 / p95 41.9 / max 72.

Raw CSVs: `compose-framestats.csv` (r1) + `-r2`/`-r3`; `views-framestats.csv` (r1) + `-r2`/`-r3`.
Parsed r1 summaries: `compose-summary.txt`, `views-summary.txt`.

## Caveats (read with the numbers)

- **ART caveat (device reality):** this device runs API 30 (LineageOS), not the project's stated
  stock-6 / API 23. The API-30 runtime is *newer/faster* than stock-6 ART. So on a true API-23
  device the Compose numbers would be **worse**, not better — the Views advantage shown here is a
  **lower bound** on the real target. Hardware (Adreno 320 / 2 GB / 1920×1200 panel) is genuine.
- **Drive fidelity:** scroll was scripted `adb input swipe` (not the macrobenchmark `fling()`), applied
  **identically** to both scenes — so the *comparison* is fair; absolute p95 values would shift under a
  different gesture but the ~2x relative gap is stable across 3 rounds.
- **FrameTimingMetric corroboration** was not separately captured: it requires the release app to be
  `profileable`/debuggable, which would have meant editing 01-01's shared manifest. gfxinfo is the
  D-07 system of record and is self-sufficient for the verdict; FrameTimingMetric is explicitly
  corroboration-only. Deferred as a non-blocking follow-up.
