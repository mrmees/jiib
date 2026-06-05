# Phase 14: Multi-Printer Switching - Research

**Researched:** 2026-06-04
**Domain:** Android / Kotlin profile management + DataStore persistence + Flow composition + per-printer theme re-seeding (reuse of the existing Phase-2/4 connection spine)
**Confidence:** HIGH (grounded in the actual source files named in CONTEXT.md canonical_refs; every recommendation binds to read code, not training-data theory)

## Summary

Phase 14 is a **data-model + Flow-composition** phase, not a protocol phase. The rebind machinery already exists and is JVM-tested: `MoonrakerService.runConfigLoop` (lines 317–334 of `MoonrakerService.kt`) collects a `Flow<ConnectionConfig?>` via `collectLatest` and `cancelAndJoin()`s the prior session job **before** building the new one. Switching printers = making that flow emit a different `ConnectionConfig`. The service stays **byte-for-byte unchanged** [VERIFIED: read `MoonrakerService.kt`]. The entire phase composes into `AppContainer` (a new profile store + active-id selection + a derived `Flow<ConnectionConfig?>` that replaces `connectionStore.config` at the service's collection site), `WebcamPrefs` (re-key host→profile-id), the theme seeding path (`AppContainer.seedTheme`), routing (`hasConfig` → "has active profile"), a new `Dest.Devices` screen, and a Settings restructure.

The **one genuinely net-new wrinkle** is per-printer theming (D-08). Today theme is process-scoped/connection-independent: `seedTheme` collects `themePrefs.flow` forever and pushes each `Resolved` into `themeResolver.apply(...)` (lines 224–230). For per-printer theme, the seeding source must become **the active profile's theme** — re-applied on every switch. The `ThemeResolver` already has the exact primitive needed: `apply(base, deltas, fs)` applies a whole triple in one re-emit (`ThemeResolver.kt` lines 114–120), and `resolve()` is pure — so a `flatMapLatest` over the active-profile-id that swaps which theme triple is collected re-seeds the resolver atomically with no partial state. Flicker is bounded because the switch routes through the existing recovery Splash (D-02) — the theme triple lands before the Shell re-composes.

**Primary recommendation:** Single **Preferences-DataStore blob** holding a kotlinx-serialized `List<PersistedProfile>` under one string key (mirroring `ConnectionStore`'s injected-DataStore + fail-safe-read + pure-`sanitize` shape verbatim), plus a separate active-profile-id string key. Derive the active `ConnectionConfig` in `AppContainer` via `combine(profiles, activeId)` → pick → `map { it?.toConnectionConfig() }` → `distinctUntilChanged`, and feed THAT to the service in place of `connectionStore.config`. Re-key `WebcamPrefs` on `profileId`. Re-seed theme via `flatMapLatest(activeProfile) { themeTripleFor(it) }`. Generate ids with `java.util.UUID.randomUUID()` (fine on API 23).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Profile persistence (CRUD, active-id) | Persistence (DataStore) | — | Mirrors `ConnectionStore`; single-writer DataStore actor owned by `DinghyApp` |
| Active-`ConnectionConfig` derivation | DI / service-locator (`AppContainer`) | Persistence | "Service constructs, UI consumes" (D-02) — selection logic is headless, in the container, not the service or UI |
| Spine teardown + rebind on switch | Foreground Service (`MoonrakerService`) | — | The existing `runConfigLoop` seam; **unchanged** — it already owns clean teardown |
| Per-printer theme re-seed | DI (`AppContainer.seedTheme`) → `ThemeResolver` | Persistence | `ThemeResolver` is the single active-theme source of truth both toolkits collect; seeding source changes |
| Per-printer webcam pref | Persistence (`WebcamPrefs`) | — | Re-key the existing dynamic-key idiom from host to profile-id |
| Devices switcher screen + drawer indicator | UI (Compose shell) | DI | New `Dest.Devices` + wire the greyed drawer tile; reads container flows |
| Settings profile CRUD + Appearance retarget | UI (`SettingsScreen`) | Persistence | Reuse the connection form + Appearance controls 1:1; only write target changes |
| Routing (empty-state, Splash-on-switch) | UI (`RootController`) | DI | `hasConfig` → "has active profile"; switch routes through the existing recovery Splash |

---

## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01..D-14 — DO NOT re-litigate)
- **D-01:** Wire the greyed "Devices" drawer tile (`cable` icon, `dest = null`) LIVE → opens new full-screen `Dest.Devices` field-of-printers list, one tile per profile (name + `host:port`, active highlighted) + "Add printer" tile → Settings. No mockup; follows LAYOUT.md/THEMING.md.
- **D-02:** Switching = instant rebind through the Splash. Tap printer → persist new active profile → existing `collectLatest` seam tears down + rebinds → recovery Splash → land on new printer's Status. **No confirm guard** (non-destructive).
- **D-03:** Active-printer name shows on the Devices drawer tile subtitle; active list tile highlighted. No new persistent chrome.
- **D-04:** Switching mid-print just switches — the old printer keeps printing untouched.
- **D-05:** Each profile has a **generated stable UUID** identity. name/host/port/apiKey can all change without breaking the active-profile pointer or per-printer prefs. Two profiles MAY share a host. Profile carries: id (UUID), optional name, host, port, apiKey?, + per-printer theme.
- **D-06:** Per-printer prefs **re-key on profile-id** (not host). Applies to `preferred_cam_<host>` → profile-id keyed.
- **D-07:** **No migration — fresh start.** New profile store starts empty; user re-adds printers. Old `ConnectionStore` keys simply not read. First run → 0 profiles → Connect prompt.
- **D-08:** **Each profile carries a FULL theme** (base + accent + text-size), overriding the global default. Switch → whole app look changes. Reuses `ThemeResolver`/`TokenDelta`. ⚠ Rewires theme **seeding**: global → active-profile, re-seeded on every switch.
- **D-09:** Theme edited via existing Settings "Appearance" section, now acting on the ACTIVE printer's theme. Controls reused 1:1; only write target changes.
- **D-10:** Profile name optional, defaults to host (or host:port). User can rename.
- **D-11:** 0 profiles → existing Connect prompt → Settings. Drives off "no active profile" instead of "no config."
- **D-12:** Deleting the **active** profile (others remain) → auto-select another remaining + rebind. Deleting the **last** profile → Connect prompt.
- **D-13:** Settings "Connection" section becomes a **list of saved profiles**, each tappable to edit, + "Add printer" opening the existing host/port/key + mDNS form 1:1. Editing active while connected re-saves → live rebind.
- **D-14:** **Deleting a profile is behind the full-screen Confirm guard** (`08-confirm.png`).

### Claude's Discretion (the OPEN questions this research answers)
- Exact UUID scheme, profile-store persistence shape, active-config derivation composition.
- Whether Devices list shows live per-printer status in v1 or just name/address (planner's call on cost).
- Theme-switch timing/flicker handling during the rebind Splash (planner's call).

### Deferred Ideas (OUT OF SCOPE)
- Profile import/export / QR-share of a printer profile.
- Live per-printer status badges for NON-active printers (needs background probing — the spine only knows the active printer).
- Cloud/remote (non-LAN) printer profiles.

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MULTI-01 (umbrella) | Generalize single `ConnectionConfig` into managed named profiles + persisted active selection; clean rebind on switch; no stale state; survives process death | Q1 (store shape) + Q2 (active-config derivation feeds the unchanged service) + Q3/Q4 (per-printer theme + pref re-key) + Q5 (UUID) + Validation Architecture (proves the success criteria) |

---

## Standard Stack

No new external dependencies. Everything is already in the project (CLAUDE.md prescriptive stack). The whole phase composes existing libraries.

### Core (all already present)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.datastore:datastore-preferences` | 1.1.x | Profile blob + active-id persistence | Already the project's settings store (`ConnectionStore`, `ThemePrefs`, `MacroPrefs`, `WebcamPrefs`). Coroutine/Flow-native, single-writer, minSdk 23. [VERIFIED: read `DinghyApp.kt` uses `PreferenceDataStoreFactory.create`] |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.x | Serialize `List<PersistedProfile>` into the single blob string | Already the project's JSON lib (CLAUDE.md). Compile-time codegen, no reflection — fine on weak CPU. `@Serializable data class` round-trips cleanly. [CITED: CLAUDE.md stack table] |
| `kotlinx-coroutines` (Flow) | 1.9.x | `combine`/`flatMapLatest`/`mapLatest`/`distinctUntilChanged` for active-config derivation | The container already uses `flatMapLatest`/`map` on `spine` (see `AppContainer.kt` lines 121–190). [VERIFIED: read `AppContainer.kt`] |
| `java.util.UUID` | JDK (Android API 1+) | Stable profile identity (D-05) | `UUID.randomUUID()` available since API 1; see Q5. [VERIFIED: Android API docs — `java.util.UUID` is in the core library] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Single kotlinx-serialized blob in Preferences DataStore | **Proto DataStore** | Proto gives a typed schema + built-in serializer, but adds a `.proto` file, the protobuf gradle plugin, and a build step the project deliberately avoids. The project's whole persistence idiom is Preferences-DataStore + pure `sanitize` (4 stores already follow it). Proto breaks the established pattern for zero benefit at this scale (2 printers). **Reject.** |
| Single blob | **Per-profile key scheme** (`profile_<uuid>_host`, etc., the `ThemePrefs` dynamic-key idiom) | Works, and matches `WebcamPrefs`'s per-host keying — but enumerating "all profiles" means scanning all keys with a prefix, deletion means removing N keys atomically, and there's no natural ordering. A single serialized `List` is simpler to read/write/reorder and is one atomic `edit`. **Reject** for the profile set; **keep** the dynamic-key idiom only for the per-profile webcam pref (Q4). |
| `UUID.randomUUID()` | Monotonic counter / timestamp id | A counter needs its own persisted "next-id" and isn't collision-safe across export/import (a deferred feature). UUID is self-contained, no shared state. **Keep UUID.** |

**No `npm`/install step** — this is Android/Gradle and every library is already declared. **Package Legitimacy Audit is N/A** (no new external packages introduced).

---

## Architecture Patterns

### System Architecture Diagram (active-config + theme derivation)

```
                       ProfileStore (NEW — mirrors ConnectionStore)
                       profiles.preferences_pb
                       ┌─────────────────────────────────────────┐
                       │ KEY_PROFILES (one JSON blob string)      │
                       │   = Json.encode(List<PersistedProfile>)  │
                       │ KEY_ACTIVE_ID (string, nullable)         │
                       └──────────────┬──────────────────────────┘
                                      │ fail-safe read (.catch IOException → empty)
                                      │ pure sanitize() → List<Profile>
                      ┌───────────────┴───────────────┐
                      ▼                                ▼
         profilesFlow: Flow<List<Profile>>   activeIdFlow: Flow<String?>
                      │                                │
                      └──────────┬─────────────────────┘
                                 ▼ combine(profiles, activeId)
                       activeProfileFlow: Flow<Profile?>   (pick by id; D-12 auto-pick lives in the writer, not here)
                                 │
            ┌────────────────────┼─────────────────────────────────┐
            ▼ map+distinctUntilChanged                              ▼ flatMapLatest
   activeConfig: Flow<ConnectionConfig?>              themeTriple: Flow<ThemePrefs.Resolved>
            │   (= profile.toConnectionConfig())          │ (= active profile's base/deltas/fs)
            │                                              ▼
            ▼  (REPLACES connectionStore.config           themeResolver.apply(base,deltas,fs)  ← one atomic re-emit
            │   at the service's collection site)         │
            ▼                                             ▼
   MoonrakerService.runConfigLoop (UNCHANGED)     LocalTokens / Views repaint (both toolkits)
   collectLatest → cancelAndJoin → buildSpineAndLaunch
            │
            ▼
   SpineHandle republished atomically → every screen reads the NEW printer
            │
            ▼
   hasActiveProfile (= activeConfig != null) drives RootController empty-state (D-11)
```

The diagram's left spine is the **only** data path into the service — there is no parallel rebind path (CONTEXT.md: "DO NOT build a second rebind path"). The right branch is the net-new theme re-seed.

### Pattern 1: ProfileStore mirrors ConnectionStore VERBATIM
**What:** A new `config/ProfileStore.kt` copying `ConnectionStore.kt`'s exact shape: injected `DataStore<Preferences>`, a fail-safe `.catch { if (e is IOException) emit(emptyPreferences()) else throw e }` read flow, suspend writers, and a **pure host-testable `sanitize`** companion.
**When to use:** This is the store for the profile set + active id.
**Example (the exact shape to copy, grounded in `ConnectionStore.kt` lines 38–62):**
```kotlin
// Source: pattern copied from ConnectionStore.kt (read 2026-06-04)
@Serializable
data class PersistedProfile(
    val id: String,                 // UUID (D-05)
    val name: String? = null,       // optional, defaults to host for display (D-10)
    val host: String,
    val port: Int = 7125,
    val apiKey: String? = null,
    // FULL theme (D-08) — persist the SAME primitives ThemePrefs persists, NOT a resolved ThemeTokens:
    val themeBase: String = "Dark",            // ThemeBase.name
    val fsChoice: String = "M",                // FontScale.name
    val themeDeltaArgb: Map<String, Long> = emptyMap(), // Role.name -> unsigned-32 ARGB
)

class ProfileStore(private val dataStore: DataStore<Preferences>) {
    val profiles: Flow<List<Profile>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> sanitize(prefs[KEY_PROFILES]) }   // pure, host-testable

    val activeId: Flow<String?> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY_ACTIVE_ID] }

    suspend fun upsert(profile: Profile) { /* decode list, replace-by-id or append, encode, edit both */ }
    suspend fun delete(id: String) { /* remove from list; if id == active, auto-pick first remaining or null (D-12) */ }
    suspend fun setActive(id: String?) { dataStore.edit { it[KEY_ACTIVE_ID] = id /* or remove */ } }

    companion object {
        private val KEY_PROFILES  = stringPreferencesKey("profiles")
        private val KEY_ACTIVE_ID = stringPreferencesKey("active_id")
        private val PORT_RANGE = 1..65535
        // PURE: decode the blob, drop any malformed entry (bad port / blank host), keep the rest.
        fun sanitize(rawBlob: String?): List<Profile> { /* Json.decode in runCatching → filter sanitize-per-entry */ }
    }
}
```
**Critical detail (D-12 — auto-pick on delete):** the auto-select-another-active lives in the **`delete` writer** (a suspend mutator that reads, removes, and conditionally rewrites `KEY_ACTIVE_ID`), NOT in the read-side derivation. Keeping derivation pure (pick-by-id, null if absent) means a dangling active-id resolves cleanly to `null` → Connect prompt, and the writer is the single place that enforces "never leave a dead no-active state while valid printers exist."

### Pattern 2: Active-config derivation in AppContainer (the service stays unchanged)
**What:** In `AppContainer`, compose the two flows into the `Flow<ConnectionConfig?>` the service already consumes. The ONLY change at the service call site is `configFlow = container.activeConfig` instead of `container.connectionStore.config`.
**Example (grounded in `MoonrakerService.kt` line 94 + `AppContainer.kt` line 190):**
```kotlin
// In AppContainer:
val activeProfile: Flow<Profile?> =
    combine(profileStore.profiles, profileStore.activeId) { list, id ->
        list.firstOrNull { it.id == id }
    }

val activeConfig: Flow<ConnectionConfig?> =
    activeProfile
        .map { it?.toConnectionConfig() }      // host/port/apiKey only
        .distinctUntilChanged()                // D-04/perf: editing the NAME or THEME of the active
                                               // profile must NOT churn the spine — only host/port/key changes rebind

// hasConfig becomes "has active profile" (D-11) — drop in for AppContainer.kt:190:
val hasConfig: Flow<Boolean> = activeConfig.map { it != null }
```
And in `MoonrakerService.onCreate` (line 93–95), the ONLY edit:
```kotlin
runConfigLoop(
    configFlow = container.activeConfig,   // was: container.connectionStore.config
    ...
)
```
**Why `distinctUntilChanged` is load-bearing:** without it, renaming the active profile or changing its theme would re-emit a `ConnectionConfig` (or the same one) and trigger a needless `cancelAndJoin` + reconnect. The config equality is by `data class` value (host/port/apiKey) — a theme/name edit produces an *equal* `ConnectionConfig`, so `distinctUntilChanged` suppresses the rebind. This is the seam that lets D-09 (edit active theme) and D-13 (rename) NOT bounce the connection. **[VERIFIED: `ConnectionConfig` is a `data class` — equality is structural; read `ConnectionConfig.kt` line 14]**

### Pattern 3: Per-printer theme re-seed (the net-new wrinkle, D-08)
**What:** Replace `seedTheme`'s "collect global `themePrefs.flow` forever" with "collect the **active profile's** theme triple, re-applying on each switch." The `ThemeResolver.apply(base, deltas, fs)` primitive already exists for exactly this (one atomic re-emit) — `ThemeResolver.kt` lines 114–120.
**Example (grounded in `AppContainer.kt` lines 224–230 + `ThemeResolver.kt`):**
```kotlin
fun seedTheme(scope: CoroutineScope) {
    scope.launch {
        activeProfile
            .flatMapLatest { p ->
                // a profile carries its OWN base/deltas/fs; reuse ThemePrefs.sanitize parity to
                // build a Resolved from the persisted primitives so corrupt theme data fails safe.
                flowOf(p?.toThemeResolved() ?: themePrefs.flow.firstOrNull() ?: ThemePrefs.DEFAULT)
            }
            .collect { r -> themeResolver.apply(r.base, r.deltas, r.fs) }
    }
}
```
**Global-theme relationship (the open sub-question):** Recommendation — **global `ThemePrefs` becomes the DEFAULT for a NEW profile**, not a live fallback for existing profiles. When "Add printer" creates a profile, seed its theme fields from the current global `ThemePrefs` (so a new printer inherits the user's current look). Once a profile exists, its theme is self-contained; the Appearance section writes to the active profile (D-09), not to global. Keep `themePrefs` in `AppContainer` only as the new-profile default source — do NOT delete it (it's still the seed for the very first profile and the "no active profile" idle look). This is the least-surprising model and avoids a flag-day deletion of `ThemePrefs`.
**Flicker handling (the open sub-question):** The switch persists active-id → `activeConfig` re-emits a distinct config → `collectLatest` tears down → `derive()` (TopRoute.kt line 57) sees `connection !is Connected` → **recovery Splash** (D-02). The theme re-seed (`themeResolver.apply`) fires off the *same* `activeProfile` emission, BEFORE the new spine reaches `Connected` and the Shell re-composes. So the new theme is already resolved by the time the Shell paints the new printer's Status. **No flicker work needed** — the Splash is the natural cover (CONTEXT D-02). One caveat to verify on-device: the Splash itself reads `LocalTokens`, so the Splash will appear in the *new* printer's theme mid-switch — which is correct (it's the destination printer's look), not a bug.

### Pattern 4: Per-printer webcam pref re-key (D-06)
**What:** `WebcamPrefs.preferredCamKey(host)` → `preferredCamKey(profileId)`. Same dynamic-key idiom, different key source. The reader/writer signatures change from `host: String` to `profileId: String`.
**Example (grounded in `WebcamPrefs.kt` lines 48–64):**
```kotlin
// WebcamPrefs.kt — change the key source only:
fun preferredCam(profileId: String): Flow<String?> = ...
suspend fun setPreferredCam(profileId: String, camId: String) = ...
companion object {
    fun preferredCamKey(profileId: String) = stringPreferencesKey("preferred_cam_$profileId")
}
```
**D-07 confirms no migration:** the old `preferred_cam_<host>` entries simply stop being read — they're orphaned in `webcam.preferences_pb` but harmless (never garbage-collected, never crash). The 10-06 holder caller (`AppShell`/webcam holder) must thread the **active profile id** instead of the host. The active profile id is available in `AppContainer` off `activeProfile.map { it?.id }`. **[VERIFIED: `WebcamPrefs.kt` is a clean dynamic-key store — re-keying is a mechanical caller change]**

### Anti-Patterns to Avoid
- **A second rebind path.** Do NOT add a "switch printer" code path in the service or a manual `publishSpine` from the UI. Switching is ONLY "make `activeConfig` emit a different value." The `runConfigLoop` seam does the rest. (CONTEXT.md, explicit.)
- **Persisting a resolved `ThemeTokens` in a profile.** `ThemeTokens` carries baked sRGB `Color`s and is Compose-typed (`@Immutable`). Persist the **primitives** (`ThemeBase.name`, `FontScale.name`, `Role.name → ARGB Long`) exactly as `ThemePrefs` does, and resolve at read time. Reuse `ThemePrefs.sanitize` parity so corrupt theme data fails safe (`ThemePrefs.kt` lines 103–124). **[VERIFIED: read `ThemeTokens.kt` / `ThemePrefs.kt`]**
- **Letting a theme/name edit churn the spine.** Without `distinctUntilChanged` on `activeConfig`, editing the active profile's theme or name reconnects the printer. Must be guarded (Pattern 2).
- **Computing D-12 auto-pick in the read derivation.** Keep `activeProfile` derivation pure (null if id absent). Auto-pick belongs in the `delete` writer.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Clean spine teardown on switch | A "disconnect-then-reconnect" sequencer | `MoonrakerService.runConfigLoop` (existing) | It already `cancelAndJoin()`s before rebuild — JVM-tested in `MoonrakerServiceTest`. Re-implementing it leaks sockets. |
| Atomic no-stale-state across switch | Manual nulling of per-screen state | `SpineHandle` atomic republish (existing) | The whole handle swaps in one `StateFlow` assignment — "a partially-swapped mix is unrepresentable" (`SpineHandle.kt` KDoc). |
| Fail-safe corrupt-blob recovery | try/catch around JSON parse with custom defaults | The `.catch { IOException → emptyPreferences() }` + pure `sanitize` idiom | 4 stores already do this identically; `sanitize` is host-testable with no IO. |
| Theme triple application | Three separate `setBase`/`setDeltas`/`setFs` calls (3 re-emits, flicker) | `ThemeResolver.apply(base, deltas, fs)` | Applies all three in ONE re-emit (`ThemeResolver.kt:114`). |
| Stable profile identity | A timestamp or counter id | `java.util.UUID.randomUUID()` | Self-contained, collision-safe, no persisted "next id." |

**Key insight:** The phase's correctness is *structurally* guaranteed by reusing two existing invariants — the `runConfigLoop` cancel-before-rebuild and the `SpineHandle` atomic republish. The risk is NOT in the rebind (proven); it's in the **derivation** (does `activeConfig` emit the right thing, does `distinctUntilChanged` correctly suppress non-connection edits) and the **theme re-seed** (does the new theme land before the Shell repaints). Those are where the validation effort goes.

## Runtime State Inventory

> Phase 14 generalizes a persisted model and is a rename-adjacent / re-key phase (D-06). Inventory required.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `connection.preferences_pb` (old single host/port/api_key keys); `webcam.preferences_pb` (`preferred_cam_<host>` entries). **D-07 = no migration** — old connection keys simply unread; old webcam keys orphaned. **NEW:** `profiles.preferences_pb` (the profile blob + active-id). | New file added to `DinghyApp`; old data intentionally abandoned (D-07). No data-migration task. |
| Live service config | None — Moonraker is server-side; switching only changes which printer the tablet watches (D-04). No remote config carries the renamed string. | None — verified by D-04 (server untouched). |
| OS-registered state | None — no Task Scheduler / systemd / pm2 equivalents. The foreground service registration is unchanged (one FGS, config-driven). | None — verified: `MoonrakerService` is `START_STICKY`, single instance, config-flow driven. |
| Secrets/env vars | `apiKey` moves from a single `ConnectionStore` field into each `PersistedProfile`. **Redaction must be preserved** — `ConnectionConfig.toString()` already redacts (`***`); ensure `PersistedProfile` does NOT log the key. The blob is in a Preferences file (not secret-encrypted today, same posture as `ConnectionStore`). | New code-path review: confirm `PersistedProfile.toString()` redacts `apiKey`; confirm the profile blob is never logged. T-04-01-I boundary moves from `connection.preferences_pb` to `profiles.preferences_pb`. |
| Build artifacts / installed packages | None — no egg-info / compiled artifact carries a renamed string; this is source-only + a new DataStore file. | None — verified (Android/Gradle, no generated-name artifacts). |

**Security note for the planner:** the API key now lives in the profile blob. `ConnectionConfig`'s redacting `toString()` (line 26–27) still protects the key once derived, but **`PersistedProfile` is new and must get the same redaction treatment** (override `toString()` to mask `apiKey`). Add this to the V5/V6 security checklist (Security Domain below).

## Common Pitfalls

### Pitfall 1: Spine churn on non-connection edits
**What goes wrong:** Renaming the active printer or recoloring its theme reconnects the printer (a visible Splash + dropped state) because `activeConfig` re-emits.
**Why it happens:** `combine(profiles, activeId)` re-emits whenever the profile *object* changes (name/theme are part of it), and a naive `map` forwards every emission.
**How to avoid:** `distinctUntilChanged` AFTER mapping to `ConnectionConfig` (Pattern 2) — equality is structural over host/port/apiKey only, so name/theme edits are suppressed.
**Warning signs:** On-device, editing the active printer's accent color triggers a reconnect Splash.

### Pitfall 2: Theme persisted as resolved tokens
**What goes wrong:** A profile stores `ThemeTokens` (baked Colors) and can't fail-safe-recover or re-resolve on a base change.
**Why it happens:** Reaching for the live `themeResolver.tokens.value` instead of the persisted primitives.
**How to avoid:** Store `ThemeBase.name` + `FontScale.name` + `Role→ARGB Long` (the `ThemePrefs` persisted shape); reuse `ThemePrefs.sanitize` parity at read.

### Pitfall 3: DataStore single-writer violation
**What goes wrong:** Constructing a second `DataStore` instance on `profiles.preferences_pb` crashes (DataStore enforces one instance per file per process).
**Why it happens:** Building the store somewhere other than `DinghyApp`.
**How to avoid:** Create the `profiles.preferences_pb` `DataStore` ONCE in `DinghyApp.onCreate` via `PreferenceDataStoreFactory.create` (the established pattern, `DinghyApp.kt` lines 40–64) and inject it into `AppContainer` → `ProfileStore`. **[VERIFIED: read `DinghyApp.kt`]**

### Pitfall 4: Dangling active-id after delete
**What goes wrong:** Deleting the active profile leaves `active_id` pointing at a gone profile → `activeProfile` resolves null → unexpected Connect prompt while other printers exist (violates D-12).
**Why it happens:** Delete removes the profile but doesn't fix `active_id`.
**How to avoid:** The `delete` writer auto-picks the first remaining profile as active (or clears active-id if it was the last). Test this in the pure store-writer (it's deterministic).

### Pitfall 5: Webcam holder still keyed on host
**What goes wrong:** Two same-host profiles (D-05 allows it) share a preferred-cam because the holder still keys on host.
**Why it happens:** D-06 re-key missed at the *caller* (the holder), even though `WebcamPrefs` was changed.
**How to avoid:** Thread the active **profile id** (off `AppContainer.activeProfile.map { it?.id }`) into the webcam holder, not the host.

## Code Examples

### Adding the DataStore file in DinghyApp (mirrors the 4 existing files)
```kotlin
// Source: DinghyApp.kt lines 40-64 pattern (read 2026-06-04)
val profileDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = appScope,
    produceFile = { applicationContext.preferencesDataStoreFile("profiles.preferences_pb") },
)
container = AppContainer(
    /* existing */ themeDataStore, connectionDataStore, macroDataStore, webcamDataStore,
    profileDataStore = profileDataStore,   // NEW
    discovery = ...,
)
```

### Wiring the Devices drawer tile LIVE with the active-name subtitle (D-01/D-03)
```kotlin
// Source: AppDrawer.kt lines 167 + the webcam/spool runtime-greying precedent
// 1. In DRAWER_TILES, flip the Devices tile dest:
DrawerTileSpec(label = "Devices", symbol = "cable", dest = Dest.Devices),  // was dest = null
// 2. Thread the active-printer name in (the SAME shape webcamEnabled/spoolEnabled use, AppShell lines 181/240):
//    val activeName by container.activeProfile.map { it?.displayName }.collectAsStateWithLifecycle(null)
// 3. DrawerTile renders an optional 15sp subtitle under the "Devices" label when name != null (UI-SPEC).
```
The icon-no-repeat law is satisfied: `cable` is unique among `DRAWER_TILES` glyphs (verified against the list in `AppDrawer.kt` lines 137–170). The new Devices *screen's* profile-tile glyph (e.g. `print`/`dns`) must not repeat the screen's gutter glyph — a per-screen check, not a drawer check.

### Suppressing the swipe-drawer on the scrollable Devices screen
```kotlin
// Source: AppShell.kt line 420 — add Dest.Devices to the suppression set:
dest !in setOf(Dest.Files, Dest.Console, Dest.Macros, Dest.Calibration, Dest.Webcam, Dest.Spool, Dest.Devices)
// The Devices screen is a scrollable Field, so per LAYOUT.md it must carry an explicit Gutter Back
// (green Intent.Go) and suppress the swipe-up (the vertical drag fights the list scroll). UI-SPEC §New surface.
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single global `ConnectionStore.config` → service | `combine(profiles, activeId)` → `activeConfig` → service | This phase | Service unchanged; selection logic centralizes in `AppContainer` |
| Process-scoped global `ThemePrefs` seeds resolver forever | Active-profile theme re-seeds resolver per switch; global = new-profile default | This phase | First connection-DEPENDENT pref; `seedTheme` rewired |
| `preferred_cam_<host>` | `preferred_cam_<profileId>` | This phase (D-06) | Two same-host profiles keep distinct cams; old keys orphaned (D-07) |

**Deprecated/outdated by this phase (but NOT deleted):**
- The old `connection.preferences_pb` keys (host/port/api_key) — unread after this phase (D-07), but the file/store can remain for a clean revert. Planner's call whether to leave `ConnectionStore` in the tree as dead code or remove it; recommend **leave it** until the phase is verified, then a follow-up cleanup (its `sanitize` parity is reused conceptually by `ProfileStore`).

## Validation Architecture

> nyquist_validation is enabled (`config.json: workflow.nyquist_validation: true`). This section drives VALIDATION.md.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 + `kotlinx-coroutines-test` (virtual time) for host unit tests; AndroidX instrumented tests (`androidTest`) for on-device |
| Config file | Gradle `app/build.gradle.kts` (existing test/androidTest source sets) |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileStoreTest"` (list test CLASSES explicitly — the AGP glob `--tests 'works.mees.dinghy.*'` false-fails per MEMORY) |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` (pipe through `tr -d '\r'`; exit code authoritative) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MULTI-01 | `ProfileStore.sanitize` drops malformed entries, recovers from corrupt blob, empty → empty list | unit (host, pure) | `gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileStoreTest` | ❌ Wave 0 |
| MULTI-01 | `delete` writer auto-picks another active (D-12); deleting last → active-id cleared | unit (host) | same class | ❌ Wave 0 |
| MULTI-01 | `activeConfig` derivation: combine→pick→map→distinctUntilChanged; a name/theme-only edit does NOT re-emit a new `ConnectionConfig` (Pitfall 1) | unit (host, virtual time) | `--tests works.mees.dinghy.di.ActiveConfigDerivationTest` | ❌ Wave 0 |
| MULTI-01 | Theme triple derivation: active-profile change → correct `(base, deltas, fs)` applied; corrupt theme primitives fail safe to default | unit (host) | `--tests works.mees.dinghy.theme.ProfileThemeSeedTest` | ❌ Wave 0 |
| MULTI-01 | `runConfigLoop` cancels prior session before rebuild on a config change (the switch seam) | unit (host, EXISTING) | `--tests works.mees.dinghy.service.MoonrakerServiceTest` | ✅ exists |
| MULTI-01 | Switch survives process death: active-id persisted, re-read on cold start lands on the right printer | instrumented (on-device) | `connectedDebugAndroidTest` (a `ProfileSurvivesRestartTest` mirroring `ServiceSurvivesRotationTest`) | ❌ Wave 0 |
| MULTI-01 | **Live two-printer switch** (the success criterion): E5+ ↔ E3, drive a control on each without re-entering details | manual on-device | hands-on UAT on `flox` + live printers (192.168.1.120:7125 / .121:7125) | n/a (manual gate) |

### Sampling Rate
- **Per task commit:** the relevant Wave-0 unit class (`ProfileStoreTest` / `ActiveConfigDerivationTest` / `ProfileThemeSeedTest`).
- **Per wave merge:** full `:app:testDebugUnitTest`.
- **Phase gate:** full unit suite green + the instrumented survival test + the **live two-printer hands-on UAT** before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt` — pure `sanitize` + `upsert`/`delete`/D-12 auto-pick (RED scaffold must compile day-one per MEMORY [[dinghy-wave0-red-scaffold-compile]] — use typed `fail()`/assertion stubs, no refs to unbuilt symbols beyond the new types).
- [ ] `app/src/test/java/works/mees/dinghy/di/ActiveConfigDerivationTest.kt` — `combine`+`distinctUntilChanged` proves a name/theme edit does NOT churn the config (Pitfall 1).
- [ ] `app/src/test/java/works/mees/dinghy/theme/ProfileThemeSeedTest.kt` — active-profile → theme triple, fail-safe on corrupt primitives.
- [ ] `app/src/androidTest/java/works/mees/dinghy/service/ProfileSurvivesRestartTest.kt` — active-id survives process death (mirror `ServiceSurvivesRotationTest`).
- [ ] No new framework install needed — JUnit4 + coroutines-test + AndroidX test already wired.

### Where a Fake would LIE vs the real Moonraker/DataStore (the documented mock-vs-reality risk)
This project has a **documented history of green-unit-suite bugs that only on-device caught** (Phase 5 G1–G4, Phase 9 identify-`url`, Phase 11 measured-weight). Call out for the planner:
1. **DataStore single-writer / file persistence is NOT host-faithful.** Per `WebcamPrefs`/`MacroPrefs` notes, "back-to-back writes / a second instance on one `.preferences_pb` are not reliably host-testable on the Windows build host." So the **survival-across-process-death** criterion CANNOT be proven by a unit test with a fake DataStore — it needs the **instrumented** `ProfileSurvivesRestartTest` (real DataStore file, real cold start). A `FakeProfileStore` (in-memory) will happily round-trip and lie about persistence.
2. **The actual rebind / no-stale-state is only fully real on-device.** `runConfigLoop` is unit-proven for *cancellation ordering*, but "every screen reflects the NEW printer, capability detection re-runs, command registry rebinds" emerges from the real `SpineHandle` republish against a live Moonraker (capabilities are server-derived). A fake session that publishes a canned handle won't catch a stale-capability bug. → **live two-printer UAT is mandatory** (success criterion 4).
3. **`distinctUntilChanged` suppression vs a real flow.** A fake that emits configs synchronously may mask a subtle re-emit; verify on-device that editing the active theme does NOT reconnect (Pitfall 1) — eyeball: no Splash on an accent change.

## Open Questions

1. **Devices list live per-printer status (CONTEXT discretion).**
   - What we know: the spine only knows the ACTIVE printer's `ConnectionState`. Showing connected/last-seen for INACTIVE printers needs background probing (a deferred idea).
   - What's unclear: whether v1 shows the active tile's live color (cheap — the active `connectionState` is already in the container) while inactive tiles stay neutral.
   - Recommendation: **v1 = name/address only + active-tile accent emphasis** (D-03). Encode the *active* printer's connection state as color-on-the-active-tile if cheap (it's one already-collected flow), but do NOT probe inactive printers. Matches UI-SPEC ("the spine only knows the ACTIVE printer's state").

2. **Keep or remove `ConnectionStore` after this phase.**
   - What we know: D-07 means its keys are unread; `ProfileStore` supersedes it.
   - Recommendation: **leave it in the tree until the phase is verified**, then a follow-up cleanup todo. Removing it now risks a flag-day refactor mid-phase. Its `sanitize` is a useful reference for `ProfileStore.sanitize`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android build toolchain (`gw.bat`) | All compilation/tests | ✓ | JDK 21 / AGP 8.7.x / SDK android-35 | — |
| `flox` test device | On-device survival + UAT | ✓ | LineageOS 18.1 / API 30, genuine Adreno 320 | — |
| Ender 5 Plus Moonraker | Live two-printer UAT | ✓ | 192.168.1.120:7125 | — |
| Ender 3 Pro Moonraker | Live two-printer UAT | ✓ | 192.168.1.121:7125 | — |
| `java.util.UUID` | Profile id (D-05) | ✓ | core JDK (API 1+) | — |

**No missing dependencies.** Everything needed is in the existing build env + the two live printers.

## Security Domain

> `security_enforcement: true`, ASVS level 1. The phase moves the API key into the profile blob.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Moonraker API key / trusted-client — unchanged mechanism, now per-profile. Key still optional. |
| V3 Session Management | no | No app-level sessions; the Moonraker socket session is server-owned. |
| V4 Access Control | no | Local-network, single-user device. |
| V5 Input Validation | yes | `ProfileStore.sanitize` (host trim/blank-reject, port range 1–65535) — copy `ConnectionStore.sanitize` parity. Reject malformed entries from the decoded blob. |
| V6 Cryptography | partial | No new crypto. The key is stored in a Preferences DataStore (same posture as today's `ConnectionStore` — not encrypted-at-rest). Do NOT introduce hand-rolled crypto; if encryption-at-rest is ever wanted that's its own deferred decision. |
| V7 Logging (redaction) | yes | The API key must NEVER reach a log line / notification / crash dump. `ConnectionConfig.toString()` already redacts; **`PersistedProfile` must add a redacting `toString()`** and the profile blob must never be logged. (T-04-01-I boundary moves to `profiles.preferences_pb`.) |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| API key leaks via `PersistedProfile.toString()` / logged blob | Information disclosure | Redacting `toString()` (mask `apiKey` → `***`, mirror `ConnectionConfig.kt:26`); never log the blob |
| Corrupt/tampered profile blob crashes the printer display | Denial of service | Fail-safe `.catch { IOException → empty }` + pure `sanitize` drops junk entries; empty → Connect prompt, never a black screen (the `ThemePrefs`/`ConnectionStore` fail-safe contract) |
| Malformed host/port drives the spine | Tampering | `sanitize` rejects out-of-range port / blank host BEFORE it can become an active `ConnectionConfig` (the `ConnectionStore.sanitize` discipline, lines 79–88) |

## Sources

### Primary (HIGH confidence — read this session)
- `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt` — `runConfigLoop` rebind seam, `buildSpineAndLaunch`, `onCreate` config-flow wiring (the UNCHANGED service)
- `app/src/main/java/works/mees/dinghy/config/ConnectionStore.kt` — the store shape to mirror (injected DataStore, fail-safe read, pure `sanitize`)
- `app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt` — the immutable config (`data class`, redacting `toString`) the profile wraps
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — the derivation site (`hasConfig`, `flatMapLatest`/`map` idiom, `seedTheme`)
- `app/src/main/java/works/mees/dinghy/di/SpineHandle.kt` — the atomic-republish no-stale-state guarantee
- `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt` — `apply(base,deltas,fs)` one-re-emit primitive; pure `resolve`
- `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt` — the persisted-primitives shape + fail-safe `sanitize` to reuse for per-profile theme
- `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt` — why NOT to persist resolved tokens (baked Colors, Compose-typed)
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt` — the dynamic-key idiom to re-key (D-06)
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` — `Dest` enum (+`Devices`) + pure `derive` (the Splash-on-switch path)
- `app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt` — `hasConfig`-driven routing + recovery-Splash dwell
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` — the `DRAWER_TILES`/Devices tile + runtime-greying precedent + icon-no-repeat
- `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt` — the connection form + Appearance controls to retarget (D-09/D-13)
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt` — the DataStore-file creation pattern (one instance per file per process)
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — drawer-swipe-suppression set + `webcamEnabled`/`spoolEnabled` threading precedent for the active-name
- `app/src/test/java/works/mees/dinghy/service/MoonrakerServiceTest.kt` — the virtual-time seam-test pattern to mirror
- `.planning/config.json` — `nyquist_validation: true`, `security_enforcement: true`, ASVS 1
- `.planning/phases/14-multi-printer-switching/14-CONTEXT.md` + `14-UI-SPEC.md` — locked decisions D-01..D-14

### Secondary (MEDIUM confidence)
- Project MEMORY.md — AGP glob false-fail (list test classes explicitly); Wave-0 RED-scaffold-must-compile; mock-vs-reality history; build via `gw.bat`; printer addresses

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new deps; every library already in the project and read in use
- Architecture (derivation + rebind reuse): HIGH — grounded in the actual `runConfigLoop`/`AppContainer`/`ThemeResolver` source; the service is provably unchanged
- Per-printer theme re-seed: HIGH — `ThemeResolver.apply` is the exact primitive; flicker is covered by the existing Splash
- Pitfalls: HIGH — derived from the read code's invariants and the project's documented mock-vs-reality history
- Validation: HIGH for what's unit-testable; the live two-printer UAT is correctly flagged as the irreplaceable gate

**Research date:** 2026-06-04
**Valid until:** 2026-07-04 (stable — internal codebase, no fast-moving external deps)
