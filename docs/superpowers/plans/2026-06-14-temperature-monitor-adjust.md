# Temperature Monitoring/Adjust Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the Temperature screen into footer-toggled **Monitoring** (default) and **Adjust** modes, add arbitrary `temperature_sensor` display via a Settings picker, and give the heater adjuster a scrubber scaled to each heater's real Moonraker `max_temp`.

**Architecture:** Three layers, built bottom-up. (1) **Backend:** a new one-shot `configfile` heater-limits read landed on a `PrinterStateStore` StateFlow (mirrors `temperatureBackfill`). (2) **Holder/persistence:** `TemperatureHolder` grows past the 3-trace cap to a monitored set = heaters ∪ user-selected sensors (persisted in `TraceStylePrefs`); live values come from `heaters` (adjustable) or `temperatureSensors` (read-only). (3) **UI:** `TemperatureScreen` gains a `screenMode` toggle, mode-specific footers, a Settings sensor-picker Focus morph, an appearance-only Monitoring popup (4×2 color grid + visibility), and a control-only Adjust popup (stepper + scrubber + Off).

**Tech Stack:** Kotlin, Jetpack Compose + classic-Views hybrid, kotlinx.serialization (JSON-RPC), OkHttp/Retrofit, DataStore (Preferences), Coroutines/Flow. Tests: JUnit 4 (`org.junit.Assert.*`, backtick test names), `:app:testDebugUnitTest`.

**Spec:** `docs/superpowers/specs/2026-06-14-temperature-monitor-adjust-redesign-design.md`

---

## Build & Test Commands (local, Windows-side via WSL interop)

Run ALL unit tests:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Run a SINGLE test class (escape inner quotes):
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.state.HeaterLimitsTest\" --no-daemon" 2>&1 | tr -d '\r'
```
Build the debug APK (split-ABI; install BOTH slices on flox + moto for UAT — see [[dinghy-test-devices]]):
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
```
Exit code is authoritative. Force `--rerun-tasks` before an on-device UAT to dodge the stale-APK trap ([[dinghy-stale-apk-uat-gate]]).

---

## File Structure

**Create:**
- `app/src/main/java/works/mees/dinghy/state/HeaterLimits.kt` — `HeaterLimits` data class + pure `parseHeaterLimits(settings)` mapper.
- `app/src/test/java/works/mees/dinghy/state/HeaterLimitsTest.kt` — mapper contract tests.
- `app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureMonitoredSetTest.kt` — holder monitored-set resolution + ordering + sensor-value tests.
- `app/src/test/java/works/mees/dinghy/ui/temperature/ScrubberRangeTest.kt` — heater scrubber-range derivation.

**Modify:**
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` — add `HeaterLimits` import usage (data class lives in HeaterLimits.kt).
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — `_heaterLimits` StateFlow + `setHeaterLimits`.
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` — consume `parseHeaterLimits` inside the EXISTING configfile `runCatching` block.
- `app/src/main/java/works/mees/dinghy/di/SpineHandle.kt` — forward `heaterLimits` StateFlow.
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — expose `heaterLimits`; add `setSensorSelected` intent method.
- `app/src/main/java/works/mees/dinghy/ui/settings/TraceStylePrefs.kt` — `selectedSensors` Flow + `setSensorSelected`.
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt` — selected-sensors field/seed; monitored-set rework; sensor-source live values; `heaterLimits` passthrough; scrubber-range helper.
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — new `MonitorMode` (`format_list_bulleted`) + `TempSettings` (`settings`) entries.
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` — the two-mode rebuild (the bulk of the UI work).
- `app/src/main/java/works/mees/dinghy/preview/TemperaturePreviews.kt` — previews for the new states.

---

## Build order & dependencies

```
Task 1 (HeaterLimits mapper) ─┐
Task 2 (store flow)           ├─ Task 3 (handshake wiring) ─┐
                                                            │
Task 4 (prefs selection) ─ Task 5 (container intents) ─────┤
                                                            ├─ Task 6 (holder rework)
                                                            │
Task 7 (scrubber-range helper) ────────────────────────────┤
Task 8 (icons) ─────────────────────────────────────────────┘
                                                            └─ Tasks 9–14 (UI) ─ Task 15 (UAT)
```
Tasks 1–8 are backend/logic (TDD, unit-test-green). Tasks 9–14 are UI (compile + on-device). Task 15 is the integration UAT.

---

## Task 1: `HeaterLimits` data class + `parseHeaterLimits` pure mapper

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/state/HeaterLimits.kt`
- Test: `app/src/test/java/works/mees/dinghy/state/HeaterLimitsTest.kt`

Mirrors `state/TemperatureStore.kt` (pure, null-safe, no I/O). Input is the `configfile.settings` object (same level the existing block already resolves for `extruder`/`printer`/macros). Each heater section (`extruder`, `extruder<N>`, `heater_bed`, `heater_generic <name>`) carries `min_temp`/`max_temp` numbers. Keys match the monitored-set keys (full object names; `heater_generic <name>` retained verbatim).

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson

class HeaterLimitsTest {

    private fun settings(json: String) = MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun `maps min and max for heater sections`() {
        val s = settings(
            """
            {
              "extruder": {"min_temp": 0, "max_temp": 300, "pressure_advance": 0.05},
              "heater_bed": {"min_temp": 0, "max_temp": 120},
              "heater_generic chamber": {"min_temp": 0, "max_temp": 80}
            }
            """.trimIndent(),
        )
        val limits = parseHeaterLimits(s)
        assertEquals(300.0, limits["extruder"]!!.maxTemp!!, 0.0001)
        assertEquals(120.0, limits["heater_bed"]!!.maxTemp!!, 0.0001)
        assertEquals(80.0, limits["heater_generic chamber"]!!.maxTemp!!, 0.0001)
        assertEquals(0.0, limits["extruder"]!!.minTemp!!, 0.0001)
    }

    @Test
    fun `ignores non-heater sections`() {
        val s = settings(
            """
            {
              "printer": {"max_velocity": 300, "max_accel": 3000},
              "probe": {"z_offset": 1.2},
              "extruder": {"max_temp": 250}
            }
            """.trimIndent(),
        )
        val limits = parseHeaterLimits(s)
        assertEquals(setOf("extruder"), limits.keys)
    }

    @Test
    fun `omits a heater section that carries no temp fields`() {
        val s = settings("""{ "heater_bed": {"sensor_type": "EPCOS 100K"} }""")
        val limits = parseHeaterLimits(s)
        assertTrue("no temp fields -> omitted", limits.isEmpty())
    }

    @Test
    fun `present min absent max yields null max`() {
        val s = settings("""{ "extruder": {"min_temp": 10} }""")
        val limits = parseHeaterLimits(s)
        assertEquals(10.0, limits["extruder"]!!.minTemp!!, 0.0001)
        assertNull(limits["extruder"]!!.maxTemp)
    }

    @Test
    fun `skips garbled non-numeric temp`() {
        val s = settings("""{ "extruder": {"max_temp": "hot"} }""")
        val limits = parseHeaterLimits(s)
        // garbled max -> null max; section omitted because no usable temp field remains
        assertTrue(limits.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.state.HeaterLimitsTest\" --no-daemon" 2>&1 | tr -d '\r'
```
Expected: FAIL — `parseHeaterLimits` / `HeaterLimits` unresolved.

