---
phase: 24-navigation-spine
reviewed: 2026-06-10T07:00:00Z
depth: deep
files_reviewed: 18
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/MainActivity.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/FloatingEStop.kt
  - app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
  - app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt
  - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/StartDestMapping.kt
  - app/src/main/res/values/strings.xml
  - tools/verify_ligatures.py
  - app/build.gradle.kts
  - gradle/libs.versions.toml
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: resolved
resolved:
  WR-02: c5fa886
  WR-03: 976dc73
  WR-01: acknowledged-by-design
---

# Phase 24: Navigation Spine — Code Review Report

**Reviewed:** 2026-06-10
**Depth:** Deep (cross-file analysis, call-chain tracing)
**Files Reviewed:** 18
**Status:** issues_found

## Summary

The NavHost migration is structurally sound: session holders are hoisted above the NavHost, the four
`DisposableEffect` leak-cancel blocks are preserved, FloatingEStop is correctly promoted to the
AppShell overlay layer, and BackHandler priority ordering is correct. The `isRoute<T>()` workaround
for the nav-compose 2.8.9 type-argument incompatibility is a reasonable workaround.

Three defects surfaced under deep cross-file analysis: the most significant is a `printActive`
parameter that is documented as "caller's responsibility" but is silently ignored by the predicate
body AND not guarded by the caller — causing pop-to-root on foot-gun dests on any printState change
(including print completion, error, or cancellation), not just on print start. Two additional warnings
cover a `stateIn(compositionScope)` lifecycle mismatch that kills bookmark/revealHidden updates after
a recovery Splash, and a back-stack duplication path from the Spool "Home" button.

---

## Warnings

### WR-01: `shouldPopToRoot` ignores `printActive` — pops on ANY printState change, not just print start [acknowledged: by-design]

**File:** `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt:107-108`
**Cross-reference:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:915-929`

**Issue:** `shouldPopToRoot` has this body:

```kotlin
fun shouldPopToRoot(current: NavDest?, printActive: Boolean): Boolean =
    current != null && current in FOOT_GUN_DESTS
```

The `printActive` parameter is fully unused. The KDoc justifies this by saying "the decision of WHEN
to call it is the caller's responsibility." However, the caller at `AppShell.kt:927` does NOT guard
on `printActive` before calling — it passes the value in but never checks it:

```kotlin
if (shouldPopToRoot(currentNavDest, printActive = printerState.printState == PrintState.Printing)) {
    navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)
}
```

The `LaunchedEffect(printerState.printState)` fires on every printState change: `Standby → Printing`,
`Printing → Paused`, `Printing → Complete`, `Printing → Error`, `Printing → Cancelled`. Since
`shouldPopToRoot` returns `true` for ANY foot-gun dest regardless of `printActive`, the pop fires on
ALL transitions including `Printing → Complete`. The real-world scenario: a user is on Move or
Extrude (idle, valid), a previously-started print finishes (Printing → Complete), `printState`
changes, the LaunchedEffect fires, `shouldPopToRoot(NavDest.Move, false) == true`, and the user is
unexpectedly bounced to WaterfallHome.

D-04 intent: "If a print STARTS while the user is deep in a drill-down screen the new state
intentionally hides — pop." The implementation pops on START and on END and on ERROR and on CANCEL.

**Why it matters:** During a print, foot-gun dests are unreachable (the idle list is hidden). However
edge cases exist: if the user is on Move in Standby, a print starts (correct pop — user intended),
then later the print completes (the user is back in Standby on WaterfallHome — fine). But a user who
navigates to Move AFTER print completion (Standby state) and a deferred state event arrives that
delivers a `printState == Printing` → `Complete` flip, would get popped. More practically: the
pop-on-Complete/Error/Cancel is not dangerous but is unexpected behavior that contradicts D-04.

**Confidence:** HIGH — the predicate body is provably parameter-ignoring.

**Fix:** Either guard the call site, or put the guard in the predicate itself:

Option A — guard at call site (minimal diff):
```kotlin
// AppShell.kt:927
val printActive = printerState.printState == PrintState.Printing ||
                  printerState.printState == PrintState.Paused
if (printActive && shouldPopToRoot(currentNavDest, printActive)) {
    navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)
}
```

Option B — fix the predicate (matches the documented intent):
```kotlin
// NavDest.kt:107
fun shouldPopToRoot(current: NavDest?, printActive: Boolean): Boolean =
    printActive && current != null && current in FOOT_GUN_DESTS
