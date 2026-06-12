---
phase: 20-system-information-page
reviewed: 2026-06-08T00:00:00Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
  - app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt
  - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
  - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
  - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
  - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
  - app/src/main/java/works/mees/dinghy/systeminfo/HealthChip.kt
  - app/src/main/java/works/mees/dinghy/systeminfo/SysInfoFormat.kt
  - app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoHolder.kt
  - app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoModels.kt
  - app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoParse.kt
  - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt
  - app/src/main/java/works/mees/dinghy/preview/SystemInfoPreviews.kt
  - app/src/main/res/values/strings.xml
  - app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt
  - app/src/test/java/works/mees/dinghy/spool/SpoolmanNotifyRouterTest.kt
  - app/src/test/java/works/mees/dinghy/systeminfo/DegradeTest.kt
  - app/src/test/java/works/mees/dinghy/systeminfo/HealthChipTest.kt
  - app/src/test/java/works/mees/dinghy/systeminfo/ProcStatPushTest.kt
  - app/src/test/java/works/mees/dinghy/systeminfo/ProcStatRouteTest.kt
  - app/src/test/java/works/mees/dinghy/systeminfo/SysInfoFormatTest.kt
  - app/src/test/java/works/mees/dinghy/systeminfo/SystemInfoParseTest.kt
  - docs/commands/catalog.json
  - docs/commands/printer-matrix.json
  - tools/verify_ligatures.py
findings:
  critical: 0
  warning: 2
  info: 4
  total: 6
status: issues_found
---

# Phase 20: Code Review Report

**Reviewed:** 2026-06-08
**Depth:** standard
**Files Reviewed:** 27
**Status:** issues_found

## Summary

Phase 20 adds a read-only System Information page (host CPU/RAM/distro/kernel + 1 Hz live
telemetry + health chip) for the SBC running Klipper/Moonraker. I reviewed the tolerant JSON-RPC
parsers, the off-hot-path `SystemInfoHolder`, the proc-stat push routing in `JsonRpcClient`, the
Compose screen, and the command-catalog drift surfaces (registry ↔ catalog.json ↔
printer-matrix.json).

The work is solid and the highest-risk areas are well-defended:

- **Drift gate is consistent.** Both new specs (`MR-machine.system_info`, `MR-machine.proc_stats`)
  exist in `CommandRegistry.all`, in `catalog.json` with `runtime_registry.registered: true`, AND
  in `printer-matrix.json` `command_availability` (the exact triple `CommandCatalogDriftTest`
  enforces — the `[[dinghy-command-catalog-drift]]` trap was NOT tripped). Both are `Always`
  availability, so no predicate/matrix-evidence gap.
- **Parsers degrade, never throw.** Every walk is `runCatching`-guarded with safe casts; empty
  string → null (RockPro64 blank model), JsonNull `throttled_state` → null, missing keys → all-null
  model. `DegradeTest`/`SystemInfoParseTest`/`ProcStatPushTest` pin the real cross-SBC fixtures.
- **Push routing has no regression.** `notify_proc_stat_update` now routes to `procStatUpdates` AND
  the `SpoolmanNotifyRouterTest` golden re-asserts those 10 frames never reach `activeSpoolSet`.
- **Handshake order test, ligature gate, strings, icons, and drawer/route atomicity** are all
  updated in lockstep — no dead-tap window, no tofu glyph, no unrouted literal.

No blockers. Two warnings (a per-reconnect collector leak in the new holder, and an over-broad
parse-failure in the throttle-flags walk) and four info items below.

## Warnings

### WR-01: `SystemInfoHolder` leaks its push collector on every session rebuild

**File:** `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoHolder.kt:50-56`,
`app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt:240-246`

**Issue:** The holder's `init { scope.launch { procStatUpdates.collect { _live.value = ... } } }`
runs on the service-owned `serviceScope` (process-lifetime), but the holder is rebuilt on **every**
config emission / reconnect inside `buildSpineAndLaunch`. `runConfigLoop` `cancelAndJoin()`s only the
prior `session.run()` job — it does NOT cancel the prior holder's collect coroutine, and the holder
exposes no `cancel()`. Each profile switch / reconnect therefore strands a live collector on
`serviceScope`; they accumulate until `onDestroy()` (`serviceScope.cancel()`).

The leak is mostly *idle* (the old collector reads the old `JsonRpcClient`'s `procStatUpdates`, which
never emits again after its session dies), so it is not a correctness/data bug — but it is an
unbounded coroutine leak that compounds per reconnect, exactly the class of bug the codebase fixes
elsewhere with explicit `cancel()` + `DisposableEffect` (see `ConsoleHolder`/`MacroHolder`/
`WebcamHolder` WR-01 leak-cancels in `AppShell.kt`).

Note: `WebcamsHolder`, `ActiveSpoolFacade`, `PrintMetadataHolder`, and `LastJobHolder` follow this
same serviceScope-launch-without-cancel pattern in `buildSpineAndLaunch`, so this is a pre-existing
service-layer pattern rather than a Phase-20-only regression — but Phase 20 adds one more leaking
collector to it.

**Fix:** Give the holder a cancel handle and tie it to the session lifecycle, e.g.

