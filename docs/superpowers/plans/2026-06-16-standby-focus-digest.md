# Standby Focus Digest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the home/PrintStatus Focus glance with a compact printer-state digest (per-heater current/setpoint, Motors ON/OFF, Homed XYZ/NONE, Spool weight) for non-printing states; printing/paused keep today's glance.

**Architecture:** One new net field (`PrinterState.motorsEnabled`) fed by a `stepper_enable` subscription + reducer parse (motion steppers only); one new Geist type role (`focusHeroLabel`); a new `HomeDigest.kt` holding pure formatting helpers + the digest composable; `HomeFocus` branches printing→legacy-glance / else→digest. Heater values are colored from the per-printer trace-color override (else accent-first `seriesColor`).

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization (loose JSON), kotlinx-collections-immutable, JUnit. Spec: `docs/superpowers/specs/2026-06-16-standby-focus-digest-design.md`.

**Build/test commands (this repo builds Windows-side from WSL):**
- Unit tests: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon <--tests filter>" | tr -d '\r'`
- Assemble: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
- The process exit code is authoritative. `./gradlew` does NOT run from WSL bash.

---

## File Structure

**Create:**
- `app/src/main/java/works/mees/dinghy/ui/printstatus/HomeDigest.kt` — pure digest helpers (`prettyHeaterLabel`, `orderedHeaterKeys`, `heaterValueText`, `homedText`, `spoolDigestValue`) + `HomeDigest` composable + `DigestRow` composable.
- `app/src/test/java/works/mees/dinghy/ui/printstatus/HomeDigestTest.kt` — unit tests for the pure helpers.
- `app/src/test/java/works/mees/dinghy/state/PrinterStateReducerMotorsTest.kt` — reducer + motion-stepper tests (mirrors `PrinterStateReducerOutputsTest.kt`).

**Modify:**
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` — add `motorsEnabled: Boolean?`.
- `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` — parse `stepper_enable.steppers` → `motorsEnabled`; add `EXTRUDER_STEPPER` regex.
- `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` — add `stepper_enable` to `V1_SUBSCRIBE_CORE`.
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — add `stepper_enable` to `touchesControlPlane`.
- `app/src/main/java/works/mees/dinghy/theme/DinghyType.kt` — add `focusHeroLabel` role (+ to `all`).
- `app/src/test/java/works/mees/dinghy/theme/DinghyTypeTest.kt` — assert `focusHeroLabel` shape.
- `app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt` — assert `stepper_enable` is subscribed.
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt` — branch `HomeFocus`; extract legacy glance into `GlanceBlock`; accept `motorsEnabled` + `heaterColors`.
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` — collect per-printer trace colors; pass `state.motorsEnabled` + colors into `HomeFocus`.

---

## Task 1: `motorsEnabled` state field

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterState.kt`

- [ ] **Step 1: Add the field**

In `PrinterState` (after `homedAxes`, ~line 77), add:

```kotlin
    /**
     * Any MOTION stepper enabled (`stepper_enable.steppers`, extruder steppers excluded). `null` =
     * never reported (object absent / not yet seen) → the home digest hides the Motors row. Only set
     * when a diff carries `stepper_enable` (retain-on-absent merge; R-CDX-1).
     */
    val motorsEnabled: Boolean? = null,
```

- [ ] **Step 2: Verify it compiles**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL (new field has a default, so no call site breaks).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/PrinterState.kt
git commit -m "feat(state): add PrinterState.motorsEnabled field"
```

---

## Task 2: Reducer parse for `stepper_enable` (motion steppers only)

**Files:**
- Test: `app/src/test/java/works/mees/dinghy/state/PrinterStateReducerMotorsTest.kt`
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/works/mees/dinghy/state/PrinterStateReducerMotorsTest.kt`:

```kotlin
package works.mees.dinghy.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson

/**
 * R-CDX-1/R-CDX-7: stepper_enable → motorsEnabled. Motion steppers only (extruder steppers excluded);
 * retain-on-absent merge; null until first reported.
 */
class PrinterStateReducerMotorsTest {

    private fun diff(json: String) = MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun motorsEnabledDefaultsToNull() {
        assertNull(PrinterState().motorsEnabled)
    }

    @Test
    fun anyMotionStepperEnabledSetsTrue() {
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": false, "stepper_y": true, "stepper_z": false } } }"""),
        )
        assertEquals(true, s.motorsEnabled)
    }

    @Test
    fun allMotionSteppersDisabledSetsFalse() {
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": false, "stepper_y": false, "stepper_z": false } } }"""),
        )
        assertEquals(false, s.motorsEnabled)
    }

    @Test
    fun extruderSteppersAreIgnored() {
        // Only extruder steppers enabled → motion motors are OFF.
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": false, "extruder": true, "extruder1": true } } }"""),
        )
        assertEquals(false, s.motorsEnabled)
    }

    @Test
    fun extraZSteppersCount() {
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": false, "stepper_z1": true } } }"""),
        )
        assertEquals(true, s.motorsEnabled)
    }

    @Test
    fun absentStepperEnableRetainsPriorValue() {
        val enabled = reduceDiff(
            PrinterState(),
            diff("""{ "stepper_enable": { "steppers": { "stepper_x": true } } }"""),
        )
        // A later, unrelated diff must NOT clear motorsEnabled (retain-on-absent).
        val after = reduceDiff(enabled, diff("""{ "heater_bed": { "temperature": 41.2 } }"""))
        assertEquals(true, after.motorsEnabled)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *PrinterStateReducerMotorsTest*" | tr -d '\r'`
Expected: compiles; `anyMotionStepperEnabledSetsTrue` etc. FAIL (`motorsEnabled` stays null); `motorsEnabledDefaultsToNull` passes.

- [ ] **Step 3: Add the regex + parse to the reducer**

In `PrinterStateReducer.kt`, beside the existing `EXTRUDER_N` (~line 320), add:

```kotlin
/** Matches an EXTRUDER stepper name (`extruder`, `extruder1`, …) — excluded from the motion-motor check. */
private val EXTRUDER_STEPPER = Regex("""extruder\d*""")
```

Add the import at the top (with the other `kotlinx.serialization.json` imports):

```kotlin
import kotlinx.serialization.json.JsonPrimitive
```

In `applyStatus`, right AFTER the `pause_resume` block (~line 170, before the `--- Phase-9 calibration` comment), add:

```kotlin
    // stepper_enable → motorsEnabled (R-CDX-1): MOTION steppers only (exclude extruder steppers).
    // Update-on-present: only when this diff carries `stepper_enable` do we recompute; an absent object
    // RETAINS the prior value (null until first reported). Flexible to any motor topology — anything not
    // named like an extruder stepper (stepper_x/y/z, stepper_z1/z2, dual_carriage, …) is a motion stepper.
    status.objectOrNull("stepper_enable")?.objectOrNull("steppers")?.let { steppers ->
        val anyMotionEnabled = steppers.entries.any { (name, value) ->
            if (name.matches(EXTRUDER_STEPPER)) return@any false
            (value as? JsonPrimitive)?.booleanOrNull == true
        }
        s = s.copy(motorsEnabled = anyMotionEnabled)
    }
```

Add the import for the primitive accessor if not already present (it is used elsewhere via the helper, but the inline cast needs it):

```kotlin
import kotlinx.serialization.json.booleanOrNull
```