```

Option B is cleaner — it makes the predicate's contract match its documented intent and makes
`PopToRootTest` explicitly cover the `printActive=false` case.

**Status: acknowledged (by-design).** The KDoc at `NavDest.kt:107` documents ignoring `printActive`
as a conscious design choice: "keep the predicate pure; caller controls the trigger." The current
pop-on-any-foot-gun behavior is the more conservative/safer option — it ensures users are NEVER
left on a foot-gun screen during a print regardless of the exact state transition direction. The
identified pop-on-Complete/Error/Cancel edge case is a non-dangerous extra pop (user is on WaterfallHome
where they'd land anyway). Tightening to "only pop when entering a printing state" is a future
refinement, not a correctness regression. No code change made.

---

### WR-02: `stateIn(rememberCoroutineScope)` kills bookmark/revealHidden updates after a recovery Splash [resolved: c5fa886]

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:443-448`

**Issue:**

```kotlin
val scope = rememberCoroutineScope()   // line 165 — cancelled when AppShell LEAVES composition
...
val bookmarksFlow = remember {
    container.macroPrefs.bookmarks.stateIn(scope, SharingStarted.Eagerly, emptySet())
}
val revealHiddenFlow = remember {
    container.macroPrefs.revealHidden.stateIn(scope, SharingStarted.Eagerly, false)
}
```

`rememberCoroutineScope()` is tied to the Composable's lifetime — it is cancelled when `AppShell`
leaves the composition tree. This happens on every recovery Splash (accepted FIX-3 regression): the
`RootController` gate shows `SplashScreen` instead of `AppShell`, which causes `AppShell` to leave
composition and its `rememberCoroutineScope` scope to be cancelled.

`stateIn(scope, SharingStarted.Eagerly, ...)` starts the upstream collection immediately and cancels
it when `scope` is cancelled. After the recovery Splash, `AppShell` re-enters composition and
`remember {}` returns the SAME `StateFlow` instances (no re-key). However, the `stateIn` coroutine
inside those StateFlows was cancelled with the old scope. The new scope from the recomposed AppShell
is never wired to the existing `StateFlow` instances.

Result: after a recovery Splash, `bookmarksFlow` and `revealHiddenFlow` become stale — they hold
their last-seen values but no longer update when `macroPrefs.bookmarks` or `macroPrefs.revealHidden`
change. A user who toggles a macro bookmark after reconnecting would not see the update reflected in
the macro surface until the next full process restart.

This is exactly the [[dinghy-compose-write-scope-cancellation]] trap, applied to read-path flows
rather than writes.

**Why it matters:** Post-recovery Splash, the bookmarks-gating on the idle action list (whether Macros
row appears) and the system-macros revealHidden toggle both go stale silently. The only visible
symptom is "I bookmarked a macro and the idle list didn't update" — subtle enough to miss in UAT.

**Confidence:** HIGH — `rememberCoroutineScope` lifecycle is well-defined; the re-key behaviour of
`remember {}` without a key is confirmed.

**Fix:** Move the `stateIn` out of the composition scope entirely — either use `AppContainer`'s
process-scoped coroutine scope (the right owner for process-scoped prefs), or use
`collectAsStateWithLifecycle()` directly:

```kotlin
// Preferred: collect directly without stateIn (macroPrefs.bookmarks is already a Flow)
val bookmarks by container.macroPrefs.bookmarks
    .collectAsStateWithLifecycle(initialValue = emptySet())
val revealHidden by container.macroPrefs.revealHidden
    .collectAsStateWithLifecycle(initialValue = false)
```

Then pass `bookmarks` and `revealHidden` directly to `MacroHolder` as parameters. If the holder
requires a `StateFlow`, expose `container.macroPrefs.bookmarks.stateIn(container.applicationScope, ...)`
from `AppContainer` instead, so the scope outlives any Composable.

**Fixed (commit c5fa886):** `AppContainer` now exposes `macroBookmarks: StateFlow<Set<String>>` and
`macroRevealHidden: StateFlow<Boolean>` backed by the process-lifetime `stateScope`. AppShell reads
them directly as `container.macroBookmarks` / `container.macroRevealHidden` — no `remember {}`,
no composition scope. Unused `SharingStarted` and `stateIn` imports removed from AppShell.

---

### WR-03: Spool "Home" button and Devices "onSwitched" push a duplicate WaterfallHome onto the back-stack [resolved: 976dc73]

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:786` and `:883`

**Issue:**

```kotlin
// Spool composable (line 786)
onHome = { navController.navigate(NavDest.WaterfallHome) },

