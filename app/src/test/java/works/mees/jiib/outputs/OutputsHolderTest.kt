package works.mees.jiib.outputs

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.OutputLiveValue
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * Host-side proof for [OutputsHolder] / [OutputRowVm] (19-05, Wave 0 RED → GREEN). Mirrors
 * [works.mees.jiib.ui.finetune.FineTuneHolderTest]: the holder COMBINEs the store's output descriptors
 * with the throttled `printerState` into a typed row VM list. It owns DISPLAY scaling (0..1 → %, the
 * reducer keeps raw — RESEARCH Pitfall 1), the SC-3 degrade cases (absent value, servo PWM-not-angle,
 * read-only static pin), and the per-output clamped busy lock with per-family reached() (servo timeout-only).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutputsHolderTest {

    private fun fan(key: String = "fan_generic FILTER_fan", name: String = "FILTER_fan") = OutputDescriptor(
        objectKey = key, family = "fan_generic", commandName = name, prettyName = name,
        pwm = true, servoAngleMax = 180f, readOnly = false,
    )

    private fun servo(key: String = "servo my_servo", name: String = "my_servo") = OutputDescriptor(
        objectKey = key, family = "servo", commandName = name, prettyName = name,
        pwm = false, servoAngleMax = 180f, readOnly = false,
    )

    private fun staticPin(key: String = "output_pin static_led", name: String = "static_led") = OutputDescriptor(
        objectKey = key, family = "output_pin", commandName = name, prettyName = name,
        pwm = false, servoAngleMax = 180f, readOnly = true,
    )

    private fun pwmPin(key: String = "output_pin laser", name: String = "laser") = OutputDescriptor(
        objectKey = key, family = "output_pin", commandName = name, prettyName = name,
        pwm = true, servoAngleMax = 180f, readOnly = false,
    )

    private fun whiteOnlyLed(
        key: String = "led chamber_light",
        name: String = "chamber_light",
    ) = OutputDescriptor(
        objectKey = key, family = "led", commandName = name, prettyName = name,
        pwm = false, servoAngleMax = 180f, readOnly = false,
        ledHasRgb = false, ledHasWhite = true,
    )

    private fun rgbwLed(
        key: String = "led strip",
        name: String = "strip",
    ) = OutputDescriptor(
        objectKey = key, family = "led", commandName = name, prettyName = name,
        pwm = false, servoAngleMax = 180f, readOnly = false,
        ledHasRgb = true, ledHasWhite = true,
    )

    private fun rowFor(rows: List<OutputRowVm>, key: String): OutputRowVm =
        rows.first { it.descriptor.objectKey == key }

    @Test
    fun absentValueHidesRowValueButRowTappable() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        store.setOutputDescriptors(listOf(fan()))
        // No live `.speed` for this fan → outputs map empty.
        store.seed(PrinterState(outputs = persistentMapOf()))
        runCurrent()

        val row = rowFor(holder.rows.value, "fan_generic FILTER_fan")
        assertNull("absent live value → displayValue null (value hidden, SC-3)", row.displayValue)
        assertTrue("row stays settable (commandable) even with no live value", row.isSettable)
        assertEquals("the row is still emitted (present + tappable)", 1, holder.rows.value.size)
    }

    @Test
    fun servoValueHidden() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        store.setOutputDescriptors(listOf(servo()))
        // Servo reports a PWM `.value` — NOT an angle. The holder must NOT show it as degrees.
        store.seed(PrinterState(outputs = mapOf("servo my_servo" to OutputLiveValue(value = 0.5)).toImmutableMap()))
        runCurrent()

        val row = rowFor(holder.rows.value, "servo my_servo")
        assertNull("servo .value is PWM not angle → displayValue hidden (SC-3)", row.displayValue)

        // And the per-family reached() never confirms a servo → its busy lock is TIMEOUT-ONLY.
        holder.markPending("servo my_servo", clampedWireTarget = 0.5)
        runCurrent()
        assertTrue("servo dispatch arms busy", rowFor(holder.rows.value, "servo my_servo").busy)
        // Even with the live value sitting exactly at the (PWM) target, reached() never fires for a servo.
        store.seed(PrinterState(outputs = mapOf("servo my_servo" to OutputLiveValue(value = 0.5)).toImmutableMap()))
        runCurrent()
        assertTrue("servo stays busy — reached() never confirms a servo", rowFor(holder.rows.value, "servo my_servo").busy)
        // Only the timeout backstop releases it.
        advanceTimeBy(OutputsHolder.PENDING_TIMEOUT_MS + 1)
        runCurrent()
        assertFalse("servo busy clears via timeout backstop only", rowFor(holder.rows.value, "servo my_servo").busy)
    }

    @Test
    fun staticPinReadOnly() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        store.setOutputDescriptors(listOf(staticPin()))
        store.seed(PrinterState())
        runCurrent()

        val row = rowFor(holder.rows.value, "output_pin static_led")
        assertFalse("a static_value pin is read-only (no settable control, SC-3)", row.isSettable)
    }

    @Test
    fun displayScalingSpeedToPct() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        store.setOutputDescriptors(listOf(fan(), pwmPin()))
        store.seed(
            PrinterState(
                outputs = mapOf(
                    "fan_generic FILTER_fan" to OutputLiveValue(speed = 0.45),
                    "output_pin laser" to OutputLiveValue(value = 1.0),
                ).toImmutableMap(),
            ),
        )
        runCurrent()

        // Scaling happens in the HOLDER (display boundary), never the reducer (Pitfall 1).
        assertEquals("raw .speed 0.45 → \"45%\"", "45%", rowFor(holder.rows.value, "fan_generic FILTER_fan").displayValue)
        assertEquals("raw .value 1.0 → \"100%\"", "100%", rowFor(holder.rows.value, "output_pin laser").displayValue)
    }

    @Test
    fun heaterGenericTempFromHeatersSingleSource() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        val heater = OutputDescriptor(
            objectKey = "heater_generic chamber", family = "heater_generic", commandName = "chamber",
            prettyName = "chamber", pwm = false, servoAngleMax = 180f, readOnly = false,
        )
        store.setOutputDescriptors(listOf(heater))
        // Single-source decision (19-04): heater_generic current temp comes from `heaters`, not `outputs`.
        store.seed(PrinterState(heaters = mapOf("heater_generic chamber" to HeaterState(temperature = 42.0, target = 50.0)).toImmutableMap()))
        runCurrent()

        assertEquals("heater_generic temp read from heaters (single source)", "42°C", rowFor(holder.rows.value, "heater_generic chamber").displayValue)
    }

    @Test
    fun perFamilyReachedFanConfirmsFromLive() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        store.setOutputDescriptors(listOf(fan()))
        store.seed(PrinterState(outputs = mapOf("fan_generic FILTER_fan" to OutputLiveValue(speed = 0.0)).toImmutableMap()))
        runCurrent()

        // Arm the SAME clamped wire value the command sends (17-07): 80% → 0.8 wire.
        val wire = PrinterCommands.outputPctToWire(80)
        holder.markPending("fan_generic FILTER_fan", clampedWireTarget = wire)
        runCurrent()
        assertTrue("fan dispatch arms busy", rowFor(holder.rows.value, "fan_generic FILTER_fan").busy)

        // Live speed flips to the dispatched wire value → reached() confirms, busy clears (NOT via timeout).
        store.seed(PrinterState(outputs = mapOf("fan_generic FILTER_fan" to OutputLiveValue(speed = wire)).toImmutableMap()))
        runCurrent()
        assertFalse("fan busy clears the instant live .speed reaches the wire target (confirm-from-live)", rowFor(holder.rows.value, "fan_generic FILTER_fan").busy)
    }

    @Test
    fun whiteOnlyLedSwatchIsGreyNotBlack() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        store.setOutputDescriptors(listOf(whiteOnlyLed()))
        // A white-only LED reports [r,g,b,w] = [0,0,0,0.8].
        store.seed(PrinterState(outputs = mapOf("led chamber_light" to OutputLiveValue(colorData = listOf(listOf(0.0, 0.0, 0.0, 0.8).toImmutableList()).toImmutableList())).toImmutableMap()))
        runCurrent()

        val row = rowFor(holder.rows.value, "led chamber_light")
        assertEquals("white component drives brightness % ", "80%", row.displayValue)
        val argb = row.swatchColor
        assertNotNull("white-only LED has a swatch", argb)
        val r = ((argb!! shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        assertTrue("swatch is grey/white (R==G==B)", r == g && g == b)
        assertTrue("swatch is NOT black (lit white LED reads bright)", r > 0)
    }

    @Test
    fun rowsAlphaSortedByPrettyName() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        val z = fan(key = "fan_generic Zephyr", name = "Zephyr")
        val a = fan(key = "fan_generic Aux", name = "Aux")
        store.setOutputDescriptors(listOf(z, a))
        store.seed(PrinterState())
        runCurrent()

        assertEquals("rows alpha-sorted by prettyName", listOf("Aux", "Zephyr"), holder.rows.value.map { it.descriptor.prettyName })
    }

    @Test
    fun ledRow_exposesRawChannelsIncludingWhite() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        val led = rgbwLed()
        store.setOutputDescriptors(listOf(led))
        store.seed(PrinterState(outputs = mapOf(
            led.objectKey to OutputLiveValue(colorData = listOf(listOf(1.0, 0.0, 0.0, 0.5).toImmutableList()).toImmutableList())
        ).toImmutableMap()))
        runCurrent()

        // The raw [r,g,b,w] channels (incl. white) are surfaced so the slider UI can seed H/S/V + White.
        assertEquals(listOf(1f, 0f, 0f, 0.5f), rowFor(holder.rows.value, led.objectKey).ledChannels)
    }

    @Test
    fun ledPending_holdsWhenOnlySaturationChanges_sameMaxBrightness() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        val led = rgbwLed()
        store.setOutputDescriptors(listOf(led))
        // Live = white-ish [1,1,1,0], max brightness 1.0.
        store.seed(PrinterState(outputs = mapOf(
            led.objectKey to OutputLiveValue(colorData = listOf(listOf(1.0, 1.0, 1.0, 0.0).toImmutableList()).toImmutableList())
        ).toImmutableMap()))
        runCurrent()

        // Command fully-saturated red at the SAME max brightness (1.0). With the old maxOrNull() compare
        // (1.0 == 1.0) this would wrongly clear; the full-tuple compare must keep it pending.
        holder.markPending(led.objectKey, clampedWireTarget = 1.0, targetChannels = listOf(1.0, 0.0, 0.0, 0.0))
        runCurrent()
        assertTrue("LED pending holds when only hue/saturation changes at equal max brightness",
            rowFor(holder.rows.value, led.objectKey).busy)
    }

    @Test
    fun ledPending_clearsWhenLiveReachesFullTuple() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = OutputsHolder(backgroundScope, store)
        val led = rgbwLed()
        store.setOutputDescriptors(listOf(led))
        store.seed(PrinterState(outputs = mapOf(
            led.objectKey to OutputLiveValue(colorData = listOf(listOf(1.0, 1.0, 1.0, 0.0).toImmutableList()).toImmutableList())
        ).toImmutableMap()))
        runCurrent()
        holder.markPending(led.objectKey, clampedWireTarget = 1.0, targetChannels = listOf(1.0, 0.0, 0.0, 0.0))
        runCurrent()
        assertTrue("armed busy", rowFor(holder.rows.value, led.objectKey).busy)

        // Live flips to the EXACT commanded tuple → reached() confirms, busy clears (not via timeout).
        store.seed(PrinterState(outputs = mapOf(
            led.objectKey to OutputLiveValue(colorData = listOf(listOf(1.0, 0.0, 0.0, 0.0).toImmutableList()).toImmutableList())
        ).toImmutableMap()))
        runCurrent()
        assertFalse("LED busy clears the instant live channels reach the full target tuple",
            rowFor(holder.rows.value, led.objectKey).busy)
    }
}