```kotlin
class SystemInfoHolder(
    scope: CoroutineScope,
    spine: SpineHandle,
    procStatUpdates: SharedFlow<JsonObject>,
) {
    private val job = scope.launch {
        procStatUpdates.collect { _live.value = ProcStatLive.fromPush(it) }
    }
    fun cancel() { job.cancel() }
    // ...
}
```

and either cancel the prior holder before `publishSystemInfoHolder(new)` in the service, or launch
the collect on a per-session child scope cancelled with the session job. (A broader fix would lift
all five serviceScope holders onto a per-session child scope, but at minimum the new holder should
not add to the leak set without a cancel.)

### WR-02: A non-primitive `flags` element discards the entire `proc_stats` query

**File:** `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoParse.kt:110-119`

**Issue:** `throttledStateOrNull()` maps `flags` with
`?.mapNotNull { it.jsonPrimitive.contentOrNull }`. `JsonElement.jsonPrimitive` **throws**
`IllegalArgumentException` when the element is not a primitive (e.g. a nested object/array). Because
`ProcStatQuery.from` wraps the whole build in `runCatching { ... }.getOrDefault(ProcStatQuery())`,
one malformed `flags` entry doesn't just drop that flag — it collapses the **entire** query result
to the empty default, silently losing `cpuTemp` and `system_uptime` too (and forcing the health chip
onto its temp-fallback / healthy default).

This is inconsistent with the project's own collection-level tolerance rule applied right next door
in `MoonrakerSession.parseObjectsList` ("guard PER ELEMENT (mapNotNull) ... one non-string entry
must be skipped, NOT collapse the entire list"), and with the per-key `as? JsonPrimitive` discipline
the rest of this very file uses.

**Fix:** Use a safe per-element cast so a bad flag is skipped, not fatal:

```kotlin
val flags = (obj["flags"] as? JsonArray)
    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
    ?: emptyList()
```

## Info

### IN-01: Idle path allocates a fresh `MutableStateFlow` per recomposition

**File:** `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt:87-102`

**Issue:** `holder?.identity ?: nullStateFlow()` is evaluated on every recomposition of
`SystemInformationScreen`; when `holder` is null it builds a brand-new `MutableStateFlow(null)` each
pass (three of them per recompose), which `collectAsStateWithLifecycle` then re-subscribes to. The
KDoc claims "no allocation per row," which isn't accurate. It is harmless (the flow only ever emits
null) but wasteful churn on the idle/no-session path.

**Fix:** Hoist the fallbacks behind `remember`, e.g.
`val idle = remember { MutableStateFlow<Nothing?>(null) }` and reuse it for all three, or branch the
whole content on `holder == null` and pass plain nulls to `SystemInformationContent`.

### IN-02: System-info models are not `@Immutable`/`@Stable` for Compose

**File:** `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoModels.kt:24-79`

**Issue:** `ProcStatQuery` carries a `ThrottledState` whose `flags: List<String>` makes the type
Compose-**unstable** (lists are unstable by default), and none of `SystemInfo`/`ProcStatLive`/
`ProcStatQuery` are annotated `@Immutable`/`@Stable`. CLAUDE.md explicitly calls for socket-driven
state objects to be `@Stable`/`@Immutable` so Compose can skip. The page fully recomposes at 1 Hz
regardless, so this is not a measured perf problem on the Adreno floor today, but it forfeits the
skip the stack convention asks for.

**Fix:** Annotate the three data classes `@Immutable` (and either annotate `ThrottledState`
`@Immutable` accepting the list, or model `flags` as `ImmutableList` per the
`kotlinx-collections-immutable` note in the stack).

### IN-03: No holder-level test for `SystemInfoHolder`

**File:** `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoHolder.kt`

**Issue:** The holder's two behaviors — forwarding `spine.systemInfo`/`spine.procStatQuery`
unchanged, and mapping `procStatUpdates` pushes into `_live` via `ProcStatLive.fromPush` — have no
direct unit test. `ProcStatRouteTest` covers the `JsonRpcClient` flow and `ProcStatPushTest` covers
the parser, but the holder's wiring (and the WR-01 lifecycle concern) is only exercised indirectly.

**Fix:** Add a small `runTest` that constructs `SystemInfoHolder` with a `TestScope`, emits a frame
on a `MutableSharedFlow`, and asserts `holder.live.value` reflects `fromPush`; assert
`identity`/`procStats` pass through the supplied `SpineHandle` flows.

### IN-04: `distroValue` version-suppression uses a substring test

**File:** `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt:241-245`

**Issue:** `if (version != null && version !in name)` suppresses the version when it already appears
**anywhere** in the name string. This is correct for the real fixtures (`"25.11.2" in "Armbian
25.11.2 noble"`, `"12" in "Debian ... 12 (bookworm)"`), but a short numeric version (e.g. `"1"` or
`"12"`) that coincidentally occurs in an unrelated part of the distro name would be dropped from the
display even when it carries distinct information. Low likelihood with real Moonraker payloads.

**Fix:** Prefer a token/word-boundary check or simply append unconditionally when the name doesn't
*end with* the version, e.g. `if (version != null && !name.contains(Regex("\\b${Regex.escape(version)}\\b")))`.

---

_Reviewed: 2026-06-08_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
