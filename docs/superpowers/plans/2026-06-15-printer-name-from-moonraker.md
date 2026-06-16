# Seed printer-profile name from the Moonraker hostname — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a printer profile has no manual name, auto-seed its `name` from the Moonraker hostname (`printer.info.hostname`, e.g. `ender5plus`) exactly once on connect, so an un-named profile is labeled by its hostname instead of its IP.

**Architecture:** Add a `printer.info` one-shot to the handshake; parse `result.hostname`; surface it as a `hostname` StateFlow on the spine (mirroring the existing `machine.system_info`→`SystemInfo` one-shot path); `AppContainer` performs a one-time, race-guarded, durable seed of the active profile via `ProfileStore.mutateActive` with the predicate INSIDE the transform. A new `nameAutoSeeded` flag makes it strictly once (so a deliberately-cleared name stays blank).

**Tech Stack:** Kotlin, kotlinx.serialization, Coroutines/Flow, DataStore, JUnit host tests. Spec: `docs/superpowers/specs/2026-06-15-printer-name-from-moonraker-design.md`.

**Build/test harness (builds Windows-side):**
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <gradle args>" 2>&1 | tr -d '\r'
```
Host tests: `:app:testDebugUnitTest`. Pass `--tests` class names BARE (no single quotes — they break through `cmd.exe /c`). Exit code / "BUILD SUCCESSFUL" is authoritative. `./gradlew` does NOT work from WSL. On-device UAT is manual on flox + moto.

---

## File Structure

**Phase 1 — Profile model + seed predicate**
- Modify: `app/src/main/java/works/mees/dinghy/config/Profile.kt` — `nameAutoSeeded` on both classes + both conversions.
- Create: `app/src/main/java/works/mees/dinghy/config/ProfileNameSeed.kt` — pure `shouldSeedName` + `resolveAutoSeededOnSave`.
- Test: `app/src/test/java/works/mees/dinghy/config/ProfileNameSeedTest.kt`.

**Phase 2 — `printer.info` command + catalog/matrix**
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` — `printerInfo` CommandSpec + add to `all`.
- Modify: `docs/commands/catalog.json` — flip `MR-printer.info` to registered.
- Modify: `docs/commands/printer-matrix.json` — add `MR-printer.info` `command_availability` row.

**Phase 3 — hostname fetch → store → spine → container**
- Create: `app/src/main/java/works/mees/dinghy/state/HostnameParse.kt` — pure `parsePrinterInfoHostname`.
- Test: `app/src/test/java/works/mees/dinghy/state/HostnameParseTest.kt`.
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — `_hostname`/`setHostname`/`hostname`.
- Modify: `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` — fetch `printer.info` in handshake.
- Modify: `app/src/main/java/works/mees/dinghy/di/SpineHandle.kt` — `hostname` StateFlow + `sessionConfig`.
- Modify: `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt` — forward `store.hostname`, tag `sessionConfig = cfg`.
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — `hostname` flow.
- Modify: `app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt` — add `printer.info` to method list.
- Modify: `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt` — canned `printer.info` reply.

**Phase 4 — the one-time seed + editor flag**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — seed collector in `init {}`.
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt` — set `nameAutoSeeded` on save.

**Phase 5 — full gate + UAT.**

---

## Phase 1: Profile model + seed predicate

### Task 1: Add `nameAutoSeeded` to the Profile model + the pure helpers (TDD)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/config/ProfileNameSeed.kt`
- Test: `app/src/test/java/works/mees/dinghy/config/ProfileNameSeedTest.kt`
- Modify: `app/src/main/java/works/mees/dinghy/config/Profile.kt`

- [ ] **Step 1: Write the failing test.** Create `ProfileNameSeedTest.kt`:

```kotlin
package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNameSeedTest {
    @Test fun seeds_when_unnamed_unseeded_and_hostname_present() {
        assertTrue(shouldSeedName(currentName = null, nameAutoSeeded = false, hostname = "ender5plus"))
        assertTrue(shouldSeedName(currentName = "   ", nameAutoSeeded = false, hostname = "ender3"))
    }
    @Test fun no_seed_when_already_named() {
        assertFalse(shouldSeedName(currentName = "My E5+", nameAutoSeeded = false, hostname = "ender5plus"))
    }
    @Test fun no_seed_when_already_seeded() {
        assertFalse(shouldSeedName(currentName = null, nameAutoSeeded = true, hostname = "ender5plus"))
    }
    @Test fun no_seed_when_hostname_blank_or_null() {
        assertFalse(shouldSeedName(currentName = null, nameAutoSeeded = false, hostname = null))
        assertFalse(shouldSeedName(currentName = null, nameAutoSeeded = false, hostname = "   "))
    }
    @Test fun editor_save_flag_logic() {
        // new IP-only add (no prior name, blank) → still auto-seedable on first connect
        assertFalse(resolveAutoSeededOnSave(cleanName = null, prior = false, priorName = null))
        // typed a name on add → lock auto-seed off
        assertTrue(resolveAutoSeededOnSave(cleanName = "Foo", prior = false, priorName = null))
        // EDIT a previously-named profile, clearing the name → lock (stays blank, no re-seed) — B3
        assertTrue(resolveAutoSeededOnSave(cleanName = null, prior = false, priorName = "Foo"))
        // already locked (e.g. auto-seeded) and cleared → stays locked
        assertTrue(resolveAutoSeededOnSave(cleanName = null, prior = true, priorName = null))
    }
    @Test fun nameAutoSeeded_round_trips_and_defaults_false() {
        assertFalse(Profile(id = "a", host = "h").nameAutoSeeded)
        assertFalse(PersistedProfile(id = "a", host = "h").nameAutoSeeded)
        val p = Profile(id = "a", host = "h", name = "ender5plus", nameAutoSeeded = true)
        val round = Profile.fromPersisted(p.toPersisted())
        assertTrue(round.nameAutoSeeded)
        assertEquals("ender5plus", round.name)
    }
}
```

- [ ] **Step 2: Run to verify it fails.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileNameSeedTest" 2>&1 | tr -d '\r'`
Expected: FAIL to compile — `shouldSeedName`/`resolveAutoSeededOnSave`/`nameAutoSeeded` unresolved.

- [ ] **Step 3: Create `ProfileNameSeed.kt`:**

```kotlin
package works.mees.dinghy.config

/**
 * One-time hostname-seed gate (2026-06-15): seed a profile's name from the Moonraker hostname ONLY
 * when it is un-named, not yet auto-seeded, and a real hostname is present. Pure → host-testable;
 * applied INSIDE the [ProfileStore.mutateActive] transform against the freshly-decoded profile.
 */
fun shouldSeedName(currentName: String?, nameAutoSeeded: Boolean, hostname: String?): Boolean =
    !nameAutoSeeded && currentName.isNullOrBlank() && !hostname.isNullOrBlank()

/**
 * The [Profile.nameAutoSeeded] value to persist when the connection editor SAVES. Lock auto-seed off
 * (return true) when EITHER the user is saving a non-blank name, OR the profile already had a name
 * (`priorName` non-blank — so clearing a previously-named profile stays blank), OR it was already
 * locked. A brand-new IP-only profile (blank name, no prior name, not yet seeded) returns false and
 * still auto-seeds on first connect. `priorName` is the profile's name BEFORE this save (null for a
 * new profile) — closes the pre-feature "had a name, cleared it → re-seeds" hole (Codex B3).
 */
fun resolveAutoSeededOnSave(cleanName: String?, prior: Boolean, priorName: String?): Boolean =
    prior || !cleanName.isNullOrBlank() || !priorName.isNullOrBlank()
```

- [ ] **Step 4: Add `nameAutoSeeded` to `Profile.kt`.** In `PersistedProfile` (after `useSecure`, ~line 51) add `val nameAutoSeeded: Boolean = false,`. In `Profile` (after `useSecure`, ~line 83) add `val nameAutoSeeded: Boolean = false,`. In `toPersisted()` add `nameAutoSeeded = nameAutoSeeded,`. In `fromPersisted()` add `nameAutoSeeded = p.nameAutoSeeded,`. (All existing call sites construct by-name with defaults — zero blast radius, verified.)

