---
phase: 04-service-shell-settings-print-status-home
fixed_at: 2026-05-31T00:00:00Z
review_path: .planning/phases/04-service-shell-settings-print-status-home/04-REVIEW.md
iteration: 1
findings_in_scope: 6
fixed: 6
skipped: 0
status: all_fixed
---

# Phase 4: Code Review Fix Report

**Fixed at:** 2026-05-31
**Source review:** `.planning/phases/04-service-shell-settings-print-status-home/04-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 6
- Fixed: 6
- Skipped: 0

## Fixed Issues

### WR-03: MoonrakerDiscovery SERVICE_TYPE missing trailing dot

**Files modified:** `app/src/main/java/works/mees/dinghy/config/MoonrakerDiscovery.kt`
**Commit:** `cf5a24b`
**Applied fix:** Changed `SERVICE_TYPE` from `"_moonraker._tcp"` to `"_moonraker._tcp."` (DNS-SD fully-qualified form with trailing dot). Verified no comparison logic anywhere references the bare string — the constant is used only in `discoverServices()`. KDoc comments mention the un-dotted form but those describe the advertised service, not NsdManager's required format, so left as-is.

---

### WR-01: failureText in PrintStatusScreen never cleared

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`
**Commit:** `e78be48`
**Applied fix:** Two changes to `PrintStatusScreen`:
1. Added `failureText = null` reset at the top of `LaunchedEffect(dispatcher)` so stale failures from a prior session are cleared when the dispatcher key changes.
2. Added `LaunchedEffect(failureText)` that delays 4 000 ms then nulls the text, auto-dismissing the error toast.
Added `import kotlinx.coroutines.delay` (was not previously imported in this file).

---

### WR-02: CommandDispatcher.lastAccepted not thread-safe

**Files modified:** `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt`
**Commit:** `352bc9d`
**Applied fix:** Replaced `mutableMapOf<String, Long>()` (plain `LinkedHashMap`) with `java.util.concurrent.ConcurrentHashMap<String, Long>()`. All call-site usage (`lastAccepted[key]`, `lastAccepted[key] = now`) is interface-compatible — no other changes required. All `CommandDispatcherTest` tests remain green. Updated the field KDoc to explain the thread-safety rationale.

---

### IN-01: fmt() truncates instead of rounding

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`
**Commit:** `bdf35ab`
**Applied fix:** Changed `(v * 10).toInt()` to `(v * 10).roundToInt()` in `fmt()`, matching the identical pattern already used in `ScrubberPage.kt`. Added `import kotlin.math.roundToInt`. A nozzle at 249.97 °C now correctly displays as "250.0°" instead of "249.9°".

---

### IN-02: Multi-extruder grid shows duplicate "NOZZLE" labels

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`
**Commit:** `c0fcf17`
**Applied fix:** Split the single `objectName == "extruder" || objectName.startsWith("extruder") -> "NOZZLE"` branch into two ordered branches:
- `objectName == "extruder" -> "NOZZLE"` (exact match for the primary extruder)
- `objectName.startsWith("extruder") -> "NOZZLE ${objectName.removePrefix("extruder")}"` (numbered extruders: `extruder1` → `"NOZZLE 1"`, `extruder2` → `"NOZZLE 2"`, etc.)

The exact-match branch is checked first so `"extruder"` → `"NOZZLE"` without appending an empty string. Updated KDoc with the full mapping table.

---

### IN-03: SessionControl KDoc typo "a the"

**Files modified:** `app/src/main/java/works/mees/dinghy/di/SessionControl.kt`
**Commit:** `f9a8175`
**Applied fix:** Removed the stray `a` from "returns a the raw session type" → "returns the raw session type". Also cleaned up the awkward line break that split "returns" from the next line.

---

## Build Result

`:app:compileDebugKotlin` + `:app:testDebugUnitTest` — **BUILD SUCCESSFUL** (34 tasks, 6 executed).
Two pre-existing deprecation warnings on `MoonrakerDiscovery.kt` (NSD API) — not introduced by these fixes.

---

_Fixed: 2026-05-31_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
