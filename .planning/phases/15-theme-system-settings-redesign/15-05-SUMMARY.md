---
phase: 15-theme-system-settings-redesign
plan: 05
subsystem: theme
tags: [theme-persistence, theme-tuple, per-entry-sanitize, fail-safe, wr-02, fresh-start, deprecated-shims, wave-4]
requires:
  - "15-04 (ThemeResolver generate-and-cache: no-arg ctor + seed/mode/shift/override API + single-re-emit tuple apply(); DEFAULT_SEED_HEX/DEFAULT_POOL_MAX_ITEMS)"
  - "15-02/15-03 (Palette.generate + TokenBridge.build — the resolver's compute backbone, untouched here)"
provides:
  - "Profile/PersistedProfile carry the theme TUPLE (seedHex, dark, paletteMode, poolShift, maxItems, poolOverrides: Map<String,Long>); fsChoice retained separately; themeBase/themeDeltaArgb kept as @Deprecated decoded-but-unused fields (deleted in 15-06)"
  - "ThemePrefs tuple substrate: tupleFlow global fallback + setSeed/setDark/setMode/setShift/setMaxItems/setOverrides + a PURE per-entry never-throws sanitizeTuple + TUPLE_DEFAULT + DEFAULT_SEED/MODE/SHIFT/MAX_ITEMS consts"
  - "Profile.toThemeTuple() (the new resolve seam); the legacy toThemeResolved()/ThemePrefs.Resolved/sanitize/setBase/setDeltas all @Deprecated-but-retained so SettingsScreen + ProfileStoreTest/ActiveConfigDerivationTest compile at this wave"
  - "AppContainer.seedTheme rewired: single-apply of the PERSISTED tuple + a REACTIVE no-active branch (WR-02, themePrefs.tupleFlow via flatMapLatest, no firstOrNull); durable theme-edit intents setActiveSeed/Mode/Shift/Dark/Override/resetActiveTheme for the 15-06 editor"
affects:
  - "plan 15-06 (rebuilds SettingsScreen onto the tuple intents + setActive*; hard-deletes themeBase/themeDeltaArgb + the @Deprecated ThemePrefs.Resolved/sanitize/setBase/setDeltas + Profile.toThemeResolved; re-points GalleryScreen)"
tech-stack:
  added: []
  patterns:
    - "Per-entry fail-safe sanitize: a String-keyed poolOverrides map (poolIndex AS STRING → unsigned-32 ARGB Long) + a per-entry parse drops ONE malformed slot in isolation while good entries survive — because kotlinx ignoreUnknownKeys covers unknown KEYS, not malformed VALUES (an Int-keyed/typed map would fail the whole decode on a single bad entry, CONFIRMED Codex finding)"
    - "Fresh-start, no-migration decode (D-05): old themeBase/themeDeltaArgb decode onto @Deprecated ignored fields; the NEW tuple fields default — old persisted theme values are NOT carried forward into the runtime tuple"
    - "Reactive idle fallback (WR-02): the no-active-profile seedTheme branch collects themePrefs.tupleFlow via flatMapLatest (not a one-shot firstOrNull) so a global-default edit while idle re-emits and re-themes live"
    - "Two-step API retirement continued: the legacy ThemePrefs.Resolved/sanitize/setBase/setDeltas + Profile.toThemeResolved are @Deprecated-but-behavior-identical shims (NOT removed) — Gradle compiles the WHOLE main+test sourceset before any --tests, so a hard delete here would brick SettingsScreen + two profile tests; deletion is 15-06's job"
    - "Durable theme writes (T-15-05-04): every editor intent routes through the process-lifetime writeScope + an atomic mutateActiveProfile read-modify-write (active) or themePrefs (idle), NEVER a composition rememberCoroutineScope() ([[dinghy-compose-write-scope-cancellation]])"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/config/Profile.kt
    - app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt
    - app/src/test/java/works/mees/dinghy/theme/TokenDeltaSerializationTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemePrefsFallbackTest.kt