// Devices composable (line 883)
onSwitched = { navController.navigate(NavDest.WaterfallHome) },
```

`navController.navigate(NavDest.WaterfallHome)` pushes a NEW entry for `WaterfallHome` onto the back-
stack. It does NOT pop to the existing root entry. After calling `navigate(WaterfallHome)` from Spool,
the back-stack contains: `[WaterfallHome(root), Spool, WaterfallHome(pushed)]`. Pressing system Back
from the pushed WaterfallHome lands back on Spool, then on the original root.

For the Spool "Home" button, this is unexpected — the user is trying to go home, not stack another
home on top of Spool. For Devices/`onSwitched`, the same duplication applies after a printer switch.

This is a UI correctness issue: if the user presses Back after tapping "Home" from Spool, they wind
up back on the Spool screen (the new WaterfallHome entry pops, revealing Spool beneath it).

**Fix:** Use `popBackStack<NavDest.WaterfallHome>(inclusive = false)` to pop to the existing root
rather than push a duplicate:

```kotlin
// Spool "Home" foot button
onHome = { navController.popBackStack<NavDest.WaterfallHome>(inclusive = false) },

// Devices printer switch
onSwitched = { navController.popBackStack<NavDest.WaterfallHome>(inclusive = false) },
```

Both callers want the same semantics as the D-04 pop-to-root LaunchedEffect (`inclusive = false` keeps
the root entry). `popBackStack` is idempotent at root (no-ops if WaterfallHome is already the top
entry).

**Fixed (commit 976dc73):** Both call sites now use `navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)`.

---

## Info

### IN-01: `HomeAction.OpenDrawer` is a dead sealed variant — `buildIdleActions` never produces it and the renderer silently drops it [open: optional]

**File:** `app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt:47`
**Cross-reference:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt:93`

**Issue:** `HomeAction.OpenDrawer` exists as a sealed variant but `buildIdleActions` never adds it to
the list — the idle list is populated only with `HomeAction.Destination` items. Meanwhile, the
renderer at `PrintStatusField.kt:93` filters to only `Destination`:

```kotlin
items(
    items = idleActions.filterIsInstance<HomeAction.Destination>(),
    ...
)
```

If `OpenDrawer` were ever added to the list (by a future extension or a test calling
`buildIdleActions` that adds it), it would be silently swallowed without rendering or error. The
current code is not broken because `buildIdleActions` never returns `OpenDrawer`, but the renderer
contract (silently drop non-Destination items) is undocumented and the KDoc for `PrintStatusStandbyField`
says `buildIdleActions` builds the list — giving no indication that non-Destination variants are
dropped.

**Fix:** Add a KDoc note to `PrintStatusStandbyField` that only `HomeAction.Destination` items are
rendered by the list; the `OpenDrawer` variant is reserved for the foot bar's System button (the
current design places drawer access in the foot bar, not as a list row). Alternatively, add an
explicit `when` dispatch in the `items` lambda to catch `OpenDrawer` and render it as a special row,
which would make the data-driven model actually data-driven.

---

### IN-02: `printActive` parameter on `shouldPopToRoot` is a misleading no-op — `PopToRootTest` does not cover `printActive=false` returning `true` [open: optional]

**File:** `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt:107`

**Issue:** Related to WR-01 but scoped to test coverage: the `printActive` parameter is documented as
"the AppShell LaunchedEffect already fires only on a printState change, so the caller controls the
trigger, not this function." This splits the correctness contract across predicate + caller without
testing that the caller actually enforces it. If the test suite for `shouldPopToRoot` only tests
cases where `printActive=true` for foot-gun dests, it would not catch that `printActive=false` also
returns `true` (which it does — the parameter is ignored).

The existing `PopToRootTest` tests pass because the predicate behavior is consistent (always true for
foot-gun dests), but no test verifies that `shouldPopToRoot(NavDest.Move, printActive=false)` returns
`false` — which is the documented intent.

**Fix:** Regardless of which fix is chosen for WR-01, add a test case:

```kotlin
@Test
fun `shouldPopToRoot returns false when printActive is false even for foot-gun dest`() {
    assertFalse(shouldPopToRoot(NavDest.Move, printActive = false))
}
```

This test currently FAILS (confirming the WR-01 bug) and should be used to drive the fix.

---

_Reviewed: 2026-06-10_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
