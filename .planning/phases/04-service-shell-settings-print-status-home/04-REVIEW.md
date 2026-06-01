---
phase: 04-service-shell-settings-print-status-home
reviewed: 2026-05-31T00:00:00Z
depth: standard
files_reviewed: 22
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt
  - app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt
  - app/src/main/java/works/mees/dinghy/config/ConnectionStore.kt
  - app/src/main/java/works/mees/dinghy/config/MoonrakerDiscovery.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/DinghyApp.kt
  - app/src/main/java/works/mees/dinghy/di/SessionControl.kt
  - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
  - app/src/main/java/works/mees/dinghy/MainActivity.kt
  - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
  - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
  - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
  - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/SplashScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues
---

# Phase 4: Code Review Report

**Reviewed:** 2026-05-31
**Depth:** standard
**Files Reviewed:** 22
**Status:** issues_found

## Summary

Phase 4 is a substantial, well-structured piece of work. The security-critical paths (API key
redaction in `ConnectionConfig.toString`, the notification pipeline, `CommandDispatcher` failure
messages, and `SettingsScreen`'s no-clobber key logic) are all correct. The concurrency
architecture — atomic `SpineHandle` publication, `collectLatest + cancelAndJoin` rebuild loop,
`flatMapLatest`-derived per-field flows — is sound and the test coverage for those seams is credible.
Token purity holds across every UI file (the one `Color(fillArgb)` in `SettingsScreen` is the
intentional palette-data exception, correctly documented).

Three warnings surfaced. Two are defects that will cause observable incorrect behavior in production
use; one is a latent thread-safety exposure. Three info items cover minor display correctness and
naming concerns.

## Warnings

### WR-01: `failureText` in `PrintStatusScreen` is permanent — no dismiss path

**File:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:91,126-128`

**Issue:** `failureText` is set to a `DispatchEvent.Failure` message when an estop (or any
dispatched command) fails and is rendered as a `SeverityToast` in the Field column. It is never
cleared. There is no dismiss button, no timeout, and `LaunchedEffect(dispatcher)` does not reset
it when the key changes — it only replaces the collect. Once an estop times out or fails (printer
still running, screen stays open), the red error toast occupies space in the live stat grid
permanently for the rest of the session. The toast banner pushes up against the sparkline and stat
grid, visually corrupting the "normal running" readout.

The bug only bites on the failure path (estop success drives klippy to shutdown, which routes to
Splash, removing the screen), but the failure path is exactly when the user is most stressed.

**Fix:** Reset `failureText` to `null` when the `LaunchedEffect` restarts (new dispatcher = new
session, old failures are stale), and add an auto-dismiss via `LaunchedEffect(failureText)`:

```kotlin
// Reset stale failures when the session changes.
LaunchedEffect(dispatcher) {
    failureText = null          // <-- add this
    val d = dispatcher ?: return@LaunchedEffect
    d.events.collect { event ->
        when (event) {
            is DispatchEvent.Failure -> failureText = event.message
        }
    }
}

// Auto-dismiss after a few seconds so the grid is not permanently obscured.
LaunchedEffect(failureText) {
    if (failureText != null) {
        delay(4_000)
        failureText = null
    }
}
```

---

### WR-02: `CommandDispatcher.lastAccepted` is not thread-safe

**File:** `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt:82,95-97`

**Issue:** `lastAccepted` is a plain `mutableMapOf<String, Long>()` (a `LinkedHashMap`). The
`dispatch()` method reads from and writes to it on lines 95–97. `dispatch()` is not annotated
`@MainThread` and the `CommandDispatcher` is constructed with `serviceScope` (backed by
`Dispatchers.Default`, a thread pool). In the current app all call sites happen to be main-thread
onClick handlers, so this does not crash today. But:

- `SessionControl.restartFirmware()` and `restartHost()` are interface methods on an object
  constructed in `MoonrakerService`; nothing prevents a future caller from invoking them off-main.
- The check-then-update on `_inFlight` (line 91 read, line 99 CAS) is a separate but related
  TOCTOU: if `dispatch()` were ever called from two threads concurrently for the same key, both
  could pass the busy guard before either update lands. In practice only the HashMap is the live
  risk.

**Fix:** Replace the `HashMap` with a `ConcurrentHashMap` (or, since all current callers are
main-thread, add a `@MainThread` annotation to `dispatch()` and document the invariant explicitly
so it's enforced rather than assumed):

```kotlin
// Option A — enforce single-thread discipline:
@androidx.annotation.MainThread
fun dispatch(key: String, method: String, params: JsonElement? = null) { … }

// Option B — make it safe for any caller:
private val lastAccepted = java.util.concurrent.ConcurrentHashMap<String, Long>()
```

The `@MainThread` annotation is cheaper and honest about intent; `ConcurrentHashMap` is safer if
the interface boundary ever widens.

---

### WR-03: `MoonrakerDiscovery.SERVICE_TYPE` missing the trailing dot may silently suppress results on some Android versions

**File:** `app/src/main/java/works/mees/dinghy/config/MoonrakerDiscovery.kt:116`

**Issue:** `SERVICE_TYPE = "_moonraker._tcp"` is passed to `NsdManager.discoverServices()`. The
DNS-SD specification requires a fully-qualified service type ending in `.` (e.g.
`"_moonraker._tcp."`) or at minimum the `local.` domain appended
(`"_moonraker._tcp.local."`). Android's `NsdManager` documentation shows `"_http._tcp."` (with
trailing dot) in its examples. Several tested devices (particularly pre-API-29 paths relevant to
the API-23 floor) silently fail to start discovery or return zero results when the trailing dot is
absent, with no error callback fired. The `onStartDiscoveryFailed` swallows errors, so this failure
mode is invisible.

```kotlin
// Current (potentially broken on some API 23–28 devices):
const val SERVICE_TYPE = "_moonraker._tcp"

// Fix:
const val SERVICE_TYPE = "_moonraker._tcp."
```

Manual entry remains the floor (D-04), so this does not block shipping, but Scan returning zero
results on the very hardware this feature targets (old Wi-Fi routers + Android 6–8 devices) would
make the Scan button appear broken.

---

## Info

### IN-01: `fmt()` uses truncation, not rounding — temperatures read low at .x5–.x9

**File:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:325`

**Issue:** `((v * 10).toInt() / 10.0).toString()` truncates toward zero rather than rounding.
A nozzle at 249.97 °C displays as "249.9°" instead of "250.0°". For a heated bed holding
99.95 °C (target 100), the display reads "99.9°" and never reaches the displayed setpoint. This
can confuse a user watching a heat soak. Thermistor noise means the sensor value oscillates
fractionally, so it's common for the display to read 0.1° below the setpoint continuously.

```kotlin
// Current (truncates):
private fun fmt(v: Double): String = ((v * 10).toInt() / 10.0).toString()

// Fix (rounds to 1 decimal):
private fun fmt(v: Double): String = String.format("%.1f", v)
// or:
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()
```

---

### IN-02: Multi-extruder grid shows duplicate "NOZZLE" labels

**File:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:317-322`

**Issue:** `label()` maps any object name starting with `"extruder"` to `"NOZZLE"`. On a printer
with `[extruder]` and `[extruder1]` and no `heater_bed`, `resolveSecondary` promotes `extruder1`
as the secondary slot. The 2×3 grid then renders two cells both labelled `"NOZZLE"`, making them
indistinguishable. The spec notes that "nonstandard heater names are surfaced verbatim" but
`extruder1` is not actually nonstandard — it has a conventional multi-tool name.

```kotlin
// Current:
objectName == "extruder" || objectName.startsWith("extruder") -> "NOZZLE"

// Fix: only "extruder" alone maps to NOZZLE; numbered extruders show their index:
objectName == "extruder" -> "NOZZLE"
objectName.startsWith("extruder") -> "NOZZLE ${objectName.removePrefix("extruder")}"
// e.g. extruder1 -> "NOZZLE 1", extruder2 -> "NOZZLE 2"
```

---

### IN-03: `SessionControl.kt` KDoc has a broken sentence (extra "a")

**File:** `app/src/main/java/works/mees/dinghy/di/SessionControl.kt:7`

**Issue:** Line 7 reads: `"It deliberately exposes NO member that returns a the raw session type"`.
The word "a" is duplicated before "the", producing a grammatically broken sentence in the interface
documentation. Minor but it lives on the interface boundary that reviewers read most.

```kotlin
// Current:
// It deliberately exposes NO member that returns a the raw session type

// Fix:
// It deliberately exposes NO member that returns the raw session type
```

---

_Reviewed: 2026-05-31_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
