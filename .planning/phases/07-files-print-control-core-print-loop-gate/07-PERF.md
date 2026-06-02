# Phase 07 Files Route And Perf Gate

## Status

PENDING - connected flox route/perf evidence has not been collected.

## Blocker

- **Timestamp:** 2026-06-02T10:22:09-05:00
- **Observed locally:** `adb` is not available in WSL or the Windows command environment.
- **WSL check:** `adb devices` -> `/bin/bash: line 1: adb: command not found`
- **Windows cmd check:** `/mnt/c/Windows/System32/cmd.exe /c "adb devices" | tr -d '\r'` -> `'adb' is not recognized as an internal or external command`

## Required Route-Render Gate

Run on flox:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=works.mees.dinghy.ui.ShellPresenceTest --no-daemon" | tr -d '\r'
```

Required result:

- `ShellPresenceTest` passes on a real connected flox device.
- Record the Gradle result and XML/result path here.

## Required Large-Library Fixture

Before the perf run, prepare or select a Files library with:

- At least 200 visible/browsable gcode files across folders.
- At least 50 thumbnail-present rows.
- At least 50 thumbnail-less rows.
- Long filenames and nested folders included.

Record fixture details:

- **Printer:** PENDING
- **Host/library source:** PENDING
- **Folder count:** PENDING
- **Total browseable gcode files:** PENDING
- **Thumbnail-present rows:** PENDING
- **Thumbnail-less rows:** PENDING

## Required Perf Threshold

Pass threshold, declared before the run:

- Zero frozen frames over 700 ms.
- No OOM.
- No ANR.
- No process death.
- p95 frame duration at or below the established sparse-redraw/list-scroll budget used by prior flox gates unless a stricter threshold is documented before capture.

## Required Gfxinfo Procedure

1. Install/run the verified build on flox.
2. Open Files.
3. Reset gfxinfo:

```bash
adb shell dumpsys gfxinfo works.mees.dinghy reset
```

4. Drive repeatable scroll sequence through the mixed library, for example 6 down-flings and 6 up-flings with 500 ms settle.
5. Dump framestats after each segment to avoid ring-buffer truncation:

```bash
adb shell dumpsys gfxinfo works.mees.dinghy framestats > .planning/phases/07-files-print-control-core-print-loop-gate/gfxinfo-files-segment-N.txt
```

6. Parse with:

```bash
python tools/gfxinfo-parser/parse_framestats.py .planning/phases/07-files-print-control-core-print-loop-gate/gfxinfo-files-segment-N.txt
```

Record parsed results:

| Segment | p50 | p90 | p95 | Frozen >700ms | OOM/ANR/Death | Pass/Fail |
|---|---:|---:|---:|---:|---|---|
| PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING |

## Gate Result

PENDING - do not mark FILE-02 passed until route-render and large-library perf/OOM evidence are recorded.