- [ ] **Step 3: Write the mapper + data class**

```kotlin
package works.mees.dinghy.state

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Static per-heater temperature limits read ONCE from `configfile.settings.<heater>` at handshake
 * (mirrors the [parseTemperatureStore] backfill discipline — pure, null-safe, host-testable). Either
 * bound may be null when the printer's config omits it; the UI falls back to the global
 * [works.mees.dinghy.command.PrinterCommands.MAX_TEMP_C] clamp when [maxTemp] is null.
 */
@Immutable
data class HeaterLimits(
    val minTemp: Double? = null,
    val maxTemp: Double? = null,
)

/**
 * Map a `configfile.settings` [settings] object → per-heater [HeaterLimits] keyed by the FULL heater
 * object name (`extruder`, `extruder1`, `heater_bed`, `heater_generic <name>`) — the same keys the
 * monitored set / `temperature_store` backfill use. House rule (mirrors [parseTemperatureStore]):
 * every walk is null-safe (`as?`/`?.`/`orNull`), NEVER `!!`. A section with NO usable numeric temp
 * field (both min and max absent/garbled) is OMITTED, never fabricated.
 */
fun parseHeaterLimits(settings: JsonObject): Map<String, HeaterLimits> {
    val out = LinkedHashMap<String, HeaterLimits>()
    for ((key, value) in settings) {
        if (!isHeaterSection(key)) continue
        val section = value as? JsonObject ?: continue
        val min = section.numberOrNull("min_temp")
        val max = section.numberOrNull("max_temp")
        if (min == null && max == null) continue // no usable temp field — omit
        out[key] = HeaterLimits(minTemp = min, maxTemp = max)
    }
    return out
}

private fun isHeaterSection(name: String): Boolean =
    name == "heater_bed" ||
        name == "extruder" ||
        (name.startsWith("extruder") && name.removePrefix("extruder").all { it.isDigit() } &&
            name != "extruder") ||
        name.startsWith("heater_generic ")

private fun JsonObject.numberOrNull(key: String): Double? =
    runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()
```

> Note: the `extruder<N>` branch accepts `extruder1`, `extruder2`, … (digits after the prefix) but not arbitrary `extruder_foo`. `extruder` itself is matched by the first clause.

- [ ] **Step 4: Run test to verify it passes**

Run the single-class command from Step 2. Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/HeaterLimits.kt app/src/test/java/works/mees/dinghy/state/HeaterLimitsTest.kt
git commit -m "feat(temperature): parseHeaterLimits pure mapper for configfile heater min/max"
```

---

## Task 2: `PrinterStateStore` heater-limits StateFlow + setter

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` (add beside `_temperatureBackfill` at ~86-88 and `setTemperatureBackfill` at ~307-310)

No new unit test (a trivial StateFlow setter; covered transitively by Task 3 + holder tests). Verify by suite compile.

- [ ] **Step 1: Add the StateFlow declaration**

After the `temperatureBackfill` declaration (around line 88), add:
```kotlin
    private val _heaterLimits = MutableStateFlow<Map<String, HeaterLimits>>(emptyMap())
    /** Per-heater static `configfile` min/max temp (one-shot at handshake, NOT the throttled hot path). */
    val heaterLimits: StateFlow<Map<String, HeaterLimits>> = _heaterLimits.asStateFlow()
```

- [ ] **Step 2: Add the setter**

After `setTemperatureBackfill` (around line 310), add:
```kotlin
    /** One-shot at handshake: per-heater configfile min/max temp. Mirrors [setTemperatureBackfill]. */
    fun setHeaterLimits(limits: Map<String, HeaterLimits>) {
        _heaterLimits.value = limits
    }
```

`HeaterLimits` is in the same package (`works.mees.dinghy.state`) — no import needed.

- [ ] **Step 3: Compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
git commit -m "feat(temperature): heaterLimits StateFlow + setter on PrinterStateStore"
```

---

## Task 3: Wire the configfile consumer + forward through SpineHandle

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` (inside the EXISTING configfile `runCatching` block, ~534-609 — do NOT add a second `objectsQuery(configfile)`; Pitfall 3)
- Modify: `app/src/main/java/works/mees/dinghy/di/SpineHandle.kt` (add the forwarded StateFlow param ~65 + wire at the construction site)

- [ ] **Step 1: Consume parseHeaterLimits in the existing configfile block**

In `MoonrakerSession.kt`, inside the configfile `runCatching { ... }` block, after `val settings = parseStatus(cfgResult)?.objectOrNull("configfile")?.objectOrNull("settings")` is resolved and beside the other `store.set*` consumers (e.g. right after the `setMacroBodies(...)` call), add:
```kotlin
            // Per-heater min/max temp for the Temperature adjust scrubber range (real Moonraker limits,
            // NOT the fixed 350 clamp). Same one-shot configfile result — no extra query (Pitfall 3).
            store.setHeaterLimits(if (settings != null) parseHeaterLimits(settings) else emptyMap())
```
Add the import at the top (beside `import works.mees.dinghy.state.parseTemperatureStore`):
```kotlin
import works.mees.dinghy.state.parseHeaterLimits
```

- [ ] **Step 2: Forward through SpineHandle**

In `SpineHandle.kt`, add a constructor param beside `temperatureBackfill` (~line 65):
```kotlin
    /** Per-heater static configfile min/max temp (one-shot), for the Temperature adjust scrubber range. */
    val heaterLimits: StateFlow<Map<String, HeaterLimits>>,
```
Add the import if not present:
```kotlin
import works.mees.dinghy.state.HeaterLimits
```
Then find the `SpineHandle(` construction site (grep below) and wire `heaterLimits = store.heaterLimits,`:
```bash
grep -rn 'SpineHandle(' app/src/main/java | grep -v 'class SpineHandle'
```

- [ ] **Step 3: Compile the whole app**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL. (If the construction site needs `heaterLimits`, the compiler names the missing arg — wire it.)

