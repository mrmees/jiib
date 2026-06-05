# Phase 14: Multi-Printer Switching - Pattern Map

**Mapped:** 2026-06-04
**Files analyzed:** 12 (8 new, 4 modified)
**Analogs found:** 12 / 12 (every new/modified file has a strong in-tree analog — this is a generalize-existing-patterns phase, not a greenfield one)

## File Classification

| New/Modified File | New/Mod | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|---------|------|-----------|----------------|---------------|
| `config/ProfileStore.kt` | NEW | store (persistence) | CRUD (DataStore read-Flow + suspend writers) | `config/ConnectionStore.kt` | exact (role + data flow) |
| `config/Profile.kt` (+ `PersistedProfile`) | NEW | model (immutable value + `@Serializable`) | transform (value → `ConnectionConfig`/theme triple) | `config/ConnectionConfig.kt` | exact (role) |
| `di/AppContainer.kt` (active-config derivation + theme re-seed + `hasConfig`) | MOD | DI / service-locator | event-driven (Flow `combine`/`flatMapLatest`/`distinctUntilChanged`) | `di/AppContainer.kt` (self — existing `spine`/`seedTheme`/`hasConfig` idioms) | exact (in-file precedent) |
| `service/MoonrakerService.kt` (one-line config source swap) | MOD | foreground service | event-driven (config-loop rebind) | `service/MoonrakerService.kt:94` (self) | exact (the seam stays unchanged; only the arg changes) |
| `ui/route/TopRoute.kt` (`Dest.Devices`) | MOD | route enum | request-response (pure route derive) | `ui/route/TopRoute.kt:31` (self — the `Dest` enum) | exact (one-token addition) |
| `ui/screen/DevicesScreen.kt` | NEW | screen (Compose, Field + Gutter) | request-response (tile grid → tap = persist active) | `ui/files/FilesScreen.kt` (scroll-Field + gutter Back) + `ui/shell/AppDrawer.kt` (tile grammar) | role-match (composite) |
| `ui/shell/AppDrawer.kt` (Devices tile LIVE + active-name subtitle) | MOD | shell nav surface | request-response | `ui/shell/AppDrawer.kt` (self — the runtime-greying Webcam/Spool precedent) | exact (in-file precedent) |
| `ui/shell/AppShell.kt` (host `Dest.Devices` + suppress swipe + thread active-name) | MOD | shell host | request-response | `ui/shell/AppShell.kt` (self — the `webcamEnabled`/`spoolEnabled` threading + swipe-suppress set) | exact (in-file precedent) |
| `ui/screen/SettingsScreen.kt` (profile-list CRUD + Appearance→active) | MOD | screen (Compose conventional) | CRUD (form save) + transform (theme write) | `ui/screen/SettingsScreen.kt` (self — connection form + mDNS + Appearance reused 1:1) | exact (in-file precedent) |
| `ui/webcam/WebcamPrefs.kt` (re-key host→profileId, D-06) | MOD | store (per-printer pref) | CRUD (dynamic-key DataStore) | `ui/webcam/WebcamPrefs.kt` (self) + `theme/ThemePrefs.kt` (dynamic-key idiom) | exact (mechanical re-key) |
| `ui/webcam/WebcamHolder.kt` (caller: key on profileId not host, D-06) | MOD | holder (per-session) | event-driven | `ui/webcam/WebcamHolder.kt` (self) | exact (mechanical caller change) |
| `DinghyApp.kt` (add `profiles.preferences_pb` DataStore) | MOD | Application (DataStore owner) | — (one-time wiring) | `DinghyApp.kt` (self — the 4 existing `PreferenceDataStoreFactory.create` blocks) | exact (in-file precedent) |