decisions:
  - "ADDITIVE, not replace-in-place. The plan's Task 1/2 language ('REPLACE the persisted keys + Resolved with the tuple', 'sanitize's SIGNATURE changes') would have hard-removed ThemePrefs.Resolved/sanitize/setBase/setDeltas AND Profile.toThemeResolved — but SettingsScreen (lines 145-149, 377-379, 529/543/560) + ProfileStoreTest + ActiveConfigDerivationTest + the still-to-be-rebuilt screen all read those at THIS wave. Gradle compiles the whole main+test sourceset before any --tests filter, so removing them would brick compilation (the plan's own #1 load-bearing must-have is 'both sourcesets compile at this wave boundary'). Resolution: ADD the tuple (ThemeTuple/sanitizeTuple/tupleFlow/setSeed.../toThemeTuple) ALONGSIDE the @Deprecated-retained legacy shims. Net effect is identical to the plan's intent — the tuple is the live source of truth, the legacy path is dead-but-compiling — and 15-06 deletes the shims exactly as the plan's affects: line says. This is the SAME two-step-retirement pattern 15-04 already established for the resolver."
  - "Resolver overrides bridged Long→Color at the apply seam. ThemeResolver.apply takes Map<Int,Color> (15-04); the persisted/sanitized tuple carries Map<Int,Long> (unsigned-32 ARGB). seedTheme maps `Color(it.value.toInt())` at the single apply call — the persistence layer stays Long (serializable, mask-validated), the resolver stays Color (its render input)."
  - "fsChoice stays a SEPARATE field on both Profile forms (D-05/THEME-02 refinement) — NOT folded into the tuple. resetActiveTheme deliberately does NOT reset fsChoice (text size is an independent accessibility setting)."
  - "themeBase/themeDeltaArgb @Deprecated but DEFAULTED ('Dark'/empty) so old AND new blobs decode cleanly and every existing call-site (Profile(..., themeBase=...) in the two tests; SettingsScreen create/copy) still compiles."
metrics:
  duration: 14 min
  completed: 2026-06-05
---

# Phase 15 Plan 05: Per-Profile Theme Tuple Persistence Summary

Reworked per-profile theme persistence to carry the full generate-and-cache TUPLE `(seedHex, dark, paletteMode, poolShift, maxItems, poolOverrides)` — reusing Phase-14's `mutateActive`/`writeScope` durable write path — with a **never-throws PER-ENTRY fail-safe sanitize** (a String-keyed `poolOverrides` map so one malformed slot drops in isolation), a **fresh-start no-migration decode** (old `themeBase`/`themeDeltaArgb` decode onto `@Deprecated` ignored fields; new tuple fields default), and the **WR-02 reactive no-active-branch fix** in `AppContainer.seedTheme`. The retired legacy fields + the old `ThemePrefs.Resolved`/`sanitize`/`setBase`/`setDeltas`/`Profile.toThemeResolved` are kept as `@Deprecated`-but-compiling shims so the still-standing `SettingsScreen` + `ProfileStoreTest`/`ActiveConfigDerivationTest` build at this wave; 15-06 deletes them when the editor is rebuilt. `fsChoice` (S/M/L) stays a separate, unchanged setting.

## What Was Built

**Task 1 — theme tuple on Profile/ThemePrefs + per-entry never-throws sanitize (`8e17d01`)**

- `config/Profile.kt`:
  - `PersistedProfile` + `Profile` both gained the tuple primitives `seedHex: String = "#3f78ff"`, `dark: Boolean = true`, `paletteMode: String = "Colorful"`, `poolShift: Int = 0`, `maxItems: Int = 4`, **`poolOverrides: Map<String, Long> = emptyMap()`** (String-keyed ON PURPOSE — per-entry tolerance). `fsChoice` retained.
  - `themeBase`/`themeDeltaArgb` now `@Deprecated(... deleted in 15-06)` decoded-but-unused fields (still DEFAULTED so old + new blobs decode and every call-site compiles).
  - `toThemeTuple()` is the NEW resolve seam → `ThemePrefs.sanitizeTuple`. The old `toThemeResolved()` is `@Deprecated`-retained (SettingsScreen line 145 still reads it).
  - `toString` redaction preserved (`apiKey=***`); the new theme fields carry no secrets, so the redaction surface was NOT widened (V7). `toConnectionConfig()` stays host/port/apiKey-ONLY — theme excluded from the spine.
  - `toPersisted`/`fromPersisted` carry the full tuple + pass the deprecated fields through.