- [ ] **Step 5: Run to verify it passes.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileNameSeedTest" 2>&1 | tr -d '\r'`
Expected: PASS.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/config/Profile.kt app/src/main/java/works/mees/dinghy/config/ProfileNameSeed.kt app/src/test/java/works/mees/dinghy/config/ProfileNameSeedTest.kt
git commit -m "feat(profile): nameAutoSeeded flag + shouldSeedName/resolveAutoSeededOnSave helpers"
```

---

## Phase 2: `printer.info` command + catalog/matrix

### Task 2: Register the `printer.info` CommandSpec and satisfy the drift test

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt`
- Modify: `docs/commands/catalog.json`
- Modify: `docs/commands/printer-matrix.json`
- Test: `app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt` (existing — must stay green)

- [ ] **Step 1: Add the CommandSpec.** In `CommandRegistry.kt`, beside `machineSystemInfo` (~line 388), add:

```kotlin
    /** `printer.info` — host identity incl. `hostname` (used to seed an un-named profile's name). One-shot. */
    val printerInfo: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.info",
        method = "printer.info",
        key = { "printer_info" },
        params = { null },
    )
```

- [ ] **Step 2: Add it to `CommandRegistry.all`.** In the `val all = listOf(...)` block, add `printerInfo,` next to `machineSystemInfo,` / `machineProcStats,`.

- [ ] **Step 3: Flip the catalog entry (documentation accuracy — NOT test-gating).** Per Codex S5, `CommandCatalogDriftTest` only checks that the catalogId EXISTS in `catalog.json` (it already does, as `reference_only`) and that a matrix row exists (Step 4 — the test-critical edit); it does NOT assert `runtime_registry.registered`. Flip it anyway so the catalog truthfully reflects that it's now a registered runtime command. In `docs/commands/catalog.json`, find the `MR-printer.info` entry's `runtime_registry` block (~line 7007) and change:

```json
      "runtime_registry": {
        "status": "reference_only",
        "registered": false,
        "notes": "Official command/API surface cataloged for reference only; Dinghy v1 does not send it yet."
      },
```
to:
```json
      "runtime_registry": {
        "status": "registered",
        "registered": true,
        "notes": "Registered runtime CommandSpec in CommandRegistry (2026-06-15) — one-shot read of hostname to seed an un-named profile's name."
      },
```

- [ ] **Step 4: Add the matrix row.** In `docs/commands/printer-matrix.json`, after the `MR-machine.system_info` `command_availability` object, add:

```json
    {
      "catalog_id": "MR-printer.info",
      "name": "printer.info",
      "source_api": "moonraker",
      "transport": "json_rpc",
      "registry_status": "registered",
      "predicate": {
        "type": "always"
      },
      "predicate_key": "always",
      "printer_status": [
        {
          "printer_id": "ender5plus",
          "status": "present",
          "evidence": "GET /printer/info probed 2026-06-15; result.hostname='ender5plus' (state 'ready')."
        },
        {
          "printer_id": "ender3",
          "status": "present",
          "evidence": "GET /printer/info probed 2026-06-15; result.hostname='ender3' (state 'ready')."
        }
      ]
    },
```

- [ ] **Step 5: Run the drift test + a JSON sanity build.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.command.CommandCatalogDriftTest" 2>&1 | tr -d '\r'`
Expected: PASS (catalogId present in catalog.json as registered; matrix availability row present). If it complains about JSON parse, you broke a comma/brace — fix it.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt docs/commands/catalog.json docs/commands/printer-matrix.json
git commit -m "feat(command): register printer.info one-shot (catalog + matrix rows)"
```

---

## Phase 3: hostname fetch → store → spine → container

### Task 3: Hostname parse helper (TDD)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/state/HostnameParse.kt`
- Test: `app/src/test/java/works/mees/dinghy/state/HostnameParseTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package works.mees.dinghy.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostnameParseTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
    @Test fun extracts_hostname() {
        assertEquals("ender5plus", parsePrinterInfoHostname(obj("""{"hostname":"ender5plus","state":"ready"}""")))
    }
    @Test fun trims_and_blanks_to_null() {
        assertEquals("ender3", parsePrinterInfoHostname(obj("""{"hostname":"  ender3 "}""")))
        assertNull(parsePrinterInfoHostname(obj("""{"hostname":"   "}""")))
    }
    @Test fun missing_or_malformed_is_null() {
        assertNull(parsePrinterInfoHostname(obj("""{"state":"ready"}""")))
        assertNull(parsePrinterInfoHostname(null))
    }
    @Test fun non_string_hostname_is_null() {
        // a number/bool must NOT become a printer name (Codex S4)
        assertNull(parsePrinterInfoHostname(obj("""{"hostname":12345}""")))
        assertNull(parsePrinterInfoHostname(obj("""{"hostname":true}""")))
    }
}
```

