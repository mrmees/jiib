# Phase 6 Verification

**Plan:** 06-05 - Final host verification and flox regression checkpoint
**Started:** 2026-06-02T12:06:58Z
**Automated gates completed:** 2026-06-02T12:07:52Z
**Status:** Automated gates green; flox regression awaiting human approval.

## Automated Gates

| Gate | Command | Status | Evidence |
| --- | --- | --- | --- |
| Catalog JSON parse | `python3 -m json.tool docs/commands/catalog.json >/dev/null` | PASS | Command exited 0. |
| Printer matrix JSON parse | `python3 -m json.tool docs/commands/printer-matrix.json >/dev/null` | PASS | Command exited 0. |
| Targeted Phase 6 tests | `set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandCatalogDriftTest --tests *CommandRegistryGcodeTest --tests *CommandDispatcherTest --tests *HandshakeTest --no-daemon" \| tr -d '\r'` | PASS | `BUILD SUCCESSFUL in 13s`; `35 actionable tasks: 2 executed, 33 up-to-date`. |
| Full release unit suite and release Kotlin compile | `set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon" \| tr -d '\r'` | PASS | `:app:testReleaseUnitTest` ran; `:app:compileReleaseKotlin UP-TO-DATE`; `BUILD SUCCESSFUL in 14s`; `35 actionable tasks: 2 executed, 33 up-to-date`. |

## Acceptance Criteria

- `catalog.json` parses: PASS.
- `printer-matrix.json` parses: PASS.
- `:app:testReleaseUnitTest` passes: PASS.
- `:app:compileReleaseKotlin` passes: PASS.
- Exact Windows wrapper commands and pass status are recorded above: PASS.

## flox Regression

**Status:** PENDING - release APK built/installed/launched on flox; blocking human regression approval still required.

### Build/Install Evidence

Recorded 2026-06-02T12:11:59Z:

- `set -o pipefail; /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --no-daemon" | tr -d '\r'` - PASS, `BUILD SUCCESSFUL in 56s`.
- `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\sign-release.bat app\build\outputs\apk\release\app-armeabi-v7a-release-unsigned.apk app\build\outputs\apk\release\app-armeabi-v7a-release-debugsigned.apk" | tr -d '\r'` - PASS, `SIGN_EXIT=0`.
- `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\Sdk\platform-tools\adb.exe -s 0a64b42e install -r app\build\outputs\apk\release\app-armeabi-v7a-release-debugsigned.apk" | tr -d '\r'` - PASS, `Success`.
- `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\Sdk\platform-tools\adb.exe -s 0a64b42e shell monkey -p works.mees.dinghy -c android.intent.category.LAUNCHER 1" | tr -d '\r'` - PASS, `Events injected: 1`.

Run only after the automated gates above are green:

1. Build/install the current release APK using the established Windows-side flow. If release signing is still debug-sign helper based, use `E:\Android\sign-release.bat`.
2. On flox connected to the Ender 5 Plus, verify Move: jog X/Y/Z, home all or axis, disable steppers through ConfirmGuard.
3. Verify Temperature: set a heater target, apply a preset, and cooldown.
4. Verify Extrude: extrude, retract, `LOAD_FILAMENT`/`UNLOAD_FILAMENT` when present, and missing-macro informational popup on a printer/profile where absent.
5. Verify Print Status stop: Stop opens ConfirmGuard, confirm sends `printer.emergency_stop`, and klippy shutdown routes to Splash.
6. If reachable after stop, verify recovery actions still dispatch firmware restart / host restart through the narrow `SessionControl`.

### Required Human Result

Awaiting explicit response: type `approved` if the flox regression passes, or describe the exact failed action and observed behavior.

### Blocking Criteria

- No action regresses relative to Phase 5 observed behavior.
- Long gcode actions do not show the old false 10s `could not be sent` failure.
- E-stop is immediate through `printer.emergency_stop`; no queued `M112` path is observed or present.
- This file must be updated with manual pass/fail notes and printer-specific caveats before Task 3 can run.