(`booleanOrNull` on `JsonPrimitive?` is already imported at the top — line 9 — confirm; if so, skip the duplicate.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *PrinterStateReducerMotorsTest*" | tr -d '\r'`
Expected: PASS (all 6).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt app/src/test/java/works/mees/dinghy/state/PrinterStateReducerMotorsTest.kt
git commit -m "feat(state): reduce stepper_enable into motorsEnabled (motion steppers only)"
```

---

## Task 3: Subscribe to `stepper_enable` + control-plane publish

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt`
- Modify: `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt`
- Test: `app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt`

- [ ] **Step 1: Write the failing derive test**

In `DeriveCapabilitiesTest.kt`, add a test method inside the class:

```kotlin
    @Test
    fun stepperEnableIsSubscribedWhenPresent() {
        val set = deriveSubscribeSet(listOf("webhooks", "toolhead", "extruder", "stepper_enable"))
        assertTrue("stepper_enable must be subscribed when the printer defines it", "stepper_enable" in set)
    }

    @Test
    fun stepperEnableAbsentIsNotSubscribed() {
        val set = deriveSubscribeSet(listOf("webhooks", "toolhead", "extruder"))
        assertFalse("never subscribe an object the printer lacks (A3)", "stepper_enable" in set)
    }
```

- [ ] **Step 2: Run to verify the first fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *DeriveCapabilitiesTest*" | tr -d '\r'`
Expected: `stepperEnableIsSubscribedWhenPresent` FAILS (not yet in core); `stepperEnableAbsentIsNotSubscribed` passes.

- [ ] **Step 3: Add `stepper_enable` to the core subscribe set**

In `DeriveCapabilities.kt`, inside `V1_SUBSCRIBE_CORE` (~after the `"toolhead",` line), add:

```kotlin
    "stepper_enable",  // motion-stepper enable map → PrinterState.motorsEnabled (home digest)
```

- [ ] **Step 4: Add to the control-plane immediate-publish trigger**

In `PrinterStateStore.kt`, in `touchesControlPlane` (~line 438), add before `return false`:

```kotlin
        if (diff.containsKey("stepper_enable")) return true
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *DeriveCapabilitiesTest*" | tr -d '\r'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt
git commit -m "feat(state): subscribe stepper_enable + immediate control-plane publish"
```

---

## Task 4: `focusHeroLabel` type role

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/theme/DinghyType.kt`
- Test: `app/src/test/java/works/mees/dinghy/theme/DinghyTypeTest.kt`

- [ ] **Step 1: Write the failing test**

In `DinghyTypeTest.kt`, add:

```kotlin
    @Test
    fun focusHeroLabelIsGeistHeroEnvelope() {
        val role = DinghyType.focusHeroLabel
        assertEquals(TypeRole.Ui, role.role)        // Geist (labels are UI text)
        assertEquals(40f, role.maxSp)
        assertEquals(15f, role.minSp)
        assertTrue("focusHeroLabel must be registered in DinghyType.all", DinghyType.focusHeroLabel in DinghyType.all)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *DinghyTypeTest*" | tr -d '\r'`
Expected: FAIL to compile (`focusHeroLabel` unresolved).

- [ ] **Step 3: Add the role**

In `DinghyType.kt`, after `focusHero` (~line 39), add:

```kotlin
    /** Geist (UI) mirror of [focusHero]'s shrink-to-fit envelope — for a Focus-hero LABEL beside a Mono value. */
    val focusHeroLabel = TextRole(TypeRole.Ui, baseSp = 40f, weight = FontWeight.SemiBold, maxSp = 40f, minSp = 15f)
```

And add it to the `all` list:

```kotlin
    val all: List<TextRole> = listOf(
        screenTitle, focusHeader, listLabel, buttonLabel, body, caption,
        focusHero, focusHeroLabel, statValue, dataInline, dataMeta, consoleLine,
    )
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *DinghyTypeTest*" | tr -d '\r'`
Expected: PASS (the `maxSp != null` exemption keeps `everyFixedRoleSitsOnASanctionedRampTier` green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/theme/DinghyType.kt app/src/test/java/works/mees/dinghy/theme/DinghyTypeTest.kt
git commit -m "feat(theme): add focusHeroLabel type role (Geist hero envelope)"
```

---

## Task 5: Pure digest helpers

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/printstatus/HomeDigest.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/printstatus/HomeDigestTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/works/mees/dinghy/ui/printstatus/HomeDigestTest.kt`:

```kotlin
package works.mees.dinghy.ui.printstatus

import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.ui.spool.ActiveSpoolCardState

class HomeDigestTest {

    @Test
    fun prettyHeaterLabelStripsPrefixAndCapitalizes() {
        assertEquals("Extruder", prettyHeaterLabel("extruder"))
        assertEquals("Bed", prettyHeaterLabel("heater_bed"))
        assertEquals("Chamber", prettyHeaterLabel("heater_generic chamber"))
        assertEquals("Extruder1", prettyHeaterLabel("extruder1"))
    }

    @Test
    fun orderedHeaterKeysPutsExtruderThenBedThenSorted() {
        val heaters = persistentMapOf(
            "heater_generic chamber" to HeaterState(),
            "heater_bed" to HeaterState(),
            "extruder1" to HeaterState(),
            "extruder" to HeaterState(),
        )
        assertEquals(
            listOf("extruder", "heater_bed", "extruder1", "heater_generic chamber"),
            orderedHeaterKeys(heaters),
        )
    }

    @Test
    fun orderedHeaterKeysOmitsAbsentHeadHeaters() {
        val heaters = persistentMapOf("heater_generic chamber" to HeaterState())
        assertEquals(listOf("heater_generic chamber"), orderedHeaterKeys(heaters))
    }

    @Test
    fun heaterValueTextRoundsBothToIntegers() {
        assertEquals("151/220", heaterValueText(HeaterState(temperature = 150.6, target = 220.0)))
        assertEquals("25/0", heaterValueText(HeaterState(temperature = 25.2, target = 0.0)))
    }

    @Test
    fun homedTextCanonicalizesXyzOrderOrNone() {
        assertEquals("XYZ", homedText("xyz"))
        assertEquals("XYZ", homedText("yzx"))   // R-CDX-4 NIT: force X/Y/Z order regardless of input order
        assertEquals("XY", homedText("yx"))
        assertEquals("NONE", homedText(""))
    }

    @Test
    fun spoolDigestValueHiddenWhenSpoolmanAbsent() {
        assertNull(spoolDigestValue(spoolmanPresent = false, state = ActiveSpoolCardState.NoActive))
    }

    @Test
    fun spoolDigestValueWeightWhenLoaded() {
        val spool = SpoolmanSpool(id = 1, remainingWeight = 848.4)
        assertEquals("848g", spoolDigestValue(spoolmanPresent = true, state = ActiveSpoolCardState.Loaded(spool)))
    }

    @Test
    fun spoolDigestValueNaForEveryNonLoadedVariant() {
        assertEquals("N/A", spoolDigestValue(true, ActiveSpoolCardState.NoActive))
        assertEquals("N/A", spoolDigestValue(true, ActiveSpoolCardState.Disconnected))
        assertEquals("N/A", spoolDigestValue(true, ActiveSpoolCardState.Loading(5)))
        assertEquals("N/A", spoolDigestValue(true, ActiveSpoolCardState.Loaded(SpoolmanSpool(id = 1, remainingWeight = null))))
    }
}
```

> NOTE before writing: confirm `SpoolmanSpool`'s constructor params. If `SpoolmanSpool(id = 1, remainingWeight = …)` does not compile (other required fields), build the fixture with the minimal required args — grep `data class SpoolmanSpool` in `app/src/main/java/works/mees/dinghy/spool/`. Adjust the two `SpoolmanSpool(...)` calls only; the assertions stay.

- [ ] **Step 2: Run to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *HomeDigestTest*" | tr -d '\r'`
Expected: FAIL to compile (helpers undefined).

- [ ] **Step 3: Create the helpers**

Create `app/src/main/java/works/mees/dinghy/ui/printstatus/HomeDigest.kt` with ONLY the pure helpers for now (the composable is added in Task 6):

```kotlin
package works.mees.dinghy.ui.printstatus

import kotlin.math.roundToInt
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.ui.spool.ActiveSpoolCardState

/**
 * Pure formatting for the home Focus state digest (non-printing states). All host-testable; the
 * `HomeDigest` composable (same file) consumes them. See
 * docs/superpowers/specs/2026-06-16-standby-focus-digest-design.md.
 */

/** Prettify a Klipper heater key: drop the `heater_generic `/`heater_` prefix, capitalize. Never invent a synonym. */
internal fun prettyHeaterLabel(key: String): String {
    val stripped = when {
        key.startsWith("heater_generic ") -> key.removePrefix("heater_generic ")
        key.startsWith("heater_") -> key.removePrefix("heater_")
        else -> key
    }
    return stripped.replaceFirstChar { it.uppercase() }
}

/**
 * Canonical heater order (R-CDX-2) driving BOTH row order and the color `localIndex`: `extruder` first
 * (→ accent), then `heater_bed`, then all remaining keys sorted. Keeps each heater's color stable.
 */
internal fun orderedHeaterKeys(heaters: Map<String, HeaterState>): List<String> {
    val keys = heaters.keys
    val head = listOf("extruder", "heater_bed").filter { it in keys }
    val rest = (keys - head.toSet()).sorted()
    return head + rest
}

/** Active heaters (target > 0) in canonical order. Empty → render the single `Heaters OFF` row. */
internal fun activeHeaterKeys(heaters: Map<String, HeaterState>): List<String> =
    orderedHeaterKeys(heaters).filter { (heaters[it]?.target ?: 0.0) > 0.0 }

/** `current/target`, both rounded to integers (no degree symbol). */
internal fun heaterValueText(h: HeaterState): String =
    "${h.temperature.roundToInt()}/${h.target.roundToInt()}"

/** Homed axes in canonical X/Y/Z order (R-CDX-4), any extra axes appended; blank → `NONE`. */
internal fun homedText(homedAxes: String): String {
    val lower = homedAxes.lowercase()
    val ordered = "xyz".filter { it in lower } + lower.filter { it !in "xyz" }
    return ordered.uppercase().ifBlank { "NONE" }
}

/**
 * Spool digest value (R-CDX-4). `null` → hide the row (Spoolman not configured). Otherwise `{weight}g`
 * only for a Loaded spool with a numeric remaining weight; every other variant → `N/A`.
 */
internal fun spoolDigestValue(spoolmanPresent: Boolean, state: ActiveSpoolCardState): String? {
    if (!spoolmanPresent) return null
    val weight = (state as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight
    return weight?.let { "${it.roundToInt()}g" } ?: "N/A"
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon --tests *HomeDigestTest*" | tr -d '\r'`
Expected: PASS (8).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/HomeDigest.kt app/src/test/java/works/mees/dinghy/ui/printstatus/HomeDigestTest.kt
git commit -m "feat(printstatus): pure home-digest formatting helpers"
```

---

## Task 6: The `HomeDigest` composable + `DigestRow`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/HomeDigest.kt`

- [ ] **Step 1: Add the composables**

Append to `HomeDigest.kt` (and add the imports at the top of the file):

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableMap
import works.mees.dinghy.R
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.ui.spool.ActiveSpoolCardState
```

```kotlin
/**
 * The non-printing home Focus digest: Heaters (OFF, or one row per active heater) · Motors · Homed ·
 * Spool. Label start-aligned (Geist [DinghyType.focusHeroLabel]) · value end-aligned (Mono
 * [DinghyType.focusHero]). Heater values colored from [heaterColors] (per-printer trace override) else
 * the accent-first [seriesColor] by canonical index; other values neutral.
 *
 * @param heaterColors per-heater override colors keyed by heater object name (stable ImmutableMap).
 */
@Composable
internal fun HomeDigest(
    state: PrinterState,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    heaterColors: ImmutableMap<String, Color>,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val order = orderedHeaterKeys(state.heaters)
    val active = activeHeaterKeys(state.heaters)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Heaters: OFF (single row) or one row per active heater, colored to its trace.
        if (active.isEmpty()) {
            DigestRow(stringResource(R.string.printstatus_digest_heaters), stringResource(R.string.printstatus_digest_off), t.text)
        } else {
            active.forEach { key ->
                val heater = state.heaters[key] ?: return@forEach
                val color = heaterColors[key] ?: t.seriesColor(order.indexOf(key).coerceAtLeast(0))
                DigestRow(prettyHeaterLabel(key), heaterValueText(heater), color)
            }
        }

        // Motors: hidden when motorsEnabled is null (never reported).
        state.motorsEnabled?.let { on ->
            val value = if (on) stringResource(R.string.printstatus_digest_on) else stringResource(R.string.printstatus_digest_off)
            DigestRow(stringResource(R.string.printstatus_digest_motors), value, t.text)
        }

        // Homed.
        DigestRow(stringResource(R.string.printstatus_digest_homed), homedText(state.homedAxes), t.text)

        // Spool (only when Spoolman configured).
        spoolDigestValue(spoolmanPresent, activeSpoolCardState)?.let { value ->
            DigestRow(stringResource(R.string.printstatus_digest_spool), value, t.text)
        }
    }
}

/** One digest line: Geist label at start, Mono value at end (right edges flush). */
@Composable
private fun DigestRow(label: String, value: String, valueColor: Color) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = t.text2, style = DinghyType.focusHeroLabel.toTextStyle(t))
        Text(value, color = valueColor, style = DinghyType.focusHero.toTextStyle(t))
    }
}
```

Add the remaining imports used above (`androidx.compose.ui.res.stringResource`, `androidx.compose.ui.unit.dp`) to the file's import block.

> AS-BUILT (owner 2026-06-16, supersedes the original fixed-size note): the digest renders as MEASURED uniform-shrink FULL-WIDTH rows, not fixed-size rows (which wrapped the value on narrow portrait, e.g. moto medium). `HomeDigest` wraps a `BoxWithConstraints(modifier.fillMaxWidth())`, measures the widest row's label+value at base size via `rememberTextMeasurer`, and computes the single largest font size (linear scaling, floor = `focusHero.minSp`) at which the widest row fits the Focus width on one line. Every row is `Row(fillMaxWidth)` with the label in `Modifier.weight(1f)` (start, ellipsis-on-overflow safety) + a fixed `Spacer` + the value at the end — so each value's right edge is **flush to the Focus frame's right edge** (owner: values must hug the frame edge, not a content-width column). Same uniform size on all rows. The size is applied via a new sanctioned `TextRole.toTextStyle(t, sizeSp)` overload in the allowlisted `DinghyTextStyle.kt`, so `HomeDigest.kt` carries NO inline `fontSize=` (FontConformanceTest, ENFORCE=true, stays green). The `maxSp=40f` on `focusHeroLabel` is still REQUIRED — it exempts the role from `DinghyTypeTest.everyFixedRoleSitsOnASanctionedRampTier`.

- [ ] **Step 2: Add the string resources**

In `app/src/main/res/values/strings.xml`, add (search for existing `printstatus_` strings and place alongside):

```xml
    <string name="printstatus_digest_heaters">Heaters</string>
    <string name="printstatus_digest_motors">Motors</string>
    <string name="printstatus_digest_homed">Homed</string>
    <string name="printstatus_digest_spool">Spool</string>
    <string name="printstatus_digest_on">ON</string>
    <string name="printstatus_digest_off">OFF</string>