- [ ] **Step 4: Run the full unit suite (no regressions)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Expected: PASS (existing session/store tests still green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
git commit -m "feat(temperature): read configfile heater limits at handshake, forward via SpineHandle"
```

---

## Task 4: Persisted selected-sensors set in `TraceStylePrefs`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/settings/TraceStylePrefs.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/settings/TraceStylePrefsSelectionTest.kt` (create)

Selection is **opt-in**: absent key = NOT selected (heaters are always shown regardless and never live in this set). Deselect = `remove()` the key (sparse store); the read filter counts only `== true`.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TraceStylePrefsSelectionTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store(): DataStore<Preferences> {
        val file: File = tmp.newFile("sel.preferences_pb")
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    @Test
    fun `selecting then deselecting a sensor round-trips`() = runTest {
        val prefs = TraceStylePrefs(store())
        assertTrue("empty by default", prefs.selectedSensors.first().isEmpty())

        prefs.setSensorSelected("temperature_sensor chamber", true)
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors.first())

        prefs.setSensorSelected("temperature_sensor chamber", false)
        assertTrue("deselect clears it", prefs.selectedSensors.first().isEmpty())
    }

    @Test
    fun `multiple selections accumulate`() = runTest {
        val prefs = TraceStylePrefs(store())
        prefs.setSensorSelected("temperature_sensor chamber", true)
        prefs.setSensorSelected("temperature_sensor mcu", true)
        assertEquals(
            setOf("temperature_sensor chamber", "temperature_sensor mcu"),
            prefs.selectedSensors.first(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.settings.TraceStylePrefsSelectionTest\" --no-daemon" 2>&1 | tr -d '\r'
```
Expected: FAIL — `selectedSensors`/`setSensorSelected` unresolved.

- [ ] **Step 3: Add the selection Flow + writer**

In `TraceStylePrefs.kt`, add a `selectedSensors` Flow (copy the `traceVisibility` shape) and a `setSensorSelected` writer. Add the `stringSetPreferencesKey`-free flat-boolean idiom (matches the existing per-key style):

```kotlin
    val selectedSensors: Flow<Set<String>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                buildSet {
                    prefs.asMap().forEach { (key, value) ->
                        if (key.name.startsWith(PREFIX_SELECTED) && value is Boolean && value) {
                            add(key.name.removePrefix(PREFIX_SELECTED))
                        }
                    }
                }
            }

    suspend fun setSensorSelected(sensorName: String, selected: Boolean) {
        dataStore.edit { prefs ->
            val key = booleanPreferencesKey(PREFIX_SELECTED + sensorName)
            if (selected) prefs[key] = true else prefs.remove(key)
        }
    }
```
Add the prefix constant to the companion object:
```kotlin
        private const val PREFIX_SELECTED = "trace_selected_"
```
`buildSet` is a stdlib builder; no new import. `remove` is available on `MutablePreferences` (already used by DataStore's `edit`).

- [ ] **Step 4: Run test to verify it passes**

Single-class command from Step 2. Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/settings/TraceStylePrefs.kt app/src/test/java/works/mees/dinghy/ui/settings/TraceStylePrefsSelectionTest.kt
git commit -m "feat(temperature): persist user-selected temperature_sensor set in TraceStylePrefs"
```

---

## Task 5: `AppContainer.setSensorSelected` intent + expose `heaterLimits`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`

No new test (intent method = `writeScope.launch` wrapper, same as `setTraceColor`; verified by compile + the on-device persistence check in Task 15). Exposing `heaterLimits` is a passthrough off the spine.

- [ ] **Step 1: Add the persist intent method**

Beside `setTraceColor`/`setTraceVisibility` (~408-418):
```kotlin
    /** Persist a Temperature sensor's monitored-set membership (D: opt-in). Routes through writeScope. */
    fun setSensorSelected(sensorName: String, selected: Boolean) {
        writeScope.launch { traceStylePrefs.setSensorSelected(sensorName, selected) }
    }
```

- [ ] **Step 2: Expose heaterLimits off the spine**

Find how `activeThemeTuple` / other spine flows are surfaced on `AppContainer` (grep `temperatureBackfill` / `minExtrudeTemp` in AppContainer.kt to match the exact pattern — they forward off the current `SpineHandle`). Add a `heaterLimits` passthrough mirroring whichever pattern the existing one-shot flows use, e.g.:
```bash
grep -n 'temperatureBackfill\|minExtrudeTemp\|SpineHandle\|spine' app/src/main/java/works/mees/dinghy/di/AppContainer.kt
```
If the screen consumes the holder for graph data (it does) and only needs limits in the adjust popup, the cleanest path is to pass `heaterLimits` into `TemperatureHolder` (Task 6) rather than surface it on the container directly. **Decision for this plan: feed `heaterLimits` into the holder** (Task 6 adds the ctor param), so this step only adds the `setSensorSelected` intent. Skip a separate container `heaterLimits` field unless the holder wiring needs it.

- [ ] **Step 3: Compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/di/AppContainer.kt
git commit -m "feat(temperature): AppContainer.setSensorSelected intent through writeScope"
```

---

## Task 6: `TemperatureHolder` — monitored set beyond the 3-trace cap

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt`
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (pass `heaterLimits` + the holder is built shell-side — see note)
- Modify: `app/src/main/java/works/mees/dinghy/ui/AppShell.kt` (holder construction, ~192 — add the new ctor args)
- Test: `app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureMonitoredSetTest.kt` (create)

This is the core data rework. The monitored set is now **dynamic** (changes when the user toggles a sensor), so the holder must re-resolve `drawn` + rebuild rings when either capabilities OR `selectedSensors` changes — the old "resolve once, return early" guard is replaced by a recompute keyed on `(capabilities, selectedSensors)`.

### Behavior contract
- Monitored set = `heaters` (always, from `Capabilities.heaters` else live `state.heaters.keys`) ∪ `selectedSensors` (subset of `temperature_sensor` objects). **Ordering:** two groups — heaters first, then selected sensors — **alphabetical by object name within each group**.
- `isAdjustable = name in heaterSet` (sensors → false, `target = null` always).
- Live value: heater → `state.heaters[name]?.temperature`; sensor → `state.temperatureSensors[name]`.
- Backfill request widens to the full monitored set (Task 3 already lands limits; backfill widening is here).
- `MAX_TRACES` cap removed.
- `heaterLimits` exposed as a holder StateFlow for the screen.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.temperature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureMonitoredSetTest {

    private fun store(scope: CoroutineScope) = PrinterStateStore(scope)

    @Test
    fun `monitored set is heaters alphabetical then selected sensors alphabetical`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = CoroutineScope(UnconfinedTestDispatcher())
            val st = store(scope)
            val holder = TemperatureHolder(scope = scope, store = st)
            // Capabilities name two heaters (out of alpha order) + two sensors exist live.
            st.setCapabilitiesForTest(
                Capabilities(heaters = listOf("heater_bed", "extruder")),
            )
            st.setStateForTest(
                PrinterState(
                    heaters = persistentMapOf(
                        "extruder" to HeaterState(temperature = 210.0, target = 200.0),
                        "heater_bed" to HeaterState(temperature = 60.0, target = 60.0),
                    ),
                    temperatureSensors = persistentMapOf(
                        "temperature_sensor chamber" to 35.0,
                        "temperature_sensor mcu" to 48.0,
                    ),
                ),
            )
            holder.setSensorSelected("temperature_sensor mcu", true)
            holder.setSensorSelected("temperature_sensor chamber", true)

            val names = holder.legend.value.map { it.name }
            assertEquals(
                listOf("extruder", "heater_bed", "temperature_sensor chamber", "temperature_sensor mcu"),
                names,
            )
        }

    @Test
    fun `heaters are adjustable and sensors are not`() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val st = store(scope)
        val holder = TemperatureHolder(scope = scope, store = st)
        st.setCapabilitiesForTest(Capabilities(heaters = listOf("extruder")))
        st.setStateForTest(
            PrinterState(
                heaters = persistentMapOf("extruder" to HeaterState(temperature = 25.0, target = 0.0)),
                temperatureSensors = persistentMapOf("temperature_sensor mcu" to 40.0),
            ),
        )
        holder.setSensorSelected("temperature_sensor mcu", true)

        val byName = holder.legend.value.associateBy { it.name }
        assertTrue(byName["extruder"]!!.isAdjustable)
        assertFalse(byName["temperature_sensor mcu"]!!.isAdjustable)
        assertEquals(40.0, byName["temperature_sensor mcu"]!!.current, 0.0001)
    }

    @Test
    fun `deselecting a sensor drops it from the monitored set`() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val st = store(scope)
        val holder = TemperatureHolder(scope = scope, store = st)
        st.setCapabilitiesForTest(Capabilities(heaters = listOf("extruder")))
        st.setStateForTest(
            PrinterState(
                heaters = persistentMapOf("extruder" to HeaterState(temperature = 25.0)),
                temperatureSensors = persistentMapOf("temperature_sensor mcu" to 40.0),
            ),
        )
        holder.setSensorSelected("temperature_sensor mcu", true)
        assertEquals(2, holder.legend.value.size)
        holder.setSensorSelected("temperature_sensor mcu", false)
        assertEquals(listOf("extruder"), holder.legend.value.map { it.name })
    }
}
```

> **Test seam check:** the test uses `PrinterStateStore.setCapabilitiesForTest(...)` / `setStateForTest(...)`. Before writing impl, grep the store for the existing test seams and use whatever the store already exposes (the existing `TemperatureHolderTest` drives state — match its mechanism). If no such setters exist, drive state through the same path `TemperatureHolderTest` uses and adjust these calls accordingly. Do not invent seams that aren't there.

- [ ] **Step 2: Run test to verify it fails**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.temperature.TemperatureMonitoredSetTest\" --no-daemon" 2>&1 | tr -d '\r'
```
Expected: FAIL — `setSensorSelected` unresolved on holder / ordering wrong / cap truncates.