**Wave-0 test files** (mirror existing test patterns; not listed above): `ProfileStoreTest`, `ActiveConfigDerivationTest`, `ProfileThemeSeedTest`, `ProfileSurvivesRestartTest` — analogs are the existing `MoonrakerServiceTest` (virtual-time seam test) and `ThemePrefsFallbackTest` (pure-`sanitize` host test). See RESEARCH.md §Validation Architecture for exact paths/commands; the RED scaffolds must compile day-one (MEMORY [[dinghy-wave0-red-scaffold-compile]]).

---

## Pattern Assignments

### `config/ProfileStore.kt` (store, CRUD) — NEW

**Analog:** `app/src/main/java/works/mees/dinghy/config/ConnectionStore.kt` (copy its shape VERBATIM)

**Imports pattern** (`ConnectionStore.kt` lines 1-12): injected `DataStore<Preferences>`, `catch`/`map` on `dataStore.data`, `java.io.IOException`. Add `kotlinx.serialization.json.Json` + `@Serializable` for the blob.

**Fail-safe read Flow + pure sanitize** (`ConnectionStore.kt` lines 38-44, 79-88) — replicate exactly:
```kotlin
val config: Flow<ConnectionConfig?> =
    dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> sanitize(prefs[KEY_HOST], prefs[KEY_PORT], prefs[KEY_API_KEY]) }
// companion: PORT_RANGE = 1..65535; sanitize is PURE (host-testable, no IO)
```
**Replicate:** two flows — `profiles: Flow<List<Profile>>` (`map { sanitize(prefs[KEY_PROFILES]) }`, a single kotlinx-serialized blob string key) and `activeId: Flow<String?>` (`map { it[KEY_ACTIVE_ID] }`); the `.catch { IOException → emptyPreferences() }` recovery on BOTH; a PURE `sanitize(rawBlob: String?): List<Profile>` companion (`Json.decode` in `runCatching` → drop malformed entries via per-entry `ConnectionStore.sanitize` parity: trim/blank-reject host, port in `1..65535`). Suspend writers `upsert`/`delete`/`setActive` mirror `save`/`clear` (lines 47-62).

**Change vs analog:**
- Store a `List<Profile>` blob (one `stringPreferencesKey("profiles")`) + a separate `stringPreferencesKey("active_id")` — NOT three flat keys. (RESEARCH Pattern 1; per-profile-key scheme rejected.)
- **D-12 auto-pick lives in the `delete` WRITER, not the read derivation** (RESEARCH Pattern 1 critical detail + Pitfall 4): `delete(id)` reads the list, removes `id`, and if `id == activeId` rewrites `KEY_ACTIVE_ID` to the first remaining profile (or clears it when none remain). Keep the read derivation pure (dangling active-id → `null` → Connect prompt).
- `apiKey` redaction: the `@Serializable PersistedProfile` must NOT serialize/log the key in `toString()` (V7 — see Profile.kt below).

---

### `config/Profile.kt` / `PersistedProfile` (model, transform) — NEW

**Analog:** `app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt`

**Immutable value + redacting toString** (`ConnectionConfig.kt` lines 14-28) — replicate the redaction:
```kotlin
override fun toString(): String =
    "ConnectionConfig(host=$host, port=$port, apiKey=${if (apiKey != null) "***" else "null"})"
```
**Replicate:** a `data class` value type; a `toConnectionConfig()` projection that produces the `ConnectionConfig` (host/port/apiKey ONLY — this is what `distinctUntilChanged` keys on); a redacting `toString()` masking `apiKey` to `***` (V7, RESEARCH Security Domain — this is NEW code so it MUST get the same treatment, the blob is not encrypted-at-rest).

