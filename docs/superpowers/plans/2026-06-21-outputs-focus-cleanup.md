# Outputs Focus Cleanup + RGB/RGBW Slider Rework — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every Outputs Focus surface conform to one shape — value/control rows fill the body, action buttons pin to the bottom in a real `FootButtonBar` — and replace the RGB color wheel with theme-style H/S/V(+W) sliders.

**Architecture:** The Outputs screen renders a per-output control surface inside a `FocusFrame` (`OutputFocusControl` → `FocusScrubberSurface` / `FocusLedSurface` / `OutputToggleControl`). We center the shared `Scrubber`'s name-less value, extend `HsvSliders` with an optional White track, rewrite the switch + LED surfaces, route all foot actions through `FootButtonBar` (icons `power`/`power_off`), fix the LED command path to send full R/G/B/W state, remove the redundant in-Focus Back everywhere, and delete `ColorWheel`.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4 host tests (`app/src/test`), kotlinx-coroutines-test, the project's Windows-side Gradle wrapper.

## Global Constraints

- **minSdk 23**; no new dependency may raise the floor. (No new deps in this plan.)
- **Build Windows-side only:** `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"` — `./gradlew` does NOT run from WSL. Pipe through `tr -d '\r'`; the exit code is authoritative.
- **Force fresh builds before any UAT/test gate** with `--rerun-tasks` (Gradle UP-TO-DATE serves stale APKs/tests — [[dinghy-stale-apk-uat-gate]]).
- **Icon law:** NEVER invent or pick a glyph. The only glyphs this plan introduces are owner-chosen: `power` (On) and `power_off` (Off). New glyphs go in `DinghyIcons.kt` as a `val` AND in the `all` list, then `tools/verify_ligatures.py` must pass.
- **Type law:** no inline `fontFamily=`/`fontSize=`; use `DinghyType` roles (`FontConformanceTest` fails the build otherwise).
- **Commit message footer (every commit):**
  ```
  Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01JGVJU8WHgiVgYifChzHFfb
  ```
  Commit with `git -c core.filemode=false commit` (drvfs chmod trap).
- **Spec:** `docs/superpowers/specs/2026-06-21-outputs-focus-cleanup-design.md` (Codex-reviewed).

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | Icon registry | Add `Power`, `PowerOff` (val + `all`) |
| `app/src/main/java/works/mees/dinghy/control/ControlSpecs.kt` | Control catalog | Add `outputOn`; give `outputOff` the `power_off` icon |
| `app/src/main/java/works/mees/dinghy/designsystem/components/Scrubber.kt` | Shared scrubber | Center value when `name` blank (non-bare header only) |
| `app/src/main/java/works/mees/dinghy/designsystem/HsvSliders.kt` | HSV slider stack | Optional 4th White track |
| `app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt` | Outputs VM/holder | `OutputRowVm.ledChannels`; full-tuple LED pending |
| `app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt` | Per-output Focus surfaces | LED→HsvSliders, full-state dispatch, foot→FootButtonBar, drop in-Focus Back |
| `app/src/main/java/works/mees/dinghy/ui/outputs/OutputToggleControl.kt` | Switch surface | Rewrite: state readout body + `[On][Off]` foot |
| `app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt` | Outputs screen | Drop `onBack` thread to `OutputFocusControl` |
| `app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt` | (deleted) | Delete |
| `docs/ui_design/LAYOUT.md`, `COMPONENTS.md`, `.claude/skills/sketch-findings-dinghy-display/SKILL.md` | Design law | Retire UAT-5 ">1U ColorWheel" exception |

---

