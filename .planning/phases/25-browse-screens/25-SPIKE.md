# Phase 25 Browse-Screens Toolkit Spike: Per-Surface gfxinfo Verdict

**Captured:** 2026-06-10  
**Device:** flox — Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30 / `armeabi-v7a`  
**Build:** release APK, debug-signed via `sign-release.bat`, installed via `adb install`  
**Parser:** `tools/gfxinfo-parser/parse_framestats.py --warmup 10`  
**Gate:** ADR-0001 Addendum-2 — 0 frozen frames AND p90 within the floor budget

## VERDICTS (BINDING — 25-03 and 25-04 branch on these)

| Surface | Verdict | Rationale |
|---------|---------|-----------|
| **Files** | **Compose** (migrate to literal `ListRow`) | Compose p90 16.42 ms vs Views baseline 41.95 ms — improvement; 0 frozen frames; gate PASS |
| **Console** | **Views** (keep RecyclerView/Views scrollback, conform visually) | Compose p90 73.35 ms vs Views baseline 9.26 ms — ~8× regression; gate FAIL |

Neither verdict is ambiguous.

---

## Files Surface

**Workload:** 60 synthetic thumbnail rows, Coil `AsyncImage` per row, 4 full scroll sweeps up+down (so Coil decode/fill cost is captured — representative of the real `FileRowsAdapter` per-row bind cost).

### Compose spike numbers (109 frames, warmup 10 discarded)

| Metric | Value |
|--------|-------|
| p50 | 10.71 ms |
| p90 | **16.42 ms** |
| p95 | 19.89 ms |
| max | 34.05 ms |
| frozen frames (>700 ms) | **0** |

### Phase-22 Views baseline (from `22-GFXINFO-BASELINE.md`)

| Metric | Value |
|--------|-------|
| p50 | 8.91 ms |
| p90 | 41.95 ms |
| p95 | 42.96 ms |
| frozen frames | 0 |

### Gate evaluation (ADR-0001 Addendum-2)

- Frozen frames: 0 ✓ (gate requires 0)
- p90: 16.42 ms vs floor budget ~42 ms (parity-or-better) ✓ — Compose is **2.6× faster** at p90
- **VERDICT: PASS → Files: Compose (migrate to literal `ListRow`)**

The Compose `LazyColumn + ListRow` path with thumbnail images outperforms the Views baseline at p90. The FilesScreen migration (25-03) SHALL use literal `ListBlock`/`ListRow`.

---

## Console Surface

**Workload:** 300 synthetic lines seeded, live append+evict ticker running at ~10 lines/sec (realistic Klipper gcode-response rate), auto-scroll to bottom active. Two captures:

1. **Mixed: churn + manual scroll** (110 frames)
2. **Diagnostic pure-churn, NO manual scroll** (110 frames) — isolates the recomposition cost of the live append+evict path itself, confirming it is the bottleneck, not scroll interaction.

### Compose spike numbers — mixed (churn + manual scroll, 110 frames)

| Metric | Value |
|--------|-------|
| p50 | 42.67 ms |
| p90 | **73.35 ms** |
| p95 | 80.22 ms |
| max | 86.76 ms |
| frozen frames (>700 ms) | **0** |

### Compose spike numbers — diagnostic pure-churn, no manual scroll (110 frames)

| Metric | Value |
|--------|-------|
| p50 | 46.00 ms |
| p90 | **74.94 ms** |
| p95 | 86.06 ms |
| max | 94.90 ms |
| frozen frames (>700 ms) | **0** |

The pure-churn diagnostic confirms that **the live append+evict recomposition is the cost source**, not scroll interaction. Churn-only p90 (74.94 ms) is marginally worse than the mixed capture (73.35 ms), ruling out scroll as a contributing factor.

### Phase-22 Views baseline (from `22-GFXINFO-BASELINE.md`)

| Metric | Value |
|--------|-------|
| p50 | 7.79 ms |
| p90 | 9.26 ms |
| p95 | 10.29 ms |
| frozen frames | 0 |

### Gate evaluation (ADR-0001 Addendum-2)

- Frozen frames: 0 ✓ (gate requires 0)
- p90: 73.35 ms vs floor budget ~10 ms (parity-or-better) ✗ — **~8× regression**
- **VERDICT: FAIL → Console: Views (keep RecyclerView/Views scrollback, conform visually per D-02)**

The Compose `LazyColumn` path cannot match the Views baseline under live-churn conditions on Adreno 320. The live append+evict recomposition cost dominates; this is the same pattern ADR-0001 identified at Phase 1 (high-churn lists → Views). The ConsoleScreen migration (25-04) SHALL keep the existing `ConsoleListView` RecyclerView and conform it visually to the jiib design kit tokens.

---

## adb Launch Notes (for 25-07 teardown)

`BrowseSpikeActivity` is registered as `android:exported="false"` with no LAUNCHER intent-filter. Shell launch (`adb shell am start`) is denied for exported=false activities unless adbd is running as root.

**Required launch sequence:**
```bash
adb root            # restart adbd as root (LineageOS allows this)
adb shell am start -n works.mees.dinghy/.dev.BrowseSpikeActivity --es spike_surface files
```

The `adb root` step must be repeated after each adb disconnect. The `am start` command succeeds only after adbd has restarted as root — attempt immediately after will error with "Error type 3 / Activity not found or not exported". This is expected behavior; it is NOT a manifest misconfiguration.

25-07 teardown deletes `BrowseSpikeActivity.kt` and the manifest entry. After deletion, this launch sequence is no longer valid (intended).

---

## Summary

| Surface | Compose p90 | Views baseline p90 | Delta | Verdict |
|---------|-------------|-------------------|-------|---------|
| Files | 16.42 ms | 41.95 ms | −60% | **Files: Compose** |
| Console | 73.35 ms | 9.26 ms | +692% (~8×) | **Console: Views** |

The split outcome matches ADR-0001's general principle: thumbnail-bearing file list (latency dominated by image decode, stable list) benefits from Compose; high-churn console (live append+evict forcing constant recomposition) is the exact use-case where Views outperforms Compose on Adreno 320 hardware.