**Change vs analog:**
- Add `id: String` (UUID, `java.util.UUID.randomUUID()`, D-05), `name: String? = null` (optional, defaults to host for display, D-10).
- Carry the FULL theme as PERSISTED PRIMITIVES, NOT a resolved `ThemeTokens` (RESEARCH Pitfall 2 / Anti-Pattern): `themeBase: String` (`ThemeBase.name`), `fsChoice: String` (`FontScale.name`), `themeDeltaArgb: Map<String, Long>` (`TokenDelta.Role.name` → unsigned-32 ARGB) — exactly the shape `ThemePrefs` persists (`ThemePrefs.kt` lines 84-91). Provide a `toThemeResolved()` reusing `ThemePrefs.sanitize` parity so corrupt theme data fails safe.
- Split persisted (`@Serializable PersistedProfile`) vs runtime (`Profile`) if convenient, mirroring `ThemePrefs.Resolved` vs the persisted keys.

---

### `di/AppContainer.kt` (DI, event-driven) — MODIFIED

**Analog:** `di/AppContainer.kt` itself — the existing `spine` flat-map idiom (lines 121-145), `hasConfig` (line 190), and `seedTheme` (lines 224-230).

**Existing derived-flow idiom to mirror** (`AppContainer.kt` lines 121-133):
```kotlin
val printerState: Flow<PrinterState> = spine.flatMapLatest { it?.printerState ?: flowOf(PrinterState()) }
val hasConfig: Flow<Boolean> = connectionStore.config.map { it != null }   // line 190
```
**Existing seedTheme to REWIRE** (`AppContainer.kt` lines 224-230):
```kotlin
fun seedTheme(scope: CoroutineScope) {
    scope.launch {
        themePrefs.flow.collect { resolved ->
            themeResolver.apply(resolved.base, resolved.deltas, resolved.fs)
        }
    }
}
```

**Add (RESEARCH Pattern 2 + 3):**
```kotlin
val activeProfile: Flow<Profile?> =
    combine(profileStore.profiles, profileStore.activeId) { list, id -> list.firstOrNull { it.id == id } }

val activeConfig: Flow<ConnectionConfig?> =
    activeProfile.map { it?.toConnectionConfig() }.distinctUntilChanged()   // ⚠ distinctUntilChanged LOAD-BEARING

val hasConfig: Flow<Boolean> = activeConfig.map { it != null }              // REPLACES the connectionStore-backed line 190
```
**Change vs analog:**
- Constructor gains `profileDataStore: DataStore<Preferences>` → `profileStore = ProfileStore(profileDataStore)` (alongside the existing 4 stores at lines 66-88).
- `seedTheme` switches from `themePrefs.flow.collect { … }` to `activeProfile.flatMapLatest { p -> flowOf(p?.toThemeResolved() ?: themePrefs.flow.firstOrNull() ?: ThemePrefs.DEFAULT) }.collect { themeResolver.apply(it.base, it.deltas, it.fs) }`. Keep `themePrefs` as the NEW-PROFILE default + idle look (RESEARCH Pattern 3 "Global-theme relationship" — do NOT delete it).
- `distinctUntilChanged` AFTER `.map { toConnectionConfig() }` so a name/theme edit on the active profile does NOT churn the spine (RESEARCH Pitfall 1; `ConnectionConfig` equality is structural). The class already `@OptIn(ExperimentalCoroutinesApi::class)` and imports `flatMapLatest`/`combine`/`map` — no new annotations.

---

### `service/MoonrakerService.kt` (service, event-driven) — MODIFIED (one line)

**Analog:** itself — `runConfigLoop` (lines 317-334) is the clean teardown+rebind seam, **UNCHANGED**.

**The ONLY edit** (`MoonrakerService.kt` line 94):
```kotlin
runConfigLoop(
    configFlow = container.activeConfig,   // was: container.connectionStore.config
    ...
)
```
**Replicate / change:** Nothing else. The `cancelAndJoin()`-before-rebuild (line 325) does the switch teardown for free. **Anti-pattern (RESEARCH + CONTEXT explicit): DO NOT add a second rebind path** — no UI-driven `publishSpine`, no manual disconnect sequencer. Switching = `activeConfig` emits a different value.

---

### `ui/route/TopRoute.kt` (route enum) — MODIFIED (one token)