- [ ] **Step 3: Rework the holder**

In `TemperatureHolder.kt`:

(a) Add the constructor param `heaterLimits`:
```kotlin
class TemperatureHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
    traceStylePrefs: TraceStylePrefs? = null,
) {
```
Keep the signature; expose `store.heaterLimits` directly via a forwarded flow (no new ctor arg needed — the holder already holds `store`):
```kotlin
    /** Per-heater configfile min/max temp (one-shot), for the adjust scrubber range. */
    val heaterLimits: StateFlow<Map<String, HeaterLimits>> = store.heaterLimits
```
Add imports: `works.mees.dinghy.state.HeaterLimits`.

(b) Add the selected-sensors field + seed + in-memory setter:
```kotlin
    private val _selectedSensors = MutableStateFlow<Set<String>>(emptySet())
    /** User-selected `temperature_sensor` object names to monitor (persisted via TraceStylePrefs). */
    val selectedSensors: StateFlow<Set<String>> = _selectedSensors.asStateFlow()

    /** In-memory update; the CALLER also persists via AppContainer.setSensorSelected (writeScope). */
    fun setSensorSelected(sensorName: String, selected: Boolean) {
        _selectedSensors.value =
            if (selected) _selectedSensors.value + sensorName else _selectedSensors.value - sensorName
    }
```
In `init`, inside the `if (traceStylePrefs != null)` block, add a third collector:
```kotlin
        if (traceStylePrefs != null) {
            scope.launch { traceStylePrefs.selectedSensors.collect { _selectedSensors.value = it } }
            // ...existing traceColors + traceVisibility collectors...
        }
```

