#!/usr/bin/env python3
"""
parse_framestats.py — turn `adb shell dumpsys gfxinfo <pkg> framestats` output into the
D-04 toolkit metrics: p50 / p90 / p95 frame time + count(frames > 700 ms).

This is the SYSTEM OF RECORD for the Compose-vs-Views toolkit gate (D-07). Macrobenchmark
`FrameTimingMetric` is corroboration only; the authoritative percentiles come from THIS
parser run against the raw framestats CSV captured on the Nexus 7 (API 23).

Why a custom parser (RESEARCH Pitfalls 2 & 3):
  * The human-readable "Janky frames: X%" summary line is explicitly forbidden (D-04) —
    we parse the raw per-frame CSV.
  * `dumpsys gfxinfo` keeps only a ~120-frame ring buffer per dump, so a real run dumps
    framestats REPEATEDLY and concatenates them. This parser DEDUPES overlapping frames by
    their IntendedVsync timestamp so the ring-buffer overlap doesn't double-count.
  * A warmup window is excluded so first-frame / cold-cache costs don't skew percentiles.

framestats CSV schema (Android `dumpsys gfxinfo <pkg> framestats`):
    Flags,IntendedVsync,Vsync,OldestInputEvent,NewestInputEvent,HandleInputStart,
    AnimationStart,PerformTraversalsStart,DrawStart,SyncQueued,SyncStart,
    IssueDrawCommandsStart,SwapBuffers,FrameCompleted[,DequeueBufferDuration,...]
All values are nanoseconds. Per-frame total time = FrameCompleted - IntendedVsync.
A non-zero Flags value marks a frame the platform itself considers skipped/abnormal
(e.g. first-draw / window-layout-changed); those are excluded from percentiles by default.

Usage:
    # one or more captured dumps (concatenated automatically; ring-buffer overlap deduped)
    python3 parse_framestats.py capture1.txt capture2.txt ...
    adb shell dumpsys gfxinfo works.mees.dinghy framestats | python3 parse_framestats.py -
    python3 parse_framestats.py --warmup 30 --jank-ms 700 capture.txt
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass

# Column indices into the framestats CSV (0-based), per the schema above.
COL_FLAGS = 0
COL_INTENDED_VSYNC = 1
COL_FRAME_COMPLETED = 13
MIN_COLUMNS = COL_FRAME_COMPLETED + 1

NS_PER_MS = 1_000_000.0

# Default jank / frozen-frame threshold (D-04 frozen-frame detector).
DEFAULT_JANK_MS = 700.0
# Default number of leading (post-dedup, time-ordered) frames treated as warmup.
DEFAULT_WARMUP = 20


@dataclass(frozen=True)
class Frame:
    intended_vsync: int  # ns — also the dedup key (unique per real frame)
    flags: int
    total_ms: float


def _is_framestats_row(parts: list[str]) -> bool:
    """A data row is all-integer and has at least the columns we read."""
    if len(parts) < MIN_COLUMNS:
        return False
    for p in parts[:MIN_COLUMNS]:
        s = p.strip()
        if not (s.lstrip("-").isdigit()):
            return False
    return True


def parse_frames(text: str, include_flagged: bool = False) -> list[Frame]:
    """
    Parse every framestats CSV row found across one or more concatenated dumps and DEDUPE
    by IntendedVsync (the ring-buffer overlap key). Returns frames sorted by IntendedVsync.

    Non-data lines (headers, the 'Flags,IntendedVsync,...' label line, summary text) are
    ignored — we accept only rows whose leading columns are all integers.
    """
    by_vsync: dict[int, Frame] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or "," not in line:
            continue
        parts = line.split(",")
        if not _is_framestats_row(parts):
            continue
        try:
            flags = int(parts[COL_FLAGS])
            intended = int(parts[COL_INTENDED_VSYNC])
            completed = int(parts[COL_FRAME_COMPLETED])
        except ValueError:
            continue

        # A non-zero flag = platform-marked abnormal frame; exclude unless asked.
        if flags != 0 and not include_flagged:
            continue
        # Guard against malformed/zero-vsync rows.
        if intended <= 0 or completed <= intended:
            continue

        total_ms = (completed - intended) / NS_PER_MS
        # Dedupe: identical IntendedVsync across overlapping dumps == the same frame.
        by_vsync[intended] = Frame(intended_vsync=intended, flags=flags, total_ms=total_ms)

    return sorted(by_vsync.values(), key=lambda f: f.intended_vsync)


def percentile(sorted_values: list[float], pct: float) -> float:
    """Nearest-rank percentile on an already-sorted list. pct in [0, 100]."""
    if not sorted_values:
        return float("nan")
    if len(sorted_values) == 1:
        return sorted_values[0]
    # Nearest-rank: rank = ceil(pct/100 * N), 1-based.
    import math

    rank = max(1, math.ceil((pct / 100.0) * len(sorted_values)))
    return sorted_values[min(rank, len(sorted_values)) - 1]


@dataclass(frozen=True)
class Stats:
    count: int
    warmup_excluded: int
    p50_ms: float
    p90_ms: float
    p95_ms: float
    jank_over_threshold: int
    jank_threshold_ms: float
    max_ms: float


def compute_stats(
    frames: list[Frame],
    warmup: int = DEFAULT_WARMUP,
    jank_ms: float = DEFAULT_JANK_MS,
) -> Stats:
    """Exclude the leading `warmup` frames, then compute D-04 metrics on the rest."""
    measured = frames[warmup:] if warmup > 0 else frames
    excluded = len(frames) - len(measured)
    times = sorted(f.total_ms for f in measured)
    jank = sum(1 for t in times if t > jank_ms)
    return Stats(
        count=len(times),
        warmup_excluded=excluded,
        p50_ms=percentile(times, 50),
        p90_ms=percentile(times, 90),
        p95_ms=percentile(times, 95),
        jank_over_threshold=jank,
        jank_threshold_ms=jank_ms,
        max_ms=times[-1] if times else float("nan"),
    )


def _read_inputs(paths: list[str]) -> str:
    if not paths:
        return sys.stdin.read()
    chunks: list[str] = []
    for p in paths:
        if p == "-":
            chunks.append(sys.stdin.read())
        else:
            with open(p, "r", encoding="utf-8", errors="replace") as fh:
                chunks.append(fh.read())
    return "\n".join(chunks)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Parse gfxinfo framestats CSV → D-04 metrics.")
    ap.add_argument("inputs", nargs="*", help="framestats dump file(s); '-' or none = stdin")
    ap.add_argument("--warmup", type=int, default=DEFAULT_WARMUP,
                    help=f"leading frames to exclude as warmup (default {DEFAULT_WARMUP})")
    ap.add_argument("--jank-ms", type=float, default=DEFAULT_JANK_MS,
                    help=f"frozen-frame threshold in ms (default {DEFAULT_JANK_MS})")
    ap.add_argument("--include-flagged", action="store_true",
                    help="include platform-flagged (non-zero Flags) frames in percentiles")
    args = ap.parse_args(argv)

    text = _read_inputs(args.inputs)
    frames = parse_frames(text, include_flagged=args.include_flagged)
    if not frames:
        print("ERROR: no framestats rows parsed (is this a 'gfxinfo … framestats' dump?)",
              file=sys.stderr)
        return 2

    stats = compute_stats(frames, warmup=args.warmup, jank_ms=args.jank_ms)
    print(f"frames (deduped):     {len(frames)}")
    print(f"warmup excluded:      {stats.warmup_excluded}")
    print(f"frames measured:      {stats.count}")
    print(f"p50 frame time (ms):  {stats.p50_ms:.2f}")
    print(f"p90 frame time (ms):  {stats.p90_ms:.2f}")
    print(f"p95 frame time (ms):  {stats.p95_ms:.2f}")
    print(f"max frame time (ms):  {stats.max_ms:.2f}")
    print(f"frames > {stats.jank_threshold_ms:.0f} ms:      {stats.jank_over_threshold}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
