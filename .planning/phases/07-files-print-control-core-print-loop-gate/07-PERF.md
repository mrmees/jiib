# Phase 07 Files Route And Perf Gate

## Status

PARTIAL - connected flox route-render evidence is PASS; large-library perf/OOM evidence remains PENDING.

## ADB Resolution

- **Timestamp:** 2026-06-02T10:37:46-05:00
- **Resolved path:** `C:\Android\Sdk\platform-tools\adb.exe`
- **Device check:** `/mnt/c/Android/Sdk/platform-tools/adb.exe devices | tr -d '\r'`
- **Device result:** `0a64b42e	device`
- **Device identity:** Nexus 7, Android 11

## Required Route-Render Gate

Run on flox:

```bash
/mnt/c/Windows/System32/cmd.exe /c "set PATH=C:\Android\Sdk\platform-tools;%PATH%&& E:\Android\gw.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=works.mees.dinghy.ui.ShellPresenceTest --no-daemon" | tr -d '\r'
```

Required result:

- `ShellPresenceTest` passes on a real connected flox device.
- Record the Gradle result and XML/result path here.

Route-render result:

- **Result:** PASS
- **Observed:** `Starting 4 tests on Nexus 7 - 11`; `Tests 4/4 completed. (0 skipped) (0 failed)`; `BUILD SUCCESSFUL in 1m 25s`.
- **XML result:** `app/build/outputs/androidTest-results/connected/debug/TEST-Nexus 7 - 11-_app-.xml`
- **HTML report:** `app/build/reports/androidTests/connected/debug/works.mees.dinghy.ui.ShellPresenceTest.html`

## Required Large-Library Fixture

Before the perf run, prepare or select a Files library with:

- At least 200 visible/browsable gcode files across folders.
- At least 50 thumbnail-present rows.
- At least 50 thumbnail-less rows.
- Long filenames and nested folders included.

Record fixture details:

- **Printer:** Ender 5 Plus candidate fixture host reachable at `192.168.1.120:7125`
- **Host/library source:** PENDING - existing `gcodes` library is insufficient for the gate
- **Folder count:** PENDING
- **Total browseable gcode files:** PENDING; initial `/server/files/list?root=gcodes` check returned 25 files, below the required 200
- **Thumbnail-present rows:** PENDING; sampled `ballast middle_PLA_7h26m.gcode` has 32/48/300 thumbnail metadata
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

PENDING - route-render is passed, but do not mark FILE-02 passed until large-library perf/OOM evidence is recorded.