**Analog:** itself — the `Dest` enum (line 31).

**Change:** add `Devices` to the enum:
```kotlin
enum class Dest { PrintStatus, Temperature, Move, Extrude, Files, Macros, Console, Calibration, Webcam, Spool, Devices, Settings }
```
The pure `derive()` (lines 57-62) is UNCHANGED — `!cfgPresent → Connect` already covers the D-11 empty-state once `hasConfig` is "has active profile."

---

### `ui/screen/DevicesScreen.kt` (screen, Field + Gutter) — NEW

**Analogs (composite):**
1. `ui/files/FilesScreen.kt` — the scroll-Field + explicit-gutter-Back screen pattern (the precedent for a screen that suppresses the swipe-drawer).
2. `ui/shell/AppDrawer.kt` — the SQUARE outline tile grid grammar (`LazyVerticalGrid(GridCells.Fixed(4))`, `aspectRatio(1f)`, `spacedBy(12.dp)`, `padding(16.dp)`, `RoundedCornerShape(t.rCtrl)`, `border(2.dp, …)`) to reuse VERBATIM for the printer tiles.
3. `designsystem/layout/ScreenScaffold.kt` — the Field/Gutter slots (Focus omitted; `field = { grid }`, `gutter = { Back }`).

**Tile grid grammar to copy** (`AppDrawer.kt` lines 87-110 + the `DrawerTile` body lines 193-242):
```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    modifier = Modifier.fillMaxSize().background(t.bg).padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) { items(...) { ... } }
// tile: .aspectRatio(1f).clip(shape).background(t.surface2).border(BorderStroke(2.dp, t.accentLine), shape)
```

**Build (per UI-SPEC §New surface + RESEARCH Code Examples):**
- `ScreenScaffold(field = { tile grid }, gutter = { green Back })` — Focus omitted (no single primary item), mirroring the Files scroll-Field + gutter pattern.
- Each profile tile: a device glyph (`print` or `dns`; MUST NOT repeat the gutter glyph — icon-no-repeat law), **name** at `fsSp(20f, t.fs)` SemiBold (Geist), **`host:port`** at `fsSp(17f, t.fs)` Regular (GeistMono, `t.text2`). UI-SPEC §Typography floors: 20/17/15sp — honor them (MEMORY [[dinghy-font-sizes-too-small]]).
- **Active tile** (D-03): accent outline (`t.accentLine`) + `t.accentSoft` fill tint + an accent active-marker glyph (unique on-screen). Inactive saved tiles use the ordinary `t.accentLine`/`t.surface2` live styling.
- **"Add printer" tile:** an `add`-glyph live tile → tap navigates to Settings (D-01).
- **Tap a profile tile** → persist it active via `profileStore.setActive(id)` → the `runConfigLoop` seam rebinds → recovery Splash → new printer's Status (D-02). **No confirm guard on switch** (non-destructive). Delete is NOT on this screen — it lives in Settings (D-14).
- Wire into `AppShell.when(dest)` (see AppShell mod) and add to the swipe-suppress set.

**Change vs analogs:** the drawer tiles are a fixed compile-time list hosted in a `Dialog`; Devices tiles are a DATA-DRIVEN list (one per `Profile` + the Add tile) hosted as a normal `Dest` screen inside `ScreenScaffold`, with an explicit green-`Intent.Go` gutter Back (the Files exit precedent) since the swipe-drawer is suppressed.

---

### `ui/shell/AppDrawer.kt` (shell nav) — MODIFIED

**Analog:** itself — the `DRAWER_TILES` set (lines 137-170) + the runtime-greying Webcam/Spool precedent (lines 159-166, 186-192).