- `theme/ThemePrefs.kt`:
  - New `data class ThemeTuple(seedHex, dark, paletteMode, poolShift, maxItems, poolOverrides: Map<Int,Long>, fs)` + `TUPLE_DEFAULT` (`#3f78ff`/dark/Colorful/0/4/empty/M) + `DEFAULT_SEED/MODE/SHIFT/MAX_ITEMS` consts.
  - `tupleFlow`: the global fail-safe tuple flow (the no-active idle theme + new-profile default), `.catch{IOException→emptyPreferences}` + `sanitizeTuple` per snapshot.
  - `setSeed/setDark/setMode/setShift/setMaxItems/setOverrides` (and `setFs` unchanged).
  - **`sanitizeTuple`** — PURE, never-throws, per-entry: junk seed (not 6/8-hex) → default seed; bad mode (∉ Colorful|Simple|HighContrast) → Colorful; out-of-range `poolShift` (0..360)/`maxItems` (1..64) → defaults; `poolOverrides` parsed PER-ENTRY (String key → Int index, validate ARGB ∈ [0, 0xFFFFFFFF]) — drop only the bad slot, keep the good.
  - Legacy `Resolved`/`sanitize`/`setBase`/`setDeltas` `@Deprecated`-retained (SettingsScreen + the legacy flow path).
- Verified: `:app:compileDebugKotlin` BUILD SUCCESSFUL (deprecation warnings only — SettingsScreen/GalleryActivity/AppContainer on the retained shims; no errors).

**Task 2 — reactive seedTheme (WR-02) + editor intents + reworked tests (`522324d`)**

- `di/AppContainer.kt`:
  - `seedTheme` now applies the PERSISTED tuple in ONE `themeResolver.apply(...)` call; the no-active branch collects **`themePrefs.tupleFlow` REACTIVELY via `flatMapLatest`** (the WR-02 fix — replaces the one-shot `?: themePrefs.flow.firstOrNull() ?: DEFAULT` that froze the idle theme on first read). Overrides bridged `Long → Color(it.value.toInt())` at the apply seam.
  - Added the durable editor intents the 15-06 UI calls: `setActiveSeed`/`setActiveMode`/`setActiveShift`/`setActiveDark`/`setActiveOverride`/`resetActiveTheme` — each routes active→`mutateActiveProfile` (atomic read-modify-write) / idle→`themePrefs` via `writeScope`, NEVER a composition scope (T-15-05-04, [[dinghy-compose-write-scope-cancellation]]). `setActiveOverride` does a read-modify-write of the sparse `poolOverrides`; `resetActiveTheme` clears the tuple to defaults but leaves `fsChoice` (D-09).
  - Dropped the now-unused `DEFAULT_SEED_HEX`/`DEFAULT_POOL_MAX_ITEMS`/`ThemeBase` imports; added `androidx.compose.ui.graphics.Color`.
- Tests (all rewritten onto the tuple model):
  - `ProfileThemeSeedTest` — `toThemeTuple()` corrupt-primitive table (junk seed→default, bad mode→Colorful, out-of-range shift/maxItems→defaults, bad fs→M) + the per-entry override test (1 good + 3 malformed → only the good survives) + corrupt-everything→`TUPLE_DEFAULT`, all assert no-throw.
  - `TokenDeltaSerializationTest` — REWRITTEN: an old-shape blob (only `themeBase`/`themeDeltaArgb`) fresh-start-decodes (legacy fields ignored, tuple defaults, `toThemeTuple()==TUPLE_DEFAULT`) + a new-shape `PersistedProfile` round-trips exactly incl. a 2-entry `poolOverrides`.
  - `ThemePrefsFallbackTest` — reworked to `sanitizeTuple`; zero `deltas.overrides`/`TokenDelta.Role` references; seed/mode/shift/maxItems/fs fail-safe + the per-entry override tolerance + fully-corrupt→`TUPLE_DEFAULT`.
- Verified: `:app:testDebugUnitTest --tests …ProfileThemeSeedTest --tests …TokenDeltaSerializationTest --tests …ThemePrefsFallbackTest` BUILD SUCCESSFUL (classes listed explicitly — AGP glob gotcha avoided). Both sourcesets compile.

## Verification