### Task 1: Register `power`/`power_off` glyphs + On/Off control specs

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Modify: `app/src/main/java/works/mees/dinghy/control/ControlSpecs.kt:91-97` (`outputOff`) + its `all` list
- Test: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt` (existing — runs as-is), `tools/verify_ligatures.py`

**Interfaces:**
- Produces: `DinghyIcons.Power` (ligature `"power"`), `DinghyIcons.PowerOff` (ligature `"power_off"`); `ControlSpecs.outputOn` (label `R.string.output_on`, icon `Power`, intent `Go`, Button); `ControlSpecs.outputOff` now has `icon = DinghyIcons.PowerOff` (intent stays `Warn`).

- [ ] **Step 1: Add the two glyphs to the registry.** In `DinghyIcons.kt`, after `SortDesc` (line ~334) add:

```kotlin
// --- Outputs foot-bar actions (owner-chosen 2026-06-21; icon law). On = power, Off = power_off.
/** Outputs switch/scrubber/LED foot: turn the output ON. */
val Power = DinghyIcon(IconRef.Ligature("power"), alternate = "power")
/** Outputs switch/scrubber/LED foot: turn the output OFF. */
val PowerOff = DinghyIcon(IconRef.Ligature("power_off"), alternate = "power_off")
```

- [ ] **Step 2: Add them to the `all` list.** In the `val all = listOf(` block (line ~424), add `Power, PowerOff,` near the other recently-added glyphs (e.g. after `SortAsc, SortDesc,`).

- [ ] **Step 3: Verify the bundled font carries both ligatures.**

Run: `python tools/verify_ligatures.py`
Expected: PASS with `power` and `power_off` resolved (both are standard Material Symbols; the bundled font was un-frozen recently so they are expected present). **If EITHER is missing, STOP and ASK the owner** — do NOT fall back to a `Drawable` IconRef: `FootAction` hard-requires `IconRef.Ligature` (`require(icon.primary is IconRef.Ligature)`), so a drawable cannot drive `FootButtonBar`. The owner must pick a different ligature or approve a `FootAction` API change. Do not substitute a glyph yourself. [Codex BLOCKER 2]

- [ ] **Step 4: Add `outputOn` + give `outputOff` an icon.** In `ControlSpecs.kt`, change `outputOff` (line 91-97) `icon = null` → `icon = DinghyIcons.PowerOff`, and add a sibling spec:

```kotlin
val outputOn = ControlSpec(
    key = ControlKey("output.on"),
    labelRes = R.string.output_on,
    icon = DinghyIcons.Power,
    intent = Intent.Go, // R5: the expected "turn it on" action
    type = ControlType.Button,
)
```

Add `outputOn,` to the `ControlSpecs` `all`/registry list (next to `outputOff,`).

- [ ] **Step 5: Run the icon + control-spec tests.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.designsystem.icons.DinghyIconsTest' --tests 'works.mees.dinghy.control.ControlCatalogDriftTest' --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: PASS. `ControlCatalogDriftTest` requires every referenced control icon to be registered — it must stay green after adding `outputOn` + giving `outputOff` an icon. [Codex SHOULD-FIX 8] (No existing test asserts `outputOff.icon == null`, per Codex — but if one surfaces, update it to expect `DinghyIcons.PowerOff`.)

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt \
        app/src/main/java/works/mees/dinghy/control/ControlSpecs.kt
git -c core.filemode=false commit -m "feat(outputs): register power/power_off glyphs + outputOn/outputOff foot specs"
```

---

### Task 2: Center the `Scrubber` value when the name is blank

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/Scrubber.kt:338-365` (non-bare horizontal header)

**Interfaces:**
- Consumes: nothing new.
- Produces: no signature change. Behavior: name-less horizontal scrubbers center their value+unit; named scrubbers keep name-left / value-right; `bare = true` callers are untouched (they skip the header entirely — Temperature/Extrude rely on this).

This is a pure-layout change; verification is build + preview + on-device (no host unit test can assert Compose alignment meaningfully).

- [ ] **Step 1: Edit the header `Row`.** Replace the header `Row` (lines 340-365) with:

```kotlin
// Header: name start · live value + dim unit end (sketch .row1). When name is BLANK
// (Outputs scrubber surfaces, white-only LED brightness) the value+unit is CENTERED instead
// of orphaned right-aligned. `bare = true` callers (Temperature/Extrude) skip this header.
Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = if (name.isBlank()) Arrangement.Center else Arrangement.Start,
) {
    if (name.isNotBlank()) {
        Text(
            text = name,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
            modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = fmt(working),
            color = t.text,
            style = DinghyType.focusHero.toTextStyle(t),
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                color = t.text3,
                style = DinghyType.dataInline.toTextStyle(t),
            )
        }
    }
}
```

(The named case keeps `Modifier.weight(1f)` on the name, which still pushes the value to the end under `Arrangement.Start`. The blank case drops the spacer `Box` so `Arrangement.Center` centers the value.)

- [ ] **Step 2: Compile.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/components/Scrubber.kt
git -c core.filemode=false commit -m "fix(scrubber): center value when name is blank (Outputs surfaces)"
```

---

### Task 3: Add an optional White track to `HsvSliders`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/HsvSliders.kt:38-58`

**Interfaces:**
- Produces: `HsvSliders(hue, sat, value, onMove, onSettle, white: Float? = null, onWhiteMove: (Float) -> Unit = {}, onWhiteSettle: (Float) -> Unit = {}, modifier)` — renders a 4th "W" track only when `white != null`. Existing call site (`ThemeScreen.ThemeSwatchEditor`) is source-compatible (new params default).

This is a visual addition; verification is build (defaults keep the theme call site compiling) + the LED surface usage in Task 5.

- [ ] **Step 1: Add the white params + track.** Change the signature and append the white track inside the `Column`:

```kotlin
@Composable
fun HsvSliders(
    hue: Float,
    sat: Float,
    value: Float,
    onMove: (Float, Float, Float) -> Unit,
    onSettle: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    white: Float? = null,
    onWhiteMove: (Float) -> Unit = {},
    onWhiteSettle: (Float) -> Unit = {},
) {
    val pure = Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Track("H", hue / 360f,
            Brush.horizontalGradient((0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }),
            onMove = { onMove(it * 360f, sat, value) }, onSettle = { onSettle(it * 360f, sat, value) })
        Track("S", sat,
            Brush.horizontalGradient(listOf(Color.hsv(((hue % 360f) + 360f) % 360f, 0f, value.coerceAtLeast(0.2f)), pure)),
            onMove = { onMove(hue, it, value) }, onSettle = { onSettle(hue, it, value) })
        Track("V", value,
            Brush.horizontalGradient(listOf(Color.Black, pure)),
            onMove = { onMove(hue, sat, it) }, onSettle = { onSettle(hue, sat, it) })
        if (white != null) {
            Track("W", white,
                Brush.horizontalGradient(listOf(Color.Black, Color.White)),
                onMove = onWhiteMove, onSettle = onWhiteSettle)
        }
    }
}
```