- [ ] **Step 2: Run to verify it fails.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.state.HostnameParseTest" 2>&1 | tr -d '\r'`
Expected: FAIL — `parsePrinterInfoHostname` unresolved.

- [ ] **Step 3: Create `HostnameParse.kt`:**

```kotlin
package works.mees.dinghy.state

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.isString
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pull the trimmed `hostname` out of a Moonraker `printer.info` RESULT object (the object the rpc
 * layer returns — already unwrapped from the JSON-RPC envelope, like `machine.system_info`). A
 * non-string primitive (number/bool) is rejected so it can never become a printer name (the
 * `SystemInfoParse` `isString` convention). Blank or missing → null. Fail-safe (`runCatching`) so a
 * malformed payload never crashes the handshake.
 */
fun parsePrinterInfoHostname(result: JsonObject?): String? = runCatching {
    val prim = result?.get("hostname")?.jsonPrimitive ?: return@runCatching null
    if (!prim.isString) return@runCatching null
    prim.content.trim().ifBlank { null }
}.getOrNull()
```

- [ ] **Step 4: Run to verify it passes.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.state.HostnameParseTest" 2>&1 | tr -d '\r'`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/state/HostnameParse.kt app/src/test/java/works/mees/dinghy/state/HostnameParseTest.kt
git commit -m "feat(state): parsePrinterInfoHostname helper"
```

### Task 4: Store field + spine + service + container flow + handshake fetch

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt`
- Modify: `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt`
- Modify: `app/src/main/java/works/mees/dinghy/di/SpineHandle.kt`
- Modify: `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt`
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`
- Modify: `app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt`
- Modify: `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt`

- [ ] **Step 1: `PrinterStateStore` — add the hostname seam.** Beside the `systemInfo` field (~line 94) add:

```kotlin
    private val _hostname = MutableStateFlow<String?>(null)
    /** `printer.info` hostname (2026-06-15) — one-shot per handshake, NOT the hot path. Null until read. */
    val hostname: StateFlow<String?> = _hostname.asStateFlow()
```
Beside `setSystemInfo` (~line 329) add:
```kotlin
    /** One-shot at (re)handshake: the `printer.info` hostname (used to seed an un-named profile's name). */
    fun setHostname(value: String?) {
        _hostname.value = value
    }
```

- [ ] **Step 2: `MoonrakerSession.runHandshake` — fetch `printer.info`.** IMMEDIATELY AFTER the existing `machine.system_info` `runCatching { ... }` block (~line 510), add a parallel best-effort block:

```kotlin
        runCatching {
            // printer.info: host identity incl. hostname (2026-06-15) — one-shot per handshake, beside
            // machine.system_info (which carries NO hostname). Best-effort: absent → null → no name seed.
            val infoResult = rpc.request(CommandRegistry.printerInfo, Unit)
            store.setHostname(parsePrinterInfoHostname(infoResult.jsonObject))
        }
```
Add imports if missing: `works.mees.dinghy.state.parsePrinterInfoHostname` (same package as the store? store is `state`; the session imports `SystemInfo.from` similarly — add the import).

- [ ] **Step 3: `SpineHandle` — forward hostname + carry the session's connection identity.** Beside the `systemInfo` member (~line 69) add (import `works.mees.dinghy.config.ConnectionConfig`):

```kotlin
    /**
     * `printer.info` hostname (2026-06-15) — forwarded off the store like [systemInfo]. Seeds an
     * un-named active profile's name once. Null-seeded default for headless test construction.
     */
    val hostname: StateFlow<String?> = MutableStateFlow(null).asStateFlow(),
    /**
     * The [ConnectionConfig] this live session was built for (the `cfg` passed to the spine build).
     * The name-seed verifies the still-active profile's `toConnectionConfig()` matches THIS before
     * writing (Codex B-2 guard) — no racy `activeProfileId.value` read. Null in headless tests.
     */
    val sessionConfig: ConnectionConfig? = null,