```

- [ ] **Step 3: Verify it compiles**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/HomeDigest.kt app/src/main/res/values/strings.xml
git commit -m "feat(printstatus): HomeDigest composable + digest strings"
```

---

## Task 7: Branch `HomeFocus` (printing → glance, else → digest)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt`

- [ ] **Step 1: Extract the legacy glance + add the branch**

In `PrintStatusFocus.kt`, change the `HomeFocus` signature to accept the heater colors (it already receives `state`, which now carries `motorsEnabled`):

```kotlin
@Composable
internal fun HomeFocus(
    state: PrinterState,
    printerName: String?,
    isMultiPrinter: Boolean,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    heaterColors: kotlinx.collections.immutable.ImmutableMap<String, androidx.compose.ui.graphics.Color>,
    onEmergencyStop: (() -> Unit)?,
    uDp: Dp,
) {
```

Replace the `BoxWithConstraints { … }` body's glance `Column` (the block that renders `GlanceRow(... nozzle/bed/glance/spool ...)`) with a branch. The watermark `Image` stays. The new body inside `BoxWithConstraints`:

```kotlin
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Brand watermark: 30% of the smaller edge, bottom-end, faint accent2 tint.
            val markSize = minOf(maxWidth, maxHeight) * 0.30f
            Image(
                painter = painterResource(R.drawable.jiib_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(t.accent2),
                alpha = 0.45f,
                modifier = Modifier.size(markSize).align(Alignment.BottomEnd),
            )
            // Glance only for a LIVE print (Printing/Paused) that is NOT a Klippy fault. Error/Shutdown
            // are non-printing treatments (digest) even if printState is a stale Printing — matches the
            // title precedence in homeStateLabelRes (R-CDX-2 blocker fix). isPrinting (for the e-stop) is
            // unchanged; this is a SEPARATE branch var.
            val klippyFault = state.klippyState == KlippyState.Shutdown || state.klippyState == KlippyState.Error
            val showGlance = isPrinting && !klippyFault
            if (showGlance) {
                // Printing/Paused KEEP today's glance (current temps). Separate future treatment.
                GlanceBlock(state, spoolmanPresent, spoolRemaining, Modifier.align(Alignment.TopStart))
            } else {
                // Non-printing (incl. Klippy Error/Shutdown): the state digest.
                HomeDigest(
                    state = state,
                    spoolmanPresent = spoolmanPresent,
                    activeSpoolCardState = activeSpoolCardState,
                    heaterColors = heaterColors,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
```