- [ ] **Step 2: Compile (confirms theme call site still builds).**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/HsvSliders.kt
git -c core.filemode=false commit -m "feat(hsvsliders): optional White track for RGBW LEDs"
```

---

### Task 4: `OutputRowVm.ledChannels` + full-tuple LED pending (logic, TDD)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt` (`OutputRowVm`, `OutputPending`, `markPending`, `reached`, `buildRow`)
- Test: `app/src/test/java/works/mees/dinghy/outputs/OutputsHolderTest.kt`

**Interfaces:**
- Produces:
  - `OutputRowVm.ledChannels: List<Float>?` — the strip's first-pixel `[r,g,b,w]` 0..1 (null for non-LED), so the LED surface can seed H/S/V + White from the *real* state (the existing `swatchColor` drops W).
  - `OutputPending.targetChannels: List<Double>?` — full r/g/b/w target for LEDs (null = scalar `target` for other families).
  - `markPending(objectKey, clampedWireTarget, targetChannels: List<Double>? = null)`.
- Consumes (in Task 5): `OutputRowVm.ledChannels`, `markPending(..., targetChannels = …)`.

- [ ] **Step 1: Write failing tests.** Use the file's REAL fixture style (per Codex SHOULD-FIX 6 — there is no `holderWith`/`rgbwLed`; the harness is `PrinterStateStore(backgroundScope)` + `OutputsHolder(backgroundScope, store)` + `store.setOutputDescriptors(...)` + `store.seed(PrinterState(outputs = …))`). First add an `rgbwLed()` descriptor helper next to `whiteOnlyLed()`:

```kotlin
private fun rgbwLed(key: String = "led strip", name: String = "strip") = OutputDescriptor(
    objectKey = key, family = "led", commandName = name, prettyName = name,
    pwm = false, servoAngleMax = 180f, readOnly = false,
    ledHasRgb = true, ledHasWhite = true,
)
```
(Match `whiteOnlyLed()`'s actual parameter list — copy it and flip `ledHasRgb`/`ledHasWhite` to true. Verify the exact `OutputDescriptor` LED-capability field names in the file before writing.)

Then add the two tests, building state the same way the existing LED tests in this file do:

```kotlin
@Test
fun ledRow_exposesRawChannelsIncludingWhite() = runTest {
    val store = PrinterStateStore(backgroundScope)
    val holder = OutputsHolder(backgroundScope, store)
    val led = rgbwLed()
    store.setOutputDescriptors(listOf(led))
    store.seed(PrinterState(outputs = mapOf(
        led.objectKey to OutputLiveValue(colorData = listOf(listOf(1.0, 0.0, 0.0, 0.5).toImmutableList()).toImmutableList())
    ).toImmutableMap()))
    runCurrent()
    val row = holder.rows.value.first { it.descriptor.objectKey == led.objectKey }
    assertEquals(listOf(1f, 0f, 0f, 0.5f), row.ledChannels)
}

@Test
fun ledPending_holdsWhenOnlySaturationChanges_sameMaxBrightness() = runTest {
    val store = PrinterStateStore(backgroundScope)
    val holder = OutputsHolder(backgroundScope, store)
    val led = rgbwLed()
    store.setOutputDescriptors(listOf(led))
    // Live = white-ish [1,1,1,0], max brightness 1.0.
    store.seed(PrinterState(outputs = mapOf(
        led.objectKey to OutputLiveValue(colorData = listOf(listOf(1.0, 1.0, 1.0, 0.0).toImmutableList()).toImmutableList())
    ).toImmutableMap()))
    runCurrent()
    // Command fully-saturated red at the SAME max brightness (1.0): with the old maxOrNull() compare
    // this would wrongly clear; the full-tuple compare must keep it pending.
    holder.markPending(led.objectKey, clampedWireTarget = 1.0, targetChannels = listOf(1.0, 0.0, 0.0, 0.0))
    runCurrent()
    assertTrue(holder.rows.value.first { it.descriptor.objectKey == led.objectKey }.busy)
}
```

(Confirm the exact store API names — `setOutputDescriptors` / `seed` / `outputDescriptors` — against the real `PrinterStateStore` and copy whatever the existing tests in this file call.)

- [ ] **Step 2: Run to confirm failure.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.outputs.OutputsHolderTest' --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: FAIL — `ledChannels` unresolved / `markPending` has no `targetChannels` param.

- [ ] **Step 3: Add `ledChannels` to the VM + populate it.** In `OutputsHolder.kt`:

```kotlin
data class OutputRowVm(
    val descriptor: OutputDescriptor,
    val displayValue: String?,
    val swatchColor: Long?,
    val isSettable: Boolean,
    val busy: Boolean,
    val ledChannels: List<Float>? = null,
)
```

In `buildRow`, declare the val at **`buildRow` scope** (NOT inside the LED `when` branch — the constructor at the end must see it; Codex SHOULD-FIX 4). Put it right before the `return OutputRowVm(...)`:

```kotlin
val ledChannels: List<Float>? = if (descriptor.family in LED_FAMILIES) {
    live?.colorData?.getOrNull(0)?.map { it.toFloat() }
} else null
```

and pass `ledChannels = ledChannels` to the `OutputRowVm(...)` constructor.

- [ ] **Step 4: Add `targetChannels` to pending + full-tuple `reached`.**

```kotlin
data class OutputPending(
    val objectKey: String,
    val target: Double,
    val targetChannels: List<Double>? = null,
    val seq: Long = 0L,
)

fun markPending(objectKey: String, clampedWireTarget: Double, targetChannels: List<Double>? = null) {
    timeoutJobs.remove(objectKey)?.cancel()
    val armed = OutputPending(objectKey, clampedWireTarget, targetChannels, seq = ++pendingSeq)
    _pending.value = _pending.value + (objectKey to armed)
    // … (timeout backstop unchanged) …
}
```

Restructure `reached` so the LED family compares the full tuple when present:

```kotlin
private fun reached(descriptor: OutputDescriptor, state: PrinterState, pending: OutputPending): Boolean {
    if (descriptor.family == FAMILY_SERVO) return false // timeout-only (value is PWM, not angle).
    if (descriptor.family in LED_FAMILIES) {
        val live = state.outputs[descriptor.objectKey]?.colorData?.getOrNull(0) ?: return false
        val tgt = pending.targetChannels
        return if (tgt != null) {
            (0 until maxOf(tgt.size, live.size)).all { i ->
                abs((live.getOrNull(i) ?: 0.0) - (tgt.getOrNull(i) ?: 0.0)) < REACHED_EPSILON
            }
        } else {
            val cur = live.maxOrNull() ?: return false
            abs(cur - pending.target) < REACHED_EPSILON
        }
    }
    val current: Double? = when (descriptor.family) {
        FAMILY_FAN -> state.outputs[descriptor.objectKey]?.speed
        FAMILY_HEATER -> state.heaters[descriptor.objectKey]?.target
        else -> state.outputs[descriptor.objectKey]?.value // output_pin / pwm_tool
    }
    return current != null && abs(current - pending.target) < REACHED_EPSILON
}
```

- [ ] **Step 5: Run tests to green.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.outputs.OutputsHolderTest' --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: PASS (incl. the two new tests + all pre-existing ones — the scalar path for fan/pin/heater is unchanged).

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt \
        app/src/test/java/works/mees/dinghy/outputs/OutputsHolderTest.kt
git -c core.filemode=false commit -m "feat(outputs): raw LED channels on the row VM + full-tuple LED pending"
```

---

### Task 5: Rework the LED Focus surface → H/S/V(+W) sliders + full-state dispatch

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt` (LED branch lines ~291-337; `FocusLedSurface` lines ~478-568; imports)
- Test: `app/src/test/java/works/mees/dinghy/ui/outputs/OutputLedCommandTest.kt`

**Interfaces:**
- Consumes: `HsvSliders(white=…)` (Task 3), `OutputRowVm.ledChannels` + `markPending(targetChannels=)` (Task 4), `ControlSpecs.outputOff` w/ icon (Task 1), `hsvToRgb`/`rgbToHsv` (existing, `Triple<Float,Float,Float>`).
- Produces: a pure helper `ledChannelsFromHsv(h, s, v, white: Float?): SetLedArgs` for the LED `commandName`, unit-testable; `FocusLedSurface` rendered from `HsvSliders` (no `ColorWheel`, no brightness `Scrubber`), foot = `FootButtonBar [Off]`, no in-Focus Back.

- [ ] **Step 1: Write a failing test for the conversion + full-state dispatch.** `OutputLedCommandTest` is pure command/helper testing (no dispatch-capture harness — Codex SHOULD-FIX 7); add `import org.junit.Assert.assertNull`. **Also update/remove the existing "every color dispatch carries `WHITE=0`" RGB assertions** in this file — RGB now OMITS `WHITE=` (w = null) and only RGBW sends it. Add:

```kotlin
@Test
fun rgbwDispatch_sendsAllFourChannels_fromHsvPlusWhite() {
    // Pure hue=0 (red), sat=1, value=1, white=0.5 → r=1,g=0,b=0,w=0.5
    val args = ledChannelsFromHsv("strip", h = 0f, s = 1f, v = 1f, white = 0.5f)
    assertEquals("strip", args.name)
    assertEquals(1f, args.r); assertEquals(0f, args.g); assertEquals(0f, args.b)
    assertEquals(0.5f, args.w)
}

@Test
fun rgbDispatch_omitsWhite_whenNoWhiteChannel() {
    val args = ledChannelsFromHsv("strip", h = 120f, s = 1f, v = 1f, white = null)
    assertEquals(0f, args.r); assertEquals(1f, args.g); assertEquals(0f, args.b)
    assertNull(args.w)
}
```

- [ ] **Step 2: Run to confirm failure.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.outputs.OutputLedCommandTest' --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: FAIL — `ledChannelsFromHsv` unresolved.

- [ ] **Step 3: Add the pure helper** at file scope in `OutputFocusControl.kt` (top-level, near the other helpers):

```kotlin
/**
 * Convert a full HSV color (+ optional independent white channel) into a [SetLedArgs] for [name].
 * White is passed only when the LED has a white channel ([white] non-null) so `SET_LED` omits
 * `WHITE=` on plain RGB strips (PrinterCommands.setLed appends WHITE only for non-null w).
 */
internal fun ledChannelsFromHsv(name: String, h: Float, s: Float, v: Float, white: Float?): SetLedArgs {
    val (r, g, b) = hsvToRgb(h, s, v)
    return SetLedArgs(name, r, g, b, w = white)
}
```

- [ ] **Step 4: Rewrite the LED branch dispatch.** Replace `dispatchColor`/`dispatchWhite`/`dispatchOff` (lines ~295-321) and the `FocusLedSurface(...)` call (lines ~323-336) with a single full-state path:

```kotlin
// Full-state dispatch: SET_LED zeroes any channel not sent (klippy/extras/led.py), so we always
// send the complete r/g/b/w. RGB/RGBW → from H/S/V (+ white); a WHITE-ONLY strip must send RGB=0
// (NOT hsvToRgb(0,0,v), which is grey (v,v,v)) + the white channel. [Codex BLOCKER 1]
fun dispatchLed(h: Float, s: Float, v: Float, whitePct: Float) {
    val white = if (descriptor.ledHasWhite) (whitePct / 100f).coerceIn(0f, 1f) else null
    val args = if (descriptor.ledHasRgb) {
        ledChannelsFromHsv(descriptor.commandName, h, s, v, white)
    } else {
        SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = white)
    }
    val targetChannels = listOf(args.r, args.g, args.b, args.w ?: 0f).map { it.toDouble() }
    holder.markPending(
        descriptor.objectKey,
        clampedWireTarget = targetChannels.maxOrNull() ?: 0.0,
        targetChannels = targetChannels,
    )
    dispatchCommand(CommandRegistry.setLed, args)
}

fun dispatchOff() {
    holder.markPending(
        descriptor.objectKey, 0.0,
        targetChannels = listOf(0.0, 0.0, 0.0, 0.0),
    )
    dispatchCommand(
        CommandRegistry.setLed,
        SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = if (descriptor.ledHasWhite) 0f else null),
    )
}

FocusLedSurface(
    ledHasRgb = descriptor.ledHasRgb,
    ledHasWhite = descriptor.ledHasWhite,
    channels = output.ledChannels,
    busy = busy,
    failureText = failureText,
    onSettle = { h, s, v, whitePct -> if (!busy) dispatchLed(h, s, v, whitePct) },
    onOff = { if (!busy) dispatchOff() },
    uDp = uDp,
    modifier = Modifier.fillMaxSize(),
)
```

- [ ] **Step 5: Rewrite `FocusLedSurface`** (replace the whole composable, lines ~478-568):

```kotlin
/**
 * Inline LED Focus surface — H/S/V sliders (+ White for RGBW) via the theme [HsvSliders]; a
 * white-only LED shows a single brightness [Scrubber]. Foot = FootButtonBar [Off]. No in-Focus Back
 * (the Field list + its Back own navigation). Seeded from the strip's live [channels] (r,g,b,w 0..1).
 */
@Composable
private fun FocusLedSurface(
    ledHasRgb: Boolean,
    ledHasWhite: Boolean,
    channels: List<Float>?,
    busy: Boolean,
    failureText: String?,
    onSettle: (h: Float, s: Float, v: Float, whitePct: Float) -> Unit,
    onOff: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val seed = remember(channels) {
        val r = channels?.getOrNull(0) ?: 0f
        val g = channels?.getOrNull(1) ?: 0f
        val b = channels?.getOrNull(2) ?: 0f
        val w = channels?.getOrNull(3) ?: 0f
        val (h, s, v) = rgbToHsv(r, g, b)
        FloatArray(4).also { it[0] = h; it[1] = s; it[2] = v; it[3] = w }
    }
    var h by remember(seed) { mutableFloatStateOf(seed[0]) }
    var s by remember(seed) { mutableFloatStateOf(seed[1]) }
    var v by remember(seed) { mutableFloatStateOf(seed[2]) }
    var w by remember(seed) { mutableFloatStateOf(seed[3]) }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ledHasRgb) {
                HsvSliders(
                    hue = h, sat = s, value = v,
                    onMove = { nh, ns, nv -> h = nh; s = ns; v = nv },
                    onSettle = { nh, ns, nv -> h = nh; s = ns; v = nv; if (!busy) onSettle(nh, ns, nv, w * 100f) },
                    white = if (ledHasWhite) w else null,
                    onWhiteMove = { w = it },
                    onWhiteSettle = { w = it; if (!busy) onSettle(h, s, v, it * 100f) },
                )
            } else {
                // White-only LED: a single brightness scrubber (name-less → centered value, Task 2).
                Scrubber(
                    name = "", value = w * 100f, range = 0f..100f, step = 1f, unit = "%",
                    uDp = uDp, enabled = !busy,
                    onValueChange = { w = it / 100f },
                    onSettle = { settled -> w = settled / 100f; if (!busy) onSettle(0f, 0f, settled / 100f, settled) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        FootButtonBar(
            uDp = uDp,
            actions = listOf(footAction(ControlSpecs.outputOff, onClick = onOff, enabled = !busy)),
        )
    }
}
```

Note: white-only dispatch maps brightness to the white channel (`onSettle(0,0,v,whitePct)` → `dispatchLed` sends `white = whitePct/100`, and r=g=b=0 because s/v feed `hsvToRgb(0,0,v)`… ensure white-only sends RGB=0). For white-only, force RGB to 0 in `dispatchLed` when `!ledHasRgb`: add at the top of `dispatchLed` — `val (rh, rs) = if (descriptor.ledHasRgb) h to s else 0f to 0f` and call `ledChannelsFromHsv(name, rh, rs, if (descriptor.ledHasRgb) v else 0f, white = (whitePct/100f))`. Simpler: in `dispatchLed`, when `!ledHasRgb`, build `SetLedArgs(name, 0f,0f,0f, w = whitePct/100f)` directly. Implement that guard.

- [ ] **Step 6: Fix imports.** Remove `import works.mees.dinghy.designsystem.ColorWheel`. Add `import works.mees.dinghy.designsystem.HsvSliders`, `import works.mees.dinghy.designsystem.components.FootButtonBar`, `import works.mees.dinghy.designsystem.components.footAction`. (`mutableFloatStateOf`, `rgbToHsv`, `hsvToRgb`, `Scrubber`, `ControlSpecs` are already imported.)

- [ ] **Step 7: Run the LED command tests + compile.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.outputs.OutputLedCommandTest' --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: PASS. (If the file asserted the old `WHITE=0`-always behavior for RGB, update those assertions to expect `WHITE` omitted for RGB / present for RGBW.)

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt \
        app/src/test/java/works/mees/dinghy/ui/outputs/OutputLedCommandTest.kt
git -c core.filemode=false commit -m "feat(outputs): RGB/RGBW H/S/V(+W) sliders, full-state SET_LED dispatch"
```

---

### Task 6: Rewrite the switch surface (`OutputToggleControl`)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/outputs/OutputToggleControl.kt` (full rewrite)
- Modify: `app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt` (digital branch ~388-398: drop `onBack`)

**Interfaces:**
- Produces: `OutputToggleControl(prettyName, isOn, readOnly, enabled, failureText, onOn, onOff, uDp, modifier)` — NO `onBack`. Body = current-state readout; foot = `FootButtonBar [On][Off]` (interactive) or none (read-only).
- Consumes: `ControlSpecs.outputOn`/`outputOff` (Task 1), `footAction`, `FootButtonBar`.

- [ ] **Step 1: Rewrite the file.** Replace `OutputToggleControl.kt` body (keep the package + KDoc, drop the `onBack` param + `ScreenScaffold` + duplicate name):

```kotlin
@Composable
fun OutputToggleControl(
    prettyName: String,
    isOn: Boolean?,
    readOnly: Boolean,
    enabled: Boolean,
    failureText: String?,
    onOn: () -> Unit,
    onOff: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Body: the current On/Off state, color-coded, filling the space (no duplicate name —
        // the FocusFrame header carries identity).
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            FocusHeroText(
                text = if (isOn == true) stringResource(R.string.output_on) else stringResource(R.string.output_off),
                role = DinghyType.focusHero,
                t = t,
                color = if (isOn == true) t.go else t.text3,
            )
        }
        if (readOnly) {
            Text(
                text = stringResource(R.string.output_read_only),
                color = t.text3,
                style = DinghyType.caption.toTextStyle(t),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        if (!readOnly) {
            FootButtonBar(
                uDp = uDp,
                actions = listOf(
                    footAction(ControlSpecs.outputOn, onClick = onOn, enabled = enabled),
                    footAction(ControlSpecs.outputOff, onClick = onOff, enabled = enabled),
                ),
            )
        }
    }
}
```

Update imports: drop `BoxWithConstraints`, `ScreenScaffold`, `ListFrameInset`, `rememberUnitGrid`, `OutlinedControl`, `Intent`, `DinghyIcons`, `FootAction` (the explicit one); add `works.mees.dinghy.control.ControlSpecs`, `works.mees.dinghy.designsystem.components.footAction`, `androidx.compose.ui.unit.Dp`. Keep `FootButtonBar`, `FocusHeroText`, `DinghyType`, `LocalTokens`, `SeverityToast`.

- [ ] **Step 2: Update the digital-branch call** in `OutputFocusControl.kt` (~388): remove `onBack = onBack,` and add `uDp = uDp,`:

```kotlin
OutputToggleControl(
    prettyName = descriptor.prettyName,
    isOn = isOn,
    readOnly = descriptor.readOnly,
    enabled = !busy,
    failureText = failureText,
    onOn = { setDigital(true) },
    onOff = { setDigital(false) },
    uDp = uDp,
    modifier = Modifier.fillMaxSize(),
)
```

- [ ] **Step 3: Compile.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL. (If `OutputToggleControlTest`/preview referenced `onBack`, update it — see Task 9.)

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/outputs/OutputToggleControl.kt \
        app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt
git -c core.filemode=false commit -m "feat(outputs): switch surface = state readout body + [On][Off] foot bar"
```

---

### Task 7: Scrubber surfaces foot → `FootButtonBar [Off]`; remove in-Focus Back app-wide

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt` (`FocusScrubberSurface` ~414-467; all surface call sites; `OutputFocusControl`/`OutputFocusControlInner` signature)
- Modify: `app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt:177-184` (drop `onBack` to `OutputFocusControl`)

**Interfaces:**
- Produces: `FocusScrubberSurface(prettyName, value, range, step, unit, busy, failureText, onSettle, onOff, uDp, modifier)` — NO `onBack`; foot = `FootButtonBar [Off]`. `OutputFocusControl`/`OutputFocusControlInner` lose the `onBack` param.

- [ ] **Step 1: Rewrite `FocusScrubberSurface`** (replace the foot `Row` + drop `onBack`):

```kotlin
@Composable
private fun FocusScrubberSurface(
    prettyName: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    busy: Boolean,
    failureText: String?,
    onSettle: (Float) -> Unit,
    onOff: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Scrubber(
            name = "",
            value = value.coerceIn(range.start, range.endInclusive),
            range = range, step = step, unit = unit, uDp = uDp,
            onSettle = { v -> if (!busy) onSettle(v) },
            modifier = Modifier.fillMaxWidth(),
        )
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        FootButtonBar(
            uDp = uDp,
            actions = listOf(footAction(ControlSpecs.outputOff, onClick = onOff, enabled = !busy)),
        )
    }
}
```

- [ ] **Step 2: Drop `onBack` from the surface signature + every call site.** In `OutputFocusControl` and `OutputFocusControlInner` remove the `onBack: () -> Unit` param. Remove `onBack = onBack,` from the fan / servo / heater / PWM `FocusScrubberSurface(...)` calls (lines ~149-288, ~356-369). The LED + digital branches already dropped it (Tasks 5/6).

- [ ] **Step 3: Drop `onBack` in `OutputsScreen`.** At lines 177-184 remove the `onBack = { onSelect(null) }` argument so the call is:

```kotlin
key(selectedRow.descriptor.objectKey) {
    OutputFocusControl(
        output = selectedRow,
        holder = holder,
        container = container,
        modifier = Modifier.fillMaxSize(),
    )
}
```

(The Field `FootButtonBar` Back at lines 245-256 — which leaves the screen — is unchanged. Selecting a different list row swaps the Focus; there is no longer a deselect-to-empty action, by design.)

- [ ] **Step 4: Compile + run the scrubber settle test.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.outputs.OutputScrubberSettleTest' --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: PASS (settle/dispatch semantics unchanged).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt \
        app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt
git -c core.filemode=false commit -m "feat(outputs): scrubber foot = [Off] FootButtonBar; remove in-Focus Back"
```

---

### Task 8: Delete `ColorWheel`; retire the UAT-5 ">1U" exception

**Files:**
- Delete: `app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt` (stale KDoc refs at lines ~67, ~471, ~515)
- Modify: `docs/ui_design/LAYOUT.md`, `docs/ui_design/COMPONENTS.md`, `.claude/skills/sketch-findings-dinghy-display/SKILL.md`

**Interfaces:** none (removal only).

- [ ] **Step 1: Confirm no live consumers remain.**

Run: `grep -rn "ColorWheel\b" app/src --include=*.kt | grep -v "/designsystem/ColorWheel.kt"`
Expected: only comment/KDoc hits (OutputFocusControl KDoc, HsvToRgb.kt comment, SpoolGlyph.kt comment, DevThemeCyclerOverlay.kt comment). The `import works.mees.dinghy.designsystem.ColorWheel` was removed in Task 5. If any non-comment usage remains, STOP and resolve it.

- [ ] **Step 2: Delete the file.**

```bash
git rm app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt
```

- [ ] **Step 3: Clean the stale KDoc** in `OutputFocusControl.kt` — drop the `ColorWheel` mentions in the file/`FocusLedSurface` KDoc and the deleted UAT-5 exception comment, replacing with a one-line "H/S/V(+W) HsvSliders" description.

- [ ] **Step 4: Retire the design-law exception.** Update ALL of these (Codex SHOULD-FIX 10 added the last two):
  - `docs/ui_design/LAYOUT.md` (UAT-5) and `docs/ui_design/COMPONENTS.md`: change "LED `ColorWheel` is the sole sanctioned >1U exception in Outputs" → Outputs uses H/S/V(+W) sliders (each ≤1U); no >1U control in Outputs; `ColorWheel` retired.
  - `.claude/skills/sketch-findings-dinghy-display/SKILL.md`: same edit to the UAT-5 line (drop the LED `ColorWheel` >1U carve-out).
  - `docs/ui_design/CLAUDE.md`: drop/replace the `basicMarquee`-exceptions LED `ColorWheel` mention.
  - `app/src/main/java/works/mees/dinghy/designsystem/HsvToRgb.kt`: fix the KDoc that describes "fixed saturation / hue-only ColorWheel" (saturation is now user-controlled; the wheel is gone).

- [ ] **Step 5: Full compile to prove the deletion is clean.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.**

```bash
git add -A
git -c core.filemode=false commit -m "refactor(outputs): delete ColorWheel; retire UAT-5 >1U exception in docs"
```

---

### Task 9: Previews, full suite, R8, on-device UAT

**Files:**
- Modify (if needed): `app/src/main/java/works/mees/dinghy/preview/OutputsPreviews.kt`, `app/src/test/java/works/mees/dinghy/ui/outputs/*` (any test/preview referencing removed `onBack` params)

- [ ] **Step 1: Fix any broken preview/test references.**

Run: `grep -rn "OutputToggleControl(\|FocusLedSurface(\|FocusScrubberSurface(" app/src/test app/src/main/java/works/mees/dinghy/preview --include=*.kt`
Update any call that still passes `onBack`/old LED params. `OutputsPreviews` uses the stateless `OutputsScreen` overload (unaffected — it renders a placeholder, not the live surfaces); `OutputRowVm`'s new `ledChannels` defaults, so fixtures compile unchanged.

- [ ] **Step 2: Run the full unit suite.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL, all green (incl. `FontConformanceTest`, `DinghyIconsTest`, control-baseline tests).

- [ ] **Step 3: Build the release (R8) split APKs.**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleRelease --rerun-tasks --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL (R8 keep-rules intact).

- [ ] **Step 4: On-device UAT — both devices.** Sign + install the matching-ABI APK on flox (armeabi-v7a) and moto (arm64-v8a) per [[dinghy-test-devices]]; verify APK mtime is after the last commit before installing ([[dinghy-stale-apk-uat-gate]]). Owner (Matthew) drives:
  - A digital switch: body shows On/Off state; foot `[On][Off]` (power/power_off), no Back; toggling works.
  - A scrubber output (fan/servo/plain light): value is **centered**; foot is a single `[Off]`; no Back.
  - An RGB light: H/S/V sliders set color (saturation now adjustable); foot `[Off]`.
  - An RGBW light (if available): H/S/V + White sliders; white channel independent of RGB (adjusting white does not blank the color, and vice-versa).
  - Confirm leaving Outputs via the Field's Back still works from any selection.

- [ ] **Step 5: Commit any UAT fixes**, then the work is ready to merge.

```bash
git add -A
git -c core.filemode=false commit -m "fix(outputs): UAT polish — <describe>"
```

---

## Self-Review

**Spec coverage:**
- Unifying principle (body + foot bar) → Tasks 5/6/7. ✓
- Remove in-Focus Back from all surfaces → Tasks 5/6/7 + OutputsScreen. ✓
- Center value line (name-blank, non-bare) → Task 2. ✓
- RGB→H/S/V, RGBW→H/S/V+W, white-only→brightness → Tasks 3/5. ✓
- Switch rewrite (drop nested scaffold + dup name + ListFrameInset) → Task 6. ✓
- Foot bar = real FootButtonBar w/ power/power_off, On=Go, Off=Warn → Tasks 1/5/6/7. ✓
- Icon law (register power/power_off, verify font, vector fallback) → Task 1. ✓
- LED full-state SET_LED + saturation + seed-from-color_data → Tasks 4/5. ✓
- Optimistic pending full tuple → Task 4. ✓
- ColorWheel deleted + docs retired → Task 8. ✓
- Tests/previews/R8/on-device → Task 9. ✓

**Placeholder scan:** none — every code step shows the code; visual-only tasks (2, 3, switch/foot layout) state their verification is build + preview + on-device, which is the honest method for Compose layout.

**Type consistency:** `ledChannels: List<Float>?` (VM) vs `targetChannels: List<Double>?` (pending) — intentional: the VM carries floats for the UI (`hsvToRgb`/`rgbToHsv` are Float), pending carries doubles to compare against `colorData` (`Double`). `ledChannelsFromHsv(name,h,s,v,white): SetLedArgs` used consistently in Task 5 test + impl. `OutputToggleControl` and `FocusScrubberSurface`/`FocusLedSurface` all gain `uDp` and lose `onBack` consistently.