```

- [ ] **Step 4: `MoonrakerService.buildSpineAndLaunch` — wire both.** Where the `SpineHandle(...)` is constructed (the `publishSpine(handle)` site, ~line 234–237), pass `hostname = store.hostname` (mirroring `systemInfo = store.systemInfo`) and `sessionConfig = cfg` (the `ConnectionConfig` parameter `buildSpineAndLaunch` already receives — already in hand, no extra plumbing, no `activeProfileId` race).

- [ ] **Step 5: `AppContainer` — expose the hostname flow.** In the derived per-field convenience-flows section (beside `printerState`/`connectionState`/`capabilities`, which use this exact `spine.flatMapLatest` idiom, ~lines 593–605) add:

```kotlin
    /** Live `printer.info` hostname off the current session; null when idle. */
    val hostname: Flow<String?> =
        spine.flatMapLatest { it?.hostname ?: flowOf(null) }
```
(Note: AppContainer's system info is exposed via `systemInfoHolder`, not a `flatMapLatest` flow — so mirror the `printerState`/`capabilities` flows here, not a non-existent `systemInfo` flow.)

- [ ] **Step 6: Update `HandshakeTest`.** In the expected ordered method list (~line 66), insert `"printer.info",` immediately AFTER `"machine.system_info",` (the order the new fetch sends). Update the assertion message string to mention printer.info if it enumerates the reads.

- [ ] **Step 7: Update `SessionTestHarness`.** Add a configurable fixture beside `temperatureStoreResultJson` (~line 98):

```kotlin
    @Volatile
    var printerInfoResultJson: String = """{"hostname":"ender5plus","state":"ready"}"""
```
In the `replyFor` `when (method)` block (~line 219), add a branch (beside the other one-shots):
```kotlin
            "printer.info" ->
                """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(printerInfoResultJson)},"id":$id}"""
```

- [ ] **Step 8: Build + the touched tests.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --tests works.mees.dinghy.net.HandshakeTest --tests works.mees.dinghy.command.CommandCatalogDriftTest" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; HandshakeTest green (the method list now matches the new send order); drift test green.

- [ ] **Step 9: Commit.**

```bash
git add -A
git commit -m "feat(session): fetch printer.info hostname → spine hostname flow + sessionProfileId"
```

---

## Phase 4: the one-time seed + editor flag

### Task 5: Seed collector in AppContainer + editor flag on save

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt`

- [ ] **Step 1: Add the seed collector in a NEW `init {}` block placed AFTER the `spine` field declaration.** ⚠ Codex B-2: the existing `init` block (~line 448) runs BEFORE `spine` is initialized (~line 565), so a collector reading `spine` there races object init. Add a SEPARATE `init {}` block positioned LOW in the class — after `spine` AND the new `hostname` flow are declared (i.e. after ~line 605). Kotlin runs init blocks in declaration order interleaved with property initializers, so an init block below the `spine`/`hostname` fields sees them initialized.

```kotlin
    init {
        // One-time printer-name seed from the Moonraker hostname (printer.info, 2026-06-15). When a live
        // session reports a hostname, seed THAT session's active profile's name once — iff un-named and
        // not yet seeded. The sessionConfig guard prevents seeding the wrong profile across a switch
        // (Codex B-2): activeProfile (ProfileStore) can update before the spine republishes. Idempotent —
        // the predicate is re-checked against the freshly-decoded profile INSIDE ProfileStore.mutateActive.
        // Process-lifetime writeScope (never a composition scope). This init block sits AFTER the [spine]
        // and [hostname] declarations so they are initialized when it runs.
        writeScope.launch {
            spine.collectLatest { handle ->
                val sessionConfig = handle?.sessionConfig ?: return@collectLatest
                handle.hostname
                    .map { it?.trim()?.ifBlank { null } }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { hn ->
                        profileStore.mutateActive { p ->
                            if (p.toConnectionConfig() == sessionConfig &&
                                shouldSeedName(p.name, p.nameAutoSeeded, hn)
                            ) {
                                p.copy(name = hn, nameAutoSeeded = true)
                            } else {
                                p
                            }
                        }
                    }
            }
        }
    }
```
Add imports if missing: `kotlinx.coroutines.flow.collectLatest` (per Codex N6, `filterNotNull` is ALREADY imported at AppContainer.kt:22, and `map`/`distinctUntilChanged`/`flatMapLatest` are present); `works.mees.dinghy.config.shouldSeedName`.