**The greyed Devices tile to wire LIVE** (`AppDrawer.kt` line 167):
```kotlin
DrawerTileSpec(label = "Devices", symbol = "cable", dest = null),   // ← flip dest to Dest.Devices
```
**Runtime-input threading precedent to copy** (`AppDrawer.kt` lines 79-80, signature + the `webcamEnabled`/`spoolEnabled` params):
```kotlin
@Composable fun AppDrawer(..., webcamEnabled: Boolean = false, spoolEnabled: Boolean = false)
```

**Change (D-01/D-03):**
- `dest = Dest.Devices` (keep `cable` glyph — unique, icon-no-repeat law verified vs lines 137-169).
- Add an `activeName: String? = null` param to `AppDrawer` + `DrawerTile`, threaded the SAME way `webcamEnabled`/`spoolEnabled` are (AppShell collects it — see AppShell mod). Render an optional 15sp Regular `t.text2` subtitle (ellipsized) under the "Devices" 16sp label in `DrawerTile`'s `Column` (lines 222-241). This is the ONLY tile that gains a subtitle.
- Do NOT touch the `live`/`outline` greyed-styling logic (lines 190-220) — Devices is a compile-time `dest != null` live tile, not a runtime-gated one like Webcam/Spool.

---

### `ui/shell/AppShell.kt` (shell host) — MODIFIED

**Analog:** itself — the `webcamEnabled`/`spoolEnabled` collect-and-thread block (lines 180-181, 240) + the swipe-suppress set (line 420) + the `when(dest)` host (lines 429-584).

**Active-name collect (mirror `webcamEnabled` at lines 180-181):**
```kotlin
val activeName by container.activeProfile.map { it?.displayName }.collectAsStateWithLifecycle(initialValue = null)
```
**Swipe-suppress set to extend** (`AppShell.kt` line 420):
```kotlin
dest !in setOf(Dest.Files, Dest.Console, Dest.Macros, Dest.Calibration, Dest.Webcam, Dest.Spool, Dest.Devices)
```
**Add `Dest.Devices` host** in `when(dest)` (mirror the `Dest.Webcam`/`Dest.Files` arms, lines 452-565):
```kotlin
Dest.Devices -> DevicesScreen(container = container, onAddPrinter = { navigateTo(Dest.Settings) }, onBack = { goBack() })
```
And pass `activeName = activeName` into the `AppDrawer(...)` call (lines 683-690).

**Change vs analog:** none structural — this is the same collect → thread → host → suppress pattern the Webcam (10-07) and Spool (11-06) tiles already established.

---

### `ui/screen/SettingsScreen.kt` (screen, CRUD + transform) — MODIFIED

**Analog:** itself — the connection form (lines 139-293) + mDNS scan (lines 184-253) + the Appearance section (lines 295-368), all REUSED 1:1.

**Connection-save path to generalize** (`SettingsScreen.kt` lines 256-293): the validate-then-`store.save(config)` block becomes per-profile `upsert`. **Appearance write target to retarget** (lines 302-368): the existing `container.themeResolver.setBase(...)` + `container.themePrefs.setBase(...)` dual-write (live + persist) re-points its PERSIST target from the global `themePrefs` to the **active profile's** theme fields (via `profileStore.upsert(active.copy(themeBase = …))`), keeping the live `themeResolver.setBase` immediate-retheme.

