---
phase: 13-optimization-network-efficiency-end-to-end-reliability
reviewed: 2026-06-03T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
  - app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt
  - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
  - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
  - app/src/test/java/works/mees/dinghy/net/FakeWebSocket.kt
  - app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt
  - app/src/test/java/works/mees/dinghy/net/KlippyReadyResyncTest.kt
  - app/src/test/java/works/mees/dinghy/net/KlippyRecoveryStateTest.kt
  - app/src/test/java/works/mees/dinghy/net/MoonrakerSocketClientTest.kt
  - app/src/test/java/works/mees/dinghy/net/ProbeZOffsetFreshnessTest.kt
  - app/src/test/java/works/mees/dinghy/net/ReconnectSupervisorTest.kt
  - app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt
  - docs/request-cadence-contract.md
  - tools/ws-capture.py
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase 13: Code Review Report

**Reviewed:** 2026-06-03
**Depth:** standard (+ cross-file concurrency tracing for MoonrakerSession)
**Files Reviewed:** 18 (14 production+test + 2 docs + 2 tools; SpineHandle and MoonrakerService included for cadence-removal verification)
**Status:** issues_found

## Summary

Phase 13 lands three tightly-scoped reliability improvements: (1) visible, self-healing klippy-restart recovery with a per-drop AtomicReference watchdog (13-02), (2) the cadence audit and refreshProbeZOffset removal (13-03), and (3) OkHttp pingInterval keepalive + shell nav-state hoist + reconnect-splash routing (13-05). All three passed unit tests and a dual-printer on-device UAT on real hardware. The concurrency design for the headline fix (per-drop watchdog, idempotent escalation, skipIdentify re-handshake) is sound. No critical bugs, no security vulnerabilities, no data loss paths.

Two warnings are latent issues that don't affect the UAT-verified happy path but can bite in edge cases: a coroutine-scope lifetime mismatch that can delay reconnect by up to 30 seconds when a klippy drop is followed by a socket death, and a test that silently exercises a reconnect path instead of the intended same-socket path (the project's own "mock-vs-reality" anti-pattern). A third warning is a minor UI timing glitch in the min-dwell latch.

---

## Warnings

### WR-01: Recovery watchdog coroutine can block the Served path for up to RECOVERY_WINDOW (30 s)

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:275-283, 392-396`

**Issue:** The drop-recovery watchdog is launched with `attemptScope.launch { select { thisDrop.onAwait {}; onTimeout(RECOVERY_WINDOW) { escalateReconnect(...) } } }` where `attemptScope` is the `coroutineScope {}` block that backs `connectAndServe`. `routing.cancelAndJoin()` (line 393) cancels the three collector coroutines but does **not** cancel the watchdog — the watchdog is a sibling child of `attemptScope`, not a child of `routing`.

`coroutineScope {}` awaits **all** of its children before the block can return. So after a normal socket close (via the `closed.await()` at line 392), if a `notify_klippy_disconnected` had previously been received (arming a watchdog) but no `notify_klippy_ready` arrived before the socket died, `connectAndServe` will silently block up to 30 seconds at line 396 while the watchdog waits for its `onTimeout`. After the timeout, `escalateReconnect` fires against an already-closed `liveConnection` (a no-op), and the coroutine finally completes, allowing `connectAndServe` to return `ConnectAttempt.Served`.

The practical scenario: user issues FIRMWARE_RESTART (klippy drop), then immediately powers off the printer within the 30-second window before klippy_ready arrives. The supervisor loop does not retry for 30 seconds instead of the normal short backoff. This is a UX delay (not a crash), but it runs counter to the whole "reliable reconnect" goal and adds 30 seconds to the detection-to-retry loop in a legitimate failure mode (printer power-off mid-restart).

**Fix:** Track watchdog jobs explicitly and cancel them alongside routing, or launch the watchdog inside `routing` so `routing.cancelAndJoin()` naturally cancels it:

```kotlin
// Option A: cancel watchdog when the socket closes (track its job explicitly)
val watchdogJob = attemptScope.launch { select<Unit> { ... } }