- [ ] **Step 2: Set the flag in the editor save path.** In `PrinterConnectionEditor.kt`, the save block (~lines 296–315) computes `val cleanName = name.trim().ifBlank { null }`. Thread `nameAutoSeeded` into BOTH branches using the pure helper, passing the PRIOR name (Codex B3):

For the existing-profile `profile.copy(...)` add:
`nameAutoSeeded = resolveAutoSeededOnSave(cleanName, profile.nameAutoSeeded, profile.name),`
For the new-profile `Profile(...)` add (no prior name):
`nameAutoSeeded = resolveAutoSeededOnSave(cleanName, prior = false, priorName = null),`

Add import `works.mees.dinghy.config.resolveAutoSeededOnSave`.

- [ ] **Step 3: Build + full host suite** (the seed path has no cheap unit test in isolation — it's covered by the `shouldSeedName`/`resolveAutoSeededOnSave` unit tests from Task 1 plus on-device UAT; ensure nothing regressed):

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; all host tests pass.

- [ ] **Step 4: Commit.**

```bash
git add -A
git commit -m "feat(profile): one-time seed of profile name from Moonraker hostname + editor flag"
```

---

## Phase 5: full gate + on-device UAT

### Task 6: R8 + two-device UAT

- [ ] **Step 1: Full host suite + R8 release.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleRelease --rerun-tasks" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; all tests green.

- [ ] **Step 2: On-device UAT — BOTH devices (flox + moto), matching ABI slice each.** Verify:
  - **Add a printer by IP with NO name** (E5+ `192.168.1.120` / E3 `192.168.1.121`) → connect → the profile label becomes the hostname (`ender5plus` / `ender3`); the `host:port` line still shows the IP.
  - **Rename** it to something custom → the rename sticks across a reconnect (not overwritten by the hostname).
  - **Clear the name** → it stays blank across a reconnect (no re-seed; falls back to host).
  - **Switch between two profiles** while connecting → each gets only its OWN hostname (no cross-seed).
  - An existing already-named profile is untouched.

- [ ] **Step 3: Final commit if UAT required tweaks.**

```bash
git add -A && git commit -m "feat(profile): hostname-seed — on-device UAT pass" || true
```

---

## Self-Review

**Spec coverage:** hostname source = `printer.info` (Task 2/4) ✓; one-time seed gated by `shouldSeedName` (Task 1/5) ✓; `nameAutoSeeded` on both classes + both conversions (Task 1) ✓; B-2 session guard via `sessionConfig` match — no racy `activeProfileId` read (Task 4/5) ✓; seed collector in an init block AFTER `spine` is declared (Task 5, Codex B-2) ✓; predicate INSIDE the `mutateActive` transform, not in `mutateActive` (Task 5) ✓; editor sets the flag on manual naming, with `priorName` to close the clear-after-naming hole (Task 5, Codex B3) ✓; parse rejects non-string hostname (Task 3, Codex S4) ✓; matrix row is the drift-test-critical edit, catalog flip is doc-only (Task 2, Codex S5) ✓; HandshakeTest + SessionTestHarness updates (Task 4) ✓; decode-safe default + zero blast radius (Task 1) ✓.

**Placeholder scan:** none — every step has literal code or an exact edit.

**Type consistency:** `shouldSeedName(currentName, nameAutoSeeded, hostname)` and `resolveAutoSeededOnSave(cleanName, prior)` signatures match across Task 1 (def/test), Task 5 (call sites). `parsePrinterInfoHostname(JsonObject?)` consistent Task 3↔4. `hostname`/`setHostname`/`sessionProfileId` names consistent store→spine→service→container.