Extract the existing glance `Column` (Nozzle/Bed/glance/spool) verbatim into a new private composable so the printing path is unchanged behavior:

```kotlin
/** The legacy current-temp glance, retained for the Printing/Paused Focus path only. */
@Composable
private fun GlanceBlock(
    state: PrinterState,
    spoolmanPresent: Boolean,
    spoolRemaining: Double?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    val glance = selectGlanceSensor(state.temperatureSensors)
    Column(
        modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        GlanceRow(stringResource(R.string.printstatus_nozzle_label), tempActive(nozzle), t.seriesColor(0))
        GlanceRow(stringResource(R.string.printstatus_bed_label), tempActive(bed), t.seriesColor(1))
        glance?.let { GlanceRow(glanceLabel(it.name), fmt(it.temperature), t.text) }
        if (spoolmanPresent && spoolRemaining != null) {
            GlanceRow(stringResource(R.string.printstatus_spool_label), "${spoolRemaining.roundToInt()} g", t.text)
        }
    }
}
```

Keep the `spoolRemaining`/`isPrinting`/`stateLabel`/`title` derivations and the `FocusFrame(...)` wrapper as-is (`spoolRemaining` is still derived in `HomeFocus` and passed to `GlanceBlock`). `GlanceRow` and `glanceLabel` stay in the file (now used only by `GlanceBlock`). Add the import `works.mees.dinghy.state.KlippyState` (used by the new `klippyFault` check).