- Task 1: `:app:compileDebugKotlin` BUILD SUCCESSFUL (deprecation warnings only).
- Task 2: `:app:testDebugUnitTest` (the three named classes, explicit) BUILD SUCCESSFUL — all GREEN.
- Acceptance greps (all pass): `poolOverrides: Map<String, Long>` ×2 (String-keyed) ✓; `themeBase`/`themeDeltaArgb` `@Deprecated` retained ✓; `seedTheme` body has NO `firstOrNull`, uses `flatMapLatest` into `themePrefs.tupleFlow` (WR-02) ✓; `themeResolver.apply` single call ✓; intents `setActiveSeed`/`setActiveMode`/`setActiveShift`/`setActiveOverride`/`resetActiveTheme` present ✓; `grep -c "deltas.overrides\|TokenDelta.Role" ThemePrefsFallbackTest.kt` == 0 ✓.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — blocking] ADDITIVE tuple instead of in-place replacement of the legacy ThemePrefs/Profile API**
- **Found during:** Task 1, reading the consumers before touching `ThemePrefs`/`Profile`.
- **Issue:** The plan's wording — Task 1 "REPLACE the persisted keys + `Resolved` (lines 78-91) with the tuple … `sanitize`'s SIGNATURE changes (the old `(base, fs, roleKeys, argb)` shape is gone)" — would hard-remove `ThemePrefs.Resolved`, `ThemePrefs.sanitize`, `setBase`, `setDeltas`, and `Profile.toThemeResolved`. But `SettingsScreen.kt` (lines 145-149 reads `resolved.base/.deltas/.fs`; 377-379 sets `themeBase`/`themeDeltaArgb`; 529/543/560 calls `mutateActiveProfile { it.copy(themeBase=…) }` + `themePrefs.setBase/setDeltas`) and `ProfileStoreTest`/`ActiveConfigDerivationTest` all read those AT THIS WAVE. Gradle compiles the whole main+test sourceset before any `--tests` filter, so removing them would brick compilation — directly violating the plan's own #1 load-bearing must-have ("both sourcesets compile at this wave boundary"). SettingsScreen is explicitly NOT rebuilt until 15-06.
- **Fix:** ADD the tuple substrate (`ThemeTuple`/`sanitizeTuple`/`tupleFlow`/`setSeed…setOverrides`/`Profile.toThemeTuple`) ALONGSIDE the retained `@Deprecated` legacy shims (`Resolved`/`sanitize`/`setBase`/`setDeltas`/`toThemeResolved`, behavior-identical). The tuple is the LIVE source of truth (seedTheme + the rewritten tests use it); the legacy path is dead-but-compiling. 15-06 deletes the shims exactly as the plan's `affects:` already states. This is the identical two-step-retirement pattern 15-04 established for `ThemeResolver`'s `TokenDelta` API — so it is consistent with the wave strategy, just applied to the persistence layer too.
- **Files modified:** `Profile.kt`, `ThemePrefs.kt`
- **Commit:** `8e17d01`

No other deviations — the tuple shape, per-entry String-keyed `poolOverrides`, never-throws sanitize, fresh-start decode, redaction preservation, theme-out-of-ConnectionConfig, the WR-02 reactive branch, the single-apply, and the editor intents all match the plan.

## Known Stubs

None. The tuple persistence + sanitize produce a complete, validated `ThemeTuple` (or `TUPLE_DEFAULT` on any corruption). The `@Deprecated` legacy `Resolved`/`sanitize`/`setBase`/`setDeltas`/`toThemeResolved` + `themeBase`/`themeDeltaArgb` are intentional, flagged transitional bridges slated for deletion in 15-06 (D-05 two-step), not stubs. The editor intents (`setActive*`/`resetActiveTheme`) are the durable write surface 15-06's editor consumes — present and tested via the persistence path, not dead.

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/config/Profile.kt (tuple + @Deprecated legacy fields + toThemeTuple)
- FOUND: app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt (ThemeTuple + sanitizeTuple + tupleFlow + setters)
- FOUND: app/src/main/java/works/mees/dinghy/di/AppContainer.kt (reactive seedTheme + editor intents)
- FOUND: app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt (tuple + per-entry tests)
- FOUND: app/src/test/java/works/mees/dinghy/theme/TokenDeltaSerializationTest.kt (fresh-start decode + tuple round-trip)
- FOUND: app/src/test/java/works/mees/dinghy/theme/ThemePrefsFallbackTest.kt (sanitizeTuple fail-safe)
- FOUND: commit 8e17d01 (Task 1)
- FOUND: commit 522324d (Task 2)
- VERIFIED: :app:compileDebugKotlin SUCCESSFUL; the three named test classes GREEN; both sourcesets compile