// Then at teardown (before ConnectAttempt.Served):
closed.await()
watchdogJob.cancel()        // cancel the outstanding watchdog before awaiting routing
routing.cancelAndJoin()
collector.cancelAndJoin()
rpc.close(ConnectionError.NetworkUnavailable)
```

Alternatively, launch the watchdog as a child of the klippy-collector launch block (inside `routing`). Either approach ensures that a socket death during the recovery window doesn't leave a 30-second zombie coroutine blocking the Served return.

---

### WR-02: `ProbeZOffsetFreshnessTest` silently exercises the reconnect path, not the same-socket re-handshake path

**File:** `app/src/test/java/works/mees/dinghy/net/ProbeZOffsetFreshnessTest.kt:60-63`

**Issue:** The test uses `advanceUntilIdle()` between `harness.injectKlippyDrop()` and `harness.injectKlippyReady()`:

```kotlin
harness.injectKlippyDrop()
advanceUntilIdle()          // <-- advances virtual time past the 30-second RECOVERY_WINDOW
harness.injectKlippyReady()
advanceUntilIdle()
```

`advanceUntilIdle()` advances virtual time until all pending tasks complete. Because the drop arms a 30-second watchdog (`select { thisDrop.onAwait; onTimeout(30.seconds) { escalateReconnect } }`), `advanceUntilIdle()` advances past 30 seconds, fires `escalateReconnect`, closes the socket, and the supervisor reconnects on a **fresh** socket. Only then does `harness.injectKlippyReady()` fire — on socket #2, not socket #1.

The test's assertion (`store.probeZOffset.value == 1.475f`) still passes because the reconnect's full handshake reads the updated configfile. But the test comment says it exercises "the re-handshake whose configfile reply now carries the NEW value" implying the same-socket re-handshake path — it actually exercises a full reconnect. The same-socket path (notify_klippy_ready → `runHandshake(skipIdentify=true)` on the live socket) is not exercised at all in this test.

This is the same "mock-vs-reality" pattern the project documented from its own history: the test is GREEN and measures the correct outcome, but it doesn't exercise the intended mechanism. A future change that breaks the same-socket re-handshake configfile read (while leaving the reconnect path intact) would not be caught by this test.

`KlippyReadyResyncTest` uses `runCurrent()` (not `advanceUntilIdle()`) specifically to avoid this problem (documented in 13-01/13-02 SUMMARYs). `ProbeZOffsetFreshnessTest` should follow the same discipline.

**Fix:** Replace `advanceUntilIdle()` with `runCurrent()` between drop and ready (matching the discipline from `KlippyReadyResyncTest`):

```kotlin
harness.injectKlippyDrop()
runCurrent()               // drains tasks due NOW; leaves the 30s watchdog pending
harness.injectKlippyReady()
runCurrent()               // drains the re-handshake; session quiesces
withTimeout(5_000) { session.connectionState.first { it is ConnectionState.Connected } }
```

Remove the `advanceUntilIdle` import if it becomes unused after this change.

---

### WR-03: Min-dwell latch can briefly re-show Splash after Settings save during the dwell window

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt:66-119`

**Issue:** The `splashHeld` flag and `showSplash` computation interact with `settingsEscape` in a way that can produce a brief ghost Splash after a Settings save:

1. Recovery Splash appears → `rawSplash=true`, `splashHeld=true`.
2. Recovery completes in under 600ms → `rawSplash=false`. `LaunchedEffect(rawSplash)` starts `delay(600)`.
3. User taps "Edit connection" during the dwell → `settingsEscape=true`. The first `when{}` arm shows `SettingsScreen` (bypassing `showSplash`). The `delay(600)` is still running.
4. User saves immediately → `settingsEscape=false`. `rawRoute` is now `Shell`. First arm no longer matches.
5. `showSplash = rawSplash || splashHeld = false || true = true` → the second `when{}` arm shows `SplashScreen` briefly.
6. 600ms later, `splashHeld=false`, `showSplash=false`, `SplashScreen` disappears.

The ghost Splash is at most `SPLASH_MIN_DWELL_MS` (600ms) long. The path requires the user to tap "Edit connection" during a sub-600ms recovery and then save settings before the dwell expires — an extremely tight window in practice. However it is structurally incorrect: a healthy, Connected session briefly shows the Syncing Splash after Settings save.

**Fix:** Clear `splashHeld` when `settingsEscape` becomes true (i.e., when the user explicitly navigates away from the Splash to Settings, the dwell intent is fulfilled):