- [ ] **Step 2: Verify it compiles**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: FAILS at the `PrintStatusScreen` call site (HomeFocus now needs `heaterColors`) — that's wired in Task 8. The `PrintStatusFocus.kt` file itself should be internally consistent.

- [ ] **Step 3: Commit (compile fix lands with Task 8)**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt
git commit -m "feat(printstatus): branch HomeFocus printing-glance vs non-printing digest"
```

---

## Task 8: Wire per-printer trace colors through `PrintStatusScreen`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`

> ⚠ `HomeFocus` is called inside the PRIVATE shared `PrintStatusContent` (`PrintStatusScreen.kt:275`, the `focus = { HomeFocus(...) }` slot ~line 298), reached by BOTH the live `PrintStatusScreen(container)` overload (call site ~line 188) and the stateless preview overload (call site ~line 248). So `heaterColors` must be (a) a new `PrintStatusContent` parameter, (b) passed to `HomeFocus` inside it, (c) supplied at BOTH call sites — the real value live, `persistentMapOf()` for previews.

- [ ] **Step 1: Add `heaterColors` to `PrintStatusContent` and pass it to `HomeFocus`**

In the `PrintStatusContent` signature (~line 275), add the parameter after `activeSpoolCardState`:

```kotlin
    activeSpoolCardState: ActiveSpoolCardState,
    heaterColors: kotlinx.collections.immutable.ImmutableMap<String, androidx.compose.ui.graphics.Color>,
```