**Change (D-09/D-13/D-14):**
- "Connection" section becomes a **list of profile rows** (name 20sp SemiBold + `host:port` 17sp GeistMono, active row marked with the accent marker), each tappable to **edit** (opens the existing host/port/key + mDNS form 1:1) + an **"Add printer"** row opening the same form blank. Reuse the `DiscoveredPrinterRow` row grammar (lines 442-472) as the profile-row template (name + `host:port`, `clickable`).
- Each editable profile gets a **Delete** affordance → routes through `ConfirmGuard` (see below).
- Appearance: ONLY the write target changes (active profile's theme primitives); controls unchanged.
- The form `onConnectionSaved`/`onPickSpoolForFile`-style callbacks stay; `store.save` → `profileStore.upsert(profile)` (preserving the no-clobber blank-key logic, lines 270-281).

---

### Delete Confirm guard (within SettingsScreen, D-14)

**Analog:** `app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt` (reuse — do NOT build a new guard) — see how `FilesScreen` already invokes it (`FilesScreen.kt:48` import + its delete-gate usage).

**Invocation contract** (UI-SPEC §Copywriting):
```kotlin
ConfirmGuard(
    title = "Delete printer?",
    message = "This removes ${name} and its saved theme. This can't be undone.",
    confirmLabel = "Delete",        // red Intent.Danger (the default destructive=true)
    cancelLabel = "Keep",
    onConfirm = { scope.launch { profileStore.delete(id) } },   // D-12 auto-pick fires in the store writer
    onCancel = { /* dismiss */ },
)
```
**Change vs analog:** none — `ConfirmGuard` already defaults to `destructive = true` (red `Intent.Danger`, `--stop-soft` tint, opaque bg under tint). Just supply the copy + wire `onConfirm` to the store delete writer.

---

### `ui/webcam/WebcamPrefs.kt` (per-printer pref, CRUD) — MODIFIED

**Analog:** itself — the dynamic-key idiom (lines 48-63), modeled on `theme/ThemePrefs.kt`'s `deltaArgbKey`.

**Key source to change** (`WebcamPrefs.kt` lines 48-63):
```kotlin
fun preferredCam(host: String): Flow<String?> = ...                  // → preferredCam(profileId: String)
suspend fun setPreferredCam(host: String, camId: String) = ...       // → setPreferredCam(profileId: String, ...)
fun preferredCamKey(host: String) = stringPreferencesKey("preferred_cam_$host")  // → "preferred_cam_$profileId"
```
**Change (D-06):** rename the parameter `host` → `profileId` throughout (3 signatures + the key prefix). Mechanical. D-07 = no migration: old `preferred_cam_<host>` entries are simply orphaned (RESEARCH Pattern 4). The fail-safe `.catch { IOException → emptyPreferences() }` read stays.

---

### `ui/webcam/WebcamHolder.kt` (holder caller, D-06) — MODIFIED

**Analog:** itself — the holder reads `webcamPrefs.preferredCam(host)` (line 248) + writes `webcamPrefs.setPreferredCam(host, camId)` (line 182), where `host` comes from the injected `ConnectionConfig.host`.

**Change (RESEARCH Pitfall 5):** the holder must key on the **active profile id**, not `cfg.host` — otherwise two same-host profiles (D-05 allows it) share a preferred cam. Thread the profile id into `webcamBitmapHolder(...)` (line 389) and the holder ctor; the id is available off `container.activeProfile.map { it?.id }` (AppShell collects it the same way it collects `cfg` at `AppShell.kt:186`). This is a caller change, not a logic change.

---

### `DinghyApp.kt` (DataStore owner) — MODIFIED

**Analog:** itself — the four existing `PreferenceDataStoreFactory.create` blocks (lines 40-64) + the `AppContainer(...)` construction (lines 66-83).

**The create-once pattern to copy** (`DinghyApp.kt` lines 61-64 + the single-writer comment):
```kotlin
val webcamDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = appScope,
    produceFile = { applicationContext.preferencesDataStoreFile("webcam.preferences_pb") },
)
```
**Change (RESEARCH Pitfall 3 — single-writer):** add a FIFTH file `profiles.preferences_pb` created ONCE here, injected into `AppContainer(profileDataStore = …)`. Do NOT construct the `DataStore` anywhere else (DataStore enforces one instance per file per process). `seedTheme(appScope)` (line 85) is unchanged at the call site (its internals change in AppContainer).

---

## Shared Patterns

### DataStore fail-safe read + pure sanitize
**Source:** `config/ConnectionStore.kt` lines 38-44, 79-88; `theme/ThemePrefs.kt` lines 39-52, 103-124.
**Apply to:** `ProfileStore` (new) and `WebcamPrefs` (re-key — keep its existing fail-safe).
```kotlin
dataStore.data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { prefs -> sanitize(...) }   // sanitize is PURE, host-testable, never throws
```
**Why load-bearing:** 4 stores already do this identically; a corrupt blob → empty → Connect prompt (D-11), never a black screen. The `sanitize` is the V5 input-validation boundary (host/port range) AND the host-pure unit-test surface.

### API-key redaction (V7)
**Source:** `config/ConnectionConfig.kt` lines 25-27.
**Apply to:** `PersistedProfile.toString()` (new — the key now lives in the profile blob; this is NEW code so it MUST get the same masking) and never log the profile blob.
```kotlin
override fun toString(): String = "...apiKey=${if (apiKey != null) "***" else "null"}..."
```

### Theme = persisted PRIMITIVES, applied via `ThemeResolver.apply` (one re-emit)
**Source:** `theme/ThemePrefs.kt` lines 84-91 (persisted shape) + `theme/ThemeResolver.kt` lines 114-120 (`apply(base, deltas, fs)`).
**Apply to:** `Profile.toThemeResolved()` (store `ThemeBase.name`/`FontScale.name`/`Role→ARGB Long`, NEVER a resolved `ThemeTokens`) and `AppContainer.seedTheme` (one atomic re-emit per switch).
**Why:** `apply` re-emits the whole triple once (no 3-call flicker); persisted primitives fail-safe-recover via `ThemePrefs.sanitize` parity. Anti-pattern: persisting baked `ThemeTokens` Colors (RESEARCH Pitfall 2).

### Runtime-input threading into the drawer (collect-in-AppShell → param)
**Source:** `ui/shell/AppShell.kt` lines 180-181, 240 + `AppDrawer.kt` signature lines 79-80.
**Apply to:** the Devices active-name (`activeName`), the SAME shape `webcamEnabled`/`spoolEnabled` use.

### The rebind seam is the ONLY data path into the service
**Source:** `service/MoonrakerService.kt` `runConfigLoop` lines 317-334 (`cancelAndJoin()` before rebuild) + `di/SpineHandle.kt` (atomic republish).
**Apply to:** all switching. Switching = `activeConfig` emits a different value; the seam does the teardown + `SpineHandle` republish (no-stale-state) for free. **Anti-pattern: any second rebind path.**

### Scroll-Field screen → suppress swipe-drawer + explicit gutter Back
**Source:** `ui/files/FilesScreen.kt` (Field + gutter Back) + `AppShell.kt` line 420 (suppress set).
**Apply to:** `DevicesScreen` — a scrollable tile Field fights the vertical-drag drawer gesture, so it joins the suppress set and carries a green `Intent.Go` gutter Back as the explicit exit (D-05).

---

## No Analog Found

None. Every new and modified file has a strong in-tree analog — Phase 14 is a deliberate generalize-existing-patterns phase (CONTEXT/RESEARCH both stress reuse). The single genuinely-new wrinkle (per-printer theme re-seed, D-08) reuses `ThemeResolver.apply` + `ThemePrefs`-persisted-primitives, so even it composes from existing parts.

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/` (config, di, service, theme, ui/{route,screen,shell,webcam,files}, designsystem).
**Files scanned:** 12 source analogs read in full (ConnectionStore, ConnectionConfig, WebcamPrefs, WebcamHolder, TopRoute, AppContainer, DinghyApp, ThemePrefs, ThemeResolver, AppDrawer, SettingsScreen, ConfirmGuard, AppShell, FilesScreen, ScreenScaffold) + MoonrakerService config-loop seam + RootController routing.
**Pattern extraction date:** 2026-06-04
**Grounding:** every excerpt cites a real file + line range read this session; verified against the actual tree (all expected analog paths exist as named in CONTEXT.md canonical_refs).