(c) Replace the resolve-once model with a recompute keyed on capabilities + selection. Remove `MAX_TRACES`, `@Volatile drawn`, `ensureResolved`'s early-return, and `resolveDrawn`'s ≤3 cap. New resolution:
```kotlin
    /** Heaters (always) ∪ selected sensors — two groups, alphabetical within each. */
    private fun resolveMonitored(state: PrinterState, caps: Capabilities, selected: Set<String>): List<String> {
        val heaterNames = (if (caps.heaters.isNotEmpty()) caps.heaters else state.heaters.keys.toList())
            .sorted()
        val sensorNames = selected.sorted()
        return heaterNames + sensorNames
    }
```
Hold the heater set for `isAdjustable`:
```kotlin
    @Volatile private var heaterSet: Set<String> = emptySet()
    @Volatile private var drawn: List<String> = emptyList()
```
Recompute when inputs change. The simplest robust structure: drive the live collector off a `combine` of `store.printerState`, `store.capabilities`, and `selectedSensors`, rebuilding rings when the resolved `drawn` list changes:
```kotlin
        scope.launch {
            combine(store.printerState, store.capabilities, _selectedSensors) { state, caps, sel ->
                Triple(state, caps, sel)
            }.collect { (state, caps, sel) ->
                val resolved = resolveMonitored(state, caps, sel)
                if (resolved != drawn) {
                    drawn = resolved
                    heaterSet = (if (caps.heaters.isNotEmpty()) caps.heaters else state.heaters.keys.toList()).toSet()
                    rings = resolved.map { RingBuffer() }
                    seeded = false // allow backfill to re-seed the new ring set
                }
                _legend.value = drawn.map { name -> readout(state, name) }
                _setpoints.value = drawn.map { name -> setpointOf(state, name) }
                if (rings.isNotEmpty()) {
                    drawn.forEachIndexed { i, name -> rings[i].push(liveValue(state, name)) }
                    publishSeries()
                }
            }
        }
```
Add `liveValue` (heater vs sensor source) and update `readout`/`setpointOf`:
```kotlin
    private fun liveValue(state: PrinterState, name: String): Float =
        if (name in heaterSet) (state.heaters[name]?.temperature?.toFloat() ?: 0f)
        else (state.temperatureSensors[name]?.toFloat() ?: 0f)

    private fun setpointOf(state: PrinterState, name: String): Float? {
        if (name !in heaterSet) return null // sensors have no setpoint
        val h = state.heaters[name] ?: return null
        return if (h.target > 0.0) h.target.toFloat() else null
    }

    private fun readout(state: PrinterState, name: String): SensorReadout {
        val adjustable = name in heaterSet
        val current = if (adjustable) (state.heaters[name]?.temperature ?: 0.0) else (state.temperatureSensors[name] ?: 0.0)
        val target = if (adjustable) (state.heaters[name]?.target?.takeIf { it > 0.0 }) else null
        return SensorReadout(name = name, label = label(name), current = current, target = target, isAdjustable = adjustable)
    }
```
Update `label()` to handle the `temperature_sensor ` prefix:
```kotlin
private fun label(objectName: String): String = when {
    objectName == "extruder" -> "NOZZLE"
    objectName.startsWith("extruder") -> "NOZZLE ${objectName.removePrefix("extruder")}"
    objectName == "heater_bed" -> "BED"
    objectName.startsWith("heater_generic ") -> objectName.removePrefix("heater_generic ").uppercase()
    objectName.startsWith("temperature_sensor ") -> objectName.removePrefix("temperature_sensor ").uppercase()
    else -> objectName.uppercase()
}
```
(d) Widen the backfill request: the backfill collector currently seeds only `drawn` (the heaters). Since `drawn` is now the full monitored set, the existing `backfill[name]?.forEach { rings[i].push(it) }` loop already covers sensors **if** the handshake requested them. Update the handshake request in `MoonrakerSession.kt` to request the full set the graph might draw — change `parseTemperatureStore(storeResult.jsonObject, capabilities.heaters.toSet())` to also include every `temperature_sensor ` object so added-sensor history is available:
```kotlin
            val wanted = capabilities.heaters.toSet() +
                capabilities.objects.filter { it.startsWith("temperature_sensor ") }.toSet()
            val backfill = parseTemperatureStore(storeResult.jsonObject, wanted)
```
Imports for `combine`: `kotlinx.coroutines.flow.combine`. Add `store.capabilities` access (already used via `store.capabilities.value` in the old code — now collected).

> Remove the now-dead `resolveDrawn`, `ensureResolved`, and `MAX_TRACES`. Keep the backfill collector but have it seed against the current `drawn`/`rings` (guard with `seeded`, reset on set change as above).

- [ ] **Step 4: Update holder construction (AppShell)**

`AppShell.kt:~192` already passes `traceStylePrefs = container.traceStylePrefs`; no new arg needed (the holder reads `store.heaterLimits` internally). Confirm it still compiles unchanged.

- [ ] **Step 5: Run the monitored-set test + the existing holder tests**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.temperature.*\" --no-daemon" 2>&1 | tr -d '\r'
```
Expected: PASS — new `TemperatureMonitoredSetTest` + existing `TemperatureHolderTest`/`TemperatureHolderTraceStyleTest`/`TemperatureHeaterOffTest` (fix any that assumed the ≤3 cap or old ordering — update their expectations to the new alphabetical monitored-set contract).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureMonitoredSetTest.kt
git commit -m "feat(temperature): holder monitored set (heaters ∪ selected sensors), drop 3-trace cap"
```

---

## Task 7: Scrubber-range derivation helper (pure)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt` (add a top-level pure helper near `computeGraphYRange`)
- Test: `app/src/test/java/works/mees/dinghy/ui/temperature/ScrubberRangeTest.kt` (create)

Range = `0f .. (limits.maxTemp ?: MAX_TEMP_C)`. Floor is always 0 (0 = off); `minTemp` is informational only.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.ui.temperature

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.state.HeaterLimits

class ScrubberRangeTest {
    @Test fun `uses configured max when present`() {
        val r = heaterScrubberRange(HeaterLimits(minTemp = 0.0, maxTemp = 300.0))
        assertEquals(0f, r.start, 0f)
        assertEquals(300f, r.endInclusive, 0f)
    }

    @Test fun `falls back to global max when limits null`() {
        val r = heaterScrubberRange(null)
        assertEquals(0f, r.start, 0f)
        assertEquals(PrinterCommands.MAX_TEMP_C.toFloat(), r.endInclusive, 0f)
    }