In the `focus = { HomeFocus(...) }` slot (~line 298), add the argument alongside `activeSpoolCardState = activeSpoolCardState,`:

```kotlin
                heaterColors = heaterColors,
```

- [ ] **Step 2: Supply it at the preview (stateless) call site**

At the `PrintStatusContent(...)` call inside the stateless `PrintStatusScreen(state = …)` overload (~line 248), add:

```kotlin
            heaterColors = kotlinx.collections.immutable.persistentMapOf(),
```

- [ ] **Step 3: Collect + supply the real colors in the live overload**

In the live `PrintStatusScreen(container, …)` overload, after the existing `collectAsStateWithLifecycle` calls (~line 90), add the per-printer trace-color flow (mirrors `TemperatureHolder`):

```kotlin
    // Per-printer trace-color overrides (R-CDX-3) — same source as the Temperature graph. Used to color
    // heater values in the home digest; the digest falls back to seriesColor when a heater has no override.
    val heaterColors by remember(container) {
        container.activeProfileId.flatMapLatest { pid ->
            if (pid == null) kotlinx.coroutines.flow.flowOf(persistentMapOf<String, androidx.compose.ui.graphics.Color>())
            else container.traceStylePrefs.traceColors(pid).map { argbByName ->
                argbByName.entries.fold(persistentMapOf<String, androidx.compose.ui.graphics.Color>()) { acc, (name, argb) ->
                    acc.put(name, androidx.compose.ui.graphics.Color(argb))
                }
            }
        }
    }.collectAsStateWithLifecycle(initialValue = persistentMapOf())
```