```kotlin
// In the LaunchedEffect or the when{} block, when settingsEscape transitions to true:
LaunchedEffect(settingsEscape) {
    if (settingsEscape) splashHeld = false  // user escaped; cancel any pending dwell
}
```

Alternatively, include `settingsEscape` in the `showSplash` guard:

```kotlin
val showSplash = (rawSplash || splashHeld) && !settingsEscape
```

The second form is simpler and ensures the active escape always wins over the dwell, consistent with the documented "Connect/Settings routes BYPASS the dwell entirely" intent.

---

## Info

### IN-01: `SocketEvent.Closed` cause is captured into `closed` but its value is never used

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:343-348, 395`

**Issue:** The collector stores the close cause:

```kotlin
is SocketEvent.Closed -> {
    if (!closed.isCompleted) closed.complete(event.cause)  // event.cause: ConnectionError?
}
```

But the Served path at line 395 ignores it entirely, always using `NetworkUnavailable`:

```kotlin
rpc.close(ConnectionError.NetworkUnavailable)  // cause from closed.await() discarded
```

A graceful `SocketEvent.Closed(null)` (from `onClosing`) is indistinguishable from `SocketEvent.Closed(NetworkUnavailable)` (from `onFailure`) at the `rpc.close()` call. This doesn't break behavior (the cause just controls whether OkHttp sends a close frame or hard-cancels, and OkHttp has already done one or the other), but `closed` carries a value that is never read.

**Fix:** Either use the cause or simplify to `CompletableDeferred<Unit>` since the value is unused:

```kotlin
// Use the cause:
rpc.close(closed.await() ?: ConnectionError.NetworkUnavailable)

// Or simplify the type if the distinction is genuinely irrelevant:
val closed = CompletableDeferred<Unit>()
```

---

### IN-02: `TopRouteTest` does not test arm-priority ordering when both klippy and socket are degraded

**File:** `app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt`

**Issue:** The arm-ordering comment in `derive()` and `TopRoute.kt` says the klippy gate (second arm) wins over the socket gate (third arm). The test suite covers: `!cfgPresent` wins over socket reconnect (`noConfig_winsOverSocketReconnect`), and each arm in isolation. But there is no test asserting that `klippyState != Ready` routes `Splash` even when `connection !is Connected` is also true — i.e., that the routing doesn't depend on arm ordering in the `!is Connected` case since both map to `Splash` anyway, the ordering is moot for correctness. However the documented priority ("klippy gate first") is not pinned by a test that would catch an arm-swap.

**Fix:** Add a test case:

```kotlin
@Test
fun klippyNotReady_winsOverSocketReconnect_armOrderIsPreserved() {
    // Both klippy-not-Ready AND socket-not-Connected should route Splash.
    // If arms were swapped, still Splash — but this pins the *reason*.
    assertEquals(
        TopRoute.Splash,
        derive(
            cfgPresent = true,
            s = PrinterState(
                klippyState = KlippyState.Shutdown,
                connection = ConnectionState.Connecting, // also not Connected
            ),
        ),
    )
}
```

Low priority since both arms map to the same destination.

---

### IN-03: `ws-capture.py` `t0_relative_deadline` helper applied inconsistently

**File:** `tools/ws-capture.py:308, 326, 341, 350`

**Issue:** The `t0_relative_deadline` helper (defined as a module-level function bound to `Capture` at line 373) is used at line 308 but three other `reader_loop` calls (lines 326, 341, 350) inline the equivalent `(time.monotonic() - cap.t0) + N` arithmetic directly. The inconsistency is harmless and confined to this capture-only tool (never bundled in the APK), but makes the helper appear to be dead code to a reader who scans the helper definition without seeing line 308.

**Fix:** Apply `t0_relative_deadline` uniformly to all four `reader_loop` calls, or remove the helper and inline consistently:

```python
# Uniform helper use:
await cap.reader_loop(deadline=cap.t0_relative_deadline(5))
await cap.reader_loop(deadline=cap.t0_relative_deadline(restart_wait), on_signal=on_signal)
await cap.reader_loop(deadline=cap.t0_relative_deadline(15), on_signal=on_signal)
await cap.reader_loop(deadline=cap.t0_relative_deadline(post_wait))
```

---

_Reviewed: 2026-06-03_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard + cross-file concurrency tracing_