    @Test fun `falls back to global max when maxTemp missing`() {
        val r = heaterScrubberRange(HeaterLimits(minTemp = 10.0, maxTemp = null))
        assertEquals(PrinterCommands.MAX_TEMP_C.toFloat(), r.endInclusive, 0f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.temperature.ScrubberRangeTest\" --no-daemon" 2>&1 | tr -d '\r'
```
Expected: FAIL — `heaterScrubberRange` unresolved.

- [ ] **Step 3: Implement the helper**

Add near the bottom of `TemperatureHolder.kt` (top-level fun):
```kotlin
/**
 * The adjust scrubber's range for a heater: 0 (= off) up to the configured `max_temp`, falling back to
 * the global [works.mees.dinghy.command.PrinterCommands.MAX_TEMP_C] clamp when limits are unknown.
 * `min_temp` is informational only — the floor stays 0 so the scrubber can reach off.
 */
fun heaterScrubberRange(limits: works.mees.dinghy.state.HeaterLimits?): ClosedFloatingPointRange<Float> {
    val max = (limits?.maxTemp ?: works.mees.dinghy.command.PrinterCommands.MAX_TEMP_C.toDouble()).toFloat()
    return 0f..max
}
```

- [ ] **Step 4: Run test to verify it passes**

Single-class command from Step 2. Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt app/src/test/java/works/mees/dinghy/ui/temperature/ScrubberRangeTest.kt
git commit -m "feat(temperature): heaterScrubberRange helper (0..max_temp, 350 fallback)"
```

---

## Task 8: Icon registry — `format_list_bulleted` (monitor) + settings alias

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- (If an icon-bucket JSON test enforces registration) Modify the bucket JSON the test reads.

Owner-chosen glyphs (registry law satisfied — not auto-picked): adjust toggle reuses existing `OutputHeater` (`mode_heat`); monitor toggle = NEW `format_list_bulleted`; gear reuses the existing `settings` ligature.

- [ ] **Step 1: Add the registry entries**

In `DinghyIcons.kt`, add:
```kotlin
    /** Temperature: return to Monitoring mode (owner-chosen; monitoring == the sensor list view). */
    val MonitorMode = DinghyIcon(IconRef.Ligature("format_list_bulleted"), alternate = "temp_monitor_mode")
    /** Temperature: Settings (sensor-display picker). Reuses the system settings glyph. */
    val TempSettings = DinghyIcon(IconRef.Ligature("settings"), alternate = "temp_settings")
```

- [ ] **Step 2: Check for an icon-registration test and satisfy it**

```bash
grep -rln 'material-icon-bucket\|DinghyIcons\|IconRef.Ligature' app/src/test app/src/androidTest 2>/dev/null
grep -rn 'format_list_bulleted\|filter_list' app/src/main/res app/src/main/assets docs 2>/dev/null | head
```
If a test asserts every `Ligature(...)` exists in the Material Symbols font / bucket JSON, add `format_list_bulleted` (and confirm `settings` already present) to that source. Re-run that test.

- [ ] **Step 3: Compile + run icon test (if any)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"*Icon*\" --no-daemon" 2>&1 | tr -d '\r'
```
Expected: PASS (or BUILD SUCCESSFUL if no icon test exists).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(temperature): register MonitorMode (format_list_bulleted) + TempSettings icons"
```

---

## Task 9: `TemperatureScreen` — two-mode scaffold + footers

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`

UI task — verify by compile + preview + on-device. Introduce `screenMode` and rebuild the footers. The existing `TempFieldMode` (SensorList/PresetPicker) is folded under the new mode model: Presets becomes an Adjust-mode field-takeover.

### State model
```kotlin
private enum class TempMode { Monitoring, Adjust }
```
- `var mode by remember { mutableStateOf(TempMode.Monitoring) }`
- `selectedName` (existing) — the row whose popup is open; **cleared when `mode` flips** (the popup belongs to a mode).
- `fieldMode` (existing PresetPicker takeover) — only reachable in Adjust now.
- Add `var settingsOpen by remember { mutableStateOf(false) }` — Settings sensor-picker Focus morph (Monitoring only).

- [ ] **Step 1: Add mode state + clear-on-flip + the two footers**

Replace the single `FootButtonBar` in the `SensorList` branch with a mode switch. Monitoring footer:
```kotlin
                        FootButtonBar(uDp = grid.uDp) {
                            OutlinedControl(
                                label = "", onClick = onBack, modifier = Modifier.weight(1f),
                                intent = Intent.Accent, icon = DinghyIcons.Back,
                            )
                            OutlinedControl(
                                label = "", onClick = { settingsOpen = true; selectedName = null },
                                modifier = Modifier.weight(1f), intent = Intent.Accent,
                                icon = DinghyIcons.TempSettings,
                            )
                            OutlinedControl(
                                label = "", onClick = { mode = TempMode.Adjust; selectedName = null; settingsOpen = false },
                                modifier = Modifier.weight(1f), intent = Intent.Accent,
                                icon = DinghyIcons.OutputHeater, // mode_heat -> enter Adjust
                            )
                        }
```
Adjust footer (`Presets · Cooldown · Monitor`, no Back):
```kotlin
                        FootButtonBar(uDp = grid.uDp) {
                            OutlinedControl(
                                label = stringResource(R.string.temp_presets),
                                onClick = { fieldMode = TempFieldMode.PresetPicker },
                                modifier = Modifier.weight(1f), intent = Intent.Accent,
                            )
                            OutlinedControl(
                                label = stringResource(R.string.temp_cooldown),
                                onClick = onCooldown, modifier = Modifier.weight(1f), intent = Intent.Warn,
                            )
                            OutlinedControl(
                                label = "", onClick = { mode = TempMode.Monitoring; selectedName = null },
                                modifier = Modifier.weight(1f), intent = Intent.Accent,
                                icon = DinghyIcons.MonitorMode, // format_list_bulleted -> back to Monitoring
                            )
                        }
```
Gate which footer renders on `mode`. Keep the PresetPicker takeover branch unchanged (its Back returns to `SensorList`).

- [ ] **Step 2: Filter the Field list by mode**

In the `SensorList` branch, filter the `items(...)` source by mode:
```kotlin
                        val rows = if (mode == TempMode.Adjust) legend.filter { it.isAdjustable } else legend
                        items(rows, key = { it.name }) { sensor -> /* existing row */ }
```

- [ ] **Step 3: Compile + a preview render**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL. (Functional check happens on-device in Task 15.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
git commit -m "feat(temperature): Monitoring/Adjust mode scaffold + mode-specific footers"
```

---

## Task 10: Monitoring popup — appearance only (4×2 color grid + visibility/done)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`

Split `TemperatureAdjusterFocus` into TWO Focus bodies: an appearance popup (Monitoring) and a control popup (Adjust, Task 12). The appearance popup has **no** temp controls — even for heaters.

- [ ] **Step 1: Add the appearance Focus composable**

```kotlin
@Composable
private fun SensorAppearanceFocus(
    traceColor: Color?,
    traceVisible: Boolean,
    colorfulSwatches: List<Color>,
    onColorSelect: (Color) -> Unit,
    onVisibilityToggle: () -> Unit,
    onDone: () -> Unit,
    uDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 4×2 color grid fills the space between the title (FocusFrame header) and the bottom buttons.
        val rows = colorfulSwatches.chunked(4) // two rows of four
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowColors.forEach { poolColor ->
                        val isSelected = traceColor != null && poolColor.toArgb() == traceColor.toArgb()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                .clip(CircleShape)
                                .background(poolColor) // data color — THEME-01 carve-out
                                .border(
                                    BorderStroke(if (isSelected) 4.dp else 1.dp, t.accentLine),
                                    CircleShape,
                                )
                                .clickable { onColorSelect(poolColor) },
                        )
                    }
                }
            }
        }
        // Bottom buttons: Visibility · Done.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedControl(
                label = "", onClick = onVisibilityToggle, modifier = Modifier.weight(1f),
                intent = if (traceVisible) Intent.Accent else Intent.Neutral,
                icon = if (traceVisible) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
            )
            OutlinedControl(
                label = stringResource(R.string.common_done), onClick = onDone,
                modifier = Modifier.weight(1f), intent = Intent.Accent,
            )
        }
    }
}
```
Imports already present in the file (`fillMaxHeight`, `aspectRatio`, `chunked` is stdlib). `matchHeightConstraintsFirst = true` keeps the dots round while the grid stretches to fill — dots grow to the available height (owner: "larger dots").

- [ ] **Step 2: Route the Focus morph by mode**

In `TemperatureContent`, the Focus `if (selectedSensor == null)` branch becomes a three-way:
```kotlin
                    when {
                        settingsOpen -> SensorPickerFocus(/* Task 11 */)
                        selectedSensor == null -> /* existing GraphViewHost FocusFrame */
                        mode == TempMode.Monitoring -> {
                            val sensor = selectedSensor
                            FocusFrame(title = sensor.label, icon = iconForSensor(sensor.name), uDp = grid.uDp,
                                modifier = Modifier.fillMaxSize(), isPrinting = isPrinting,
                                onEmergencyStop = onEmergencyStop, onPanic = onEmergencyStop,
                                contentInset = FocusInset / 2) {
                                SensorAppearanceFocus(
                                    traceColor = traceColors[sensor.name],
                                    traceVisible = traceVisibility[sensor.name] ?: true,
                                    colorfulSwatches = colorfulSwatches,
                                    onColorSelect = { onSetTraceColor(sensor.name, it) },
                                    onVisibilityToggle = { onSetTraceVisibility(sensor.name, !(traceVisibility[sensor.name] ?: true)) },
                                    onDone = { selectedName = null },
                                    uDp = grid.uDp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        else -> { /* Adjust control popup — Task 12 */ }
                    }
```

- [ ] **Step 3: Compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL (the `else` Adjust branch may be a temporary `Unit {}`/graph fallback until Task 12 — keep it compiling).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
git commit -m "feat(temperature): monitoring appearance popup — 4x2 color grid + visibility/done"
```

---

## Task 11: Settings sensor-picker Focus morph

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`

Lists every `temperature_sensor <name>` from capabilities, each a `ListRow` toggling membership in the monitored set. Heaters are NOT listed (always monitored). Field stays the monitored list; only Focus changes. Needs the available-sensor names + the selected set + a toggle callback threaded down from the live overload.

- [ ] **Step 1: Thread the picker data through the content seam**

Add params to `TemperatureContent` (and the live `TemperatureScreen` overload): `availableSensors: List<String>` (capability `temperature_sensor ` objects), `selectedSensors: Set<String>`, `onToggleSensor: (String, Boolean) -> Unit`. In the live overload resolve them:
```kotlin
    val caps by container.capabilities.collectAsStateWithLifecycle(initialValue = Capabilities())
    val selectedSensors by holder.selectedSensors.collectAsStateWithLifecycle()
    val availableSensors = remember(caps) {
        caps.objects.filter { it.startsWith("temperature_sensor ") }.sorted()
    }
    // ...
    onToggleSensor = { name, sel ->
        holder.setSensorSelected(name, sel)
        container.setSensorSelected(name, sel)
    },
```
(Grep `container.capabilities` to confirm the exact accessor; the spine exposes capabilities — match the existing flow name.)

- [ ] **Step 2: Add the picker composable**

```kotlin
@Composable
private fun SensorPickerFocus(
    available: List<String>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDone: () -> Unit,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    uDp: androidx.compose.ui.unit.Dp,
) {
    val t = LocalTokens.current
    FocusFrame(
        title = stringResource(R.string.temp_settings_title), // "DISPLAY SENSORS"
        icon = DinghyIcons.TempSettings, uDp = uDp, modifier = Modifier.fillMaxSize(),
        isPrinting = isPrinting, onEmergencyStop = onEmergencyStop, onPanic = onEmergencyStop,
        contentInset = FocusInset / 2,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListBlock(modifier = Modifier.weight(1f)) {
                items(available, key = { it }) { name ->
                    val isOn = name in selected
                    ListRow(
                        selected = isOn,
                        onClick = { onToggle(name, !isOn) },
                        uDp = uDp,
                        leadingContent = {
                            ListRowIcon(icon = iconForSensor(name), uDp = uDp, tint = t.text2)
                        },
                        trailingContent = {
                            DinghyIconView(
                                icon = if (isOn) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
                                tint = if (isOn) t.accent else t.text2,
                            )
                        },
                    ) { ListRowLabel(sensorPickerLabel(name)) }
                }
            }
            OutlinedControl(
                label = stringResource(R.string.common_done), onClick = onDone,
                modifier = Modifier.fillMaxWidth(), intent = Intent.Accent,
            )
        }
    }
}

private fun sensorPickerLabel(objectName: String): String =
    objectName.removePrefix("temperature_sensor ").uppercase()
```
Wire `settingsOpen`'s `SensorPickerFocus(...)` call in the Task-10 `when` with `onDone = { settingsOpen = false }`. Add string resources `temp_settings_title` ("DISPLAY SENSORS") to `res/values/strings.xml`.

- [ ] **Step 3: Compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(temperature): Settings sensor-picker Focus morph (toggle temperature_sensor display)"
```

---

## Task 12: Adjust popup — stepper + scrubber (scaled to limits) + Off

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`

Control-only popup for the selected heater in Adjust mode: value readout + scrubber `0..max_temp` + ± stepper/IncrementPicker + `Done`/`Off`. Both scrubber and stepper drive the existing batcher (no double-dispatch: drag previews via `onValueChange`, commits via `onSettle`/stepper taps → `onNudgeHeater`).

- [ ] **Step 1: Thread heaterLimits to the content seam**

Add `heaterLimits: Map<String, HeaterLimits>` to `TemperatureContent` + the live overload; resolve `val heaterLimits by holder.heaterLimits.collectAsStateWithLifecycle()`.

- [ ] **Step 2: Add the control Focus composable**

```kotlin
@Composable
private fun HeaterControlFocus(
    sensor: SensorReadout,
    currentTarget: Double?,
    activeStep: Double,
    scrubRange: ClosedFloatingPointRange<Float>,
    busy: Boolean,
    rejectTick: Long,
    onNudge: (Int) -> Unit,        // absolute new target (clamped upstream)
    onStepSelect: (Double) -> Unit,
    onOff: () -> Unit,
    onDone: () -> Unit,
    uDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val seed: Double = currentTarget ?: sensor.current.let { if (it > 0.0) it else 0.0 }
    var scrubLive by remember(sensor.name) { mutableStateOf<Float?>(null) }
    val shown: Double = scrubLive?.toDouble() ?: seed

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdjusterPanel(
            value = shown,
            unit = "°C",
            baseline = null,
            decimals = 0,
            onDecrement = { onNudge((shown - activeStep).roundToInt()) },
            onIncrement = { onNudge((shown + activeStep).roundToInt()) },
            enabled = true,
            busy = busy,
            rejectTick = rejectTick,
            incrementPicker = { IncrementPicker(steps = TEMP_STEPS, activeStep = activeStep, onSelect = onStepSelect, uDp = uDp) },
            uDp = uDp,
            modifier = Modifier.fillMaxWidth(),
        )
        Scrubber(
            name = "",
            value = seed.toFloat(),
            range = scrubRange,
            step = 1f,
            uDp = uDp,
            unit = "°C",
            onValueChange = { scrubLive = it },
            onSettle = { v -> scrubLive = null; onNudge(v.roundToInt()) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedControl(label = stringResource(R.string.output_off), onClick = onOff, modifier = Modifier.weight(1f), intent = Intent.Warn)
            OutlinedControl(label = stringResource(R.string.common_done), onClick = onDone, modifier = Modifier.weight(1f), intent = Intent.Accent)
        }
    }
}
```
Imports to add: `works.mees.dinghy.designsystem.components.Scrubber`. `TEMP_STEPS` already defined. `onNudge` takes the **absolute** new target (the batcher clamps); the existing `onNudgeHeater(name, rawTarget)` callback already accepts an absolute raw target, so the call site maps `onNudge = { raw -> onNudgeHeater(sensor.name, raw) }`.

- [ ] **Step 3: Wire the Adjust branch of the Focus `when`**

Replace the Task-10 placeholder `else` branch:
```kotlin
                        else -> { // mode == Adjust, a heater row selected
                            val sensor = selectedSensor
                            FocusFrame(title = sensor.label, icon = iconForSensor(sensor.name), uDp = grid.uDp,
                                modifier = Modifier.fillMaxSize(), isPrinting = isPrinting,
                                onEmergencyStop = onEmergencyStop, onPanic = onEmergencyStop,
                                contentInset = FocusInset / 2) {
                                HeaterControlFocus(
                                    sensor = sensor,
                                    currentTarget = workingTargets[sensor.name] ?: sensor.target,
                                    activeStep = activeStep,
                                    scrubRange = heaterScrubberRange(heaterLimits[sensor.name]),
                                    busy = heaterDispatchKey(sensor.name) in inFlight,
                                    rejectTick = rejectTicks[heaterDispatchKey(sensor.name)] ?: 0L,
                                    onNudge = { raw -> onNudgeHeater(sensor.name, raw) },
                                    onStepSelect = { activeStep = it },
                                    onOff = { onHeaterOff(sensor.name) },
                                    onDone = { selectedName = null },
                                    uDp = grid.uDp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
```
Delete the now-unused `TemperatureAdjusterFocus` composable (its color/visibility moved to `SensorAppearanceFocus`, its heater controls to `HeaterControlFocus`).

- [ ] **Step 4: Compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
git commit -m "feat(temperature): adjust popup — stepper + scrubber(0..max_temp) + Off/Done"
```

---

## Task 13: Previews for the new states

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/preview/TemperaturePreviews.kt`

Add `@Preview` entries (portrait + landscape, dark) for: Monitoring list (heaters + 2 sensors), Adjust list (heaters only), Monitoring appearance popup (color grid), Adjust control popup (scrubber + stepper), Settings picker. Use the stateless `TemperatureScreen(...)` fixture overload (it already exists). These are the on-device-free smoke for layout.

- [ ] **Step 1: Add fixtures + previews**

Mirror the existing previews in the file; pass `legend` with both adjustable + non-adjustable `SensorReadout`s and supply `seedHex`/`dark`. (No new params needed beyond what the stateless overload exposes — if Tasks 11/12 added params to the stateless overload, give them fixture defaults.)

- [ ] **Step 2: Compile (previews live in main sourceset)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/preview/TemperaturePreviews.kt
git commit -m "test(temperature): previews for monitoring/adjust modes + popups + picker"
```

---

## Task 14: Full suite + APK build

**Files:** none (verification gate)

- [ ] **Step 1: Run the FULL unit suite**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'
```
Expected: PASS — all new + existing tests green. Fix any older Temperature tests still assuming the ≤3 cap / old footer / old `TemperatureAdjusterFocus`.

- [ ] **Step 2: Build both ABI slices (force rebuild — stale-APK gate)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" 2>&1 | tr -d '\r'
```
Expected: BUILD SUCCESSFUL; APK mtime newer than the last commit.

- [ ] **Step 3: Commit (if any test fixups were needed)**

```bash
git add -A && git commit -m "test(temperature): reconcile existing tests with the two-mode monitored-set model"
```

---

## Task 15: On-device UAT (flox + moto) — owner-driven

**Files:** none (owner verification; [[dinghy-display-ondevice-iteration]] — Claude installs, Matthew navigates/eyeballs)

Install the matching ABI slice on BOTH devices ([[dinghy-test-devices]]). Point at a live Moonraker (Ender 5 Plus `192.168.1.120:7125` or Ender 3 Pro `192.168.1.121:7125`). Walk the checklist:

- [ ] **Monitoring is default** on entry; footer = `Back · Settings · mode_heat`.
- [ ] **Mode toggle:** mode_heat → Adjust (footer `Presets · Cooldown · format_list_bulleted`); format_list_bulleted → back to Monitoring. Any open popup closes on flip.
- [ ] **Settings picker:** gear opens the `temperature_sensor` list in Focus; toggling one adds/removes it from the Monitoring list AND graph; **survives an app restart** (persistence).
- [ ] **Monitoring popup:** tap any row (incl. a heater) → 4×2 color grid + `Visibility · Done`, **no temp controls**. Color applies to the row icon + graph trace. Visibility hides the trace from the graph but the **row stays in the list**.
- [ ] **Adjust list** shows heaters only (sensors filtered out).
- [ ] **Adjust popup:** scrubber range tops out at the heater's real `max_temp` (bed ~120, nozzle ~300 — distinct); scrubber drag previews, release commits; ± stepper commits; `Off` kills heat reliably (undroppable); `Done` returns to graph.
- [ ] **No graph thrash / frozen frames** entering/leaving popups (Adreno-320 floor).
- [ ] **Portrait + landscape**, S/M/L text — color grid fills at 5U; controls ≤1U.

- [ ] **On green:** finishing-a-development-branch (merge/PR back per the fork plan). On issues: iterate on device, commit fixes, re-install.

---

## Self-Review notes (author)

- **Spec coverage:** §2 modes → Tasks 9; §3 monitored set/persistence → Tasks 4–6; §4.2 picker → Task 11; §4.3 appearance popup → Task 10; §4.4 control popup → Tasks 7,12; §5.1 holder → Task 6; §5.2 backfill widen → Task 6 step 3(d); §5.3 persistence → Tasks 4,5; §5.4 limits backend → Tasks 1–3; §7 icons → Task 8. All sections mapped.
- **Type consistency:** `HeaterLimits(minTemp,maxTemp)` used in Tasks 1,2,3,6,7,12. `setSensorSelected(String,Boolean)` on prefs (4), container (5), holder (6). `heaterScrubberRange(HeaterLimits?)` (7) consumed in (12). `TempMode` enum (9) used in 9,10,12. Footer `OutlinedControl` intents per R5.
- **Open verification points flagged inline** (don't guess): the `PrinterStateStore` test seams (Task 6 Step 1 note), the `SpineHandle(` construction site (Task 3 Step 2), the `container.capabilities` accessor name (Task 11 Step 1), and whether an icon-bucket test enforces registration (Task 8 Step 2).