Then at the live `PrintStatusContent(...)` call (~line 188), add the argument alongside `activeSpoolCardState = activeSpoolCardState,`:

```kotlin
            heaterColors = heaterColors,
```

Add imports at the top of the file:

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
```

(`remember` / `map` may already be imported — dedupe. `flatMapLatest` needs `@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)` on the live overload function, matching how `TemperatureHolder` opts in — add it if the compiler requires.)

> The `collectAsStateWithLifecycle(initialValue = persistentMapOf())` gives `heaterColors` the type `PersistentMap<String, Color>`, which IS an `ImmutableMap<String, Color>` — matching the `PrintStatusContent`/`HomeFocus`/`HomeDigest` param type. No cast needed.

- [ ] **Step 4: Build the whole debug variant**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full unit suite**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL (incl. `FontConformanceTest`, `DinghyTypeTest`, the new tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
git commit -m "feat(printstatus): wire per-printer heater trace colors into HomeFocus"
```

---

## Task 9: On-device verification (flox + moto)

**Files:** none (verification only).

- [ ] **Step 1: Build + install on BOTH devices**

The split-ABI debug build emits both slices. Install the matching APK on each (`flox` = `0a64b42e` armeabi-v7a; `moto` = `ZY22LBDRM9` arm64-v8a). Force a fresh build to avoid the stale-APK trap:

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon --rerun-tasks" | tr -d '\r'`
Then install each device's ABI APK via `adb -s <id> install -r <path>` and confirm the APK mtime is newer than the last commit.

- [ ] **Step 2: Verify the digest on each device**

On the home/PrintStatus screen (idle), confirm:
- Cold standby shows `Heaters OFF`, `Motors OFF` (or hidden if `stepper_enable` absent), `Homed NONE`/actual, `Spool …g`/`N/A`/absent.
- Integer temps, no decimals; labels start-aligned (Geist), values end-aligned (Mono).
- Preheat → the active heater(s) appear as `Extruder 150/220` etc., colored to match the Temperature graph trace.
- Disable motors (Move → Disable) → `Motors OFF`; a jog/home → `Motors ON`.
- Check both portrait + landscape and both a colorful and a high-contrast theme.

Capture a screenshot from each device (`adb -s <id> exec-out screencap -p > /tmp/dd_digest_<dev>.png`) for the owner.

- [ ] **Step 3: Owner eyeball**

Present screenshots; the owner approves the look or names tweaks (iterate on-device per the established loop).

- [ ] **Step 4: Final commit (if tweaks were applied)**

```bash
git add -A && git commit -m "fix(printstatus): on-device digest polish"
```

---

## Self-Review Notes (author)

- **Spec coverage:** scope/branch (Task 7), row catalog + formatting (Tasks 5–6), heater labels (Task 5), motors plumbing + control-plane (Tasks 1–3), `focusHeroLabel` (Task 4), color override+fallback (Tasks 6, 8), Spool variant rules (Task 5), tests incl. derive/reducer/type (Tasks 2–5), on-device (Task 9). Error/Shutdown → digest renders last-known (no special code; verified Task 9).
- **Known caveat to verify on-device (Task 9):** heater override color keys are the heater object names (`extruder`, `heater_bed`). If the Temperature graph stores trace colors under a different key for heaters, the override simply won't match and the digest falls back to `seriesColor` (still correct, just not the user's custom color). Confirm the key matches; adjust the lookup key if not.
- **Uniform-size contract** is left as per-text shrink-to-fit for v1 (noted in Task 6); revisit only if Task 9 shows uneven rows.
