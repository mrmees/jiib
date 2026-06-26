package works.mees.jiib.state

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.net.GoldenFixtures
import works.mees.jiib.net.JsonRpcMethods
import works.mees.jiib.net.MoonrakerJson

/**
 * STATE-04: prove Klippy host lifecycle is first-class and updated by `notify_klippy_*` (no-params)
 * and by status diffs (`webhooks.state`), and that print_stats.state lands on the [PrintState] field.
 * The adversarial shutdown fixture must flip to Shutdown WITHOUT blanking last-known temps (D-03 retain).
 */
class KlippyLifecycleTest {

    private fun seededReadyPrinter(): PrinterState {
        val status = GoldenFixtures.resolve("objects_query_snapshot.json")
            .jsonObject["result"]!!.jsonObject["status"]!!.jsonObject
        return reduceSnapshot(status)
    }

    @Test
    fun adversarialShutdownFlipsKlippyButRetainsTemps() {
        // Seed a ready printer with known temps, then heat the bed so there is a non-default value to retain.
        var state = seededReadyPrinter()
        state = reduceDiff(state, MoonrakerJson.parseToJsonElement("""{ "heater_bed": { "temperature": 60.1, "target": 60.0 } }""").jsonObject)
        assertEquals(KlippyState.Ready, state.klippyState)

        // The adversarial fixture carries a single notify_klippy_shutdown frame with NO params.
        val frame = GoldenFixtures.frames("adversarial_klippy_shutdown.json").single()
        val methodName = MoonrakerJson.parseToJsonElement(frame).jsonObject["method"]!!.jsonPrimitive.content
        assertEquals(JsonRpcMethods.NOTIFY_KLIPPY_SHUTDOWN, methodName)

        state = applyKlippyMethod(state, methodName)

        // klippyState flipped to Shutdown...
        assertEquals(KlippyState.Shutdown, state.klippyState)
        // ...but the last-known temps are RETAINED (not blanked) — D-03 retain vs blanking.
        assertEquals(60.1, state.heaters["heater_bed"]!!.temperature, 0.0001)
        assertEquals(60.0, state.heaters["heater_bed"]!!.target, 0.0001)
    }

    @Test
    fun notifyKlippyReadyAndDisconnectedTransition() {
        var state = PrinterState(klippyState = KlippyState.Shutdown)
        state = applyKlippyMethod(state, JsonRpcMethods.NOTIFY_KLIPPY_READY)
        assertEquals(KlippyState.Ready, state.klippyState)

        state = applyKlippyMethod(state, JsonRpcMethods.NOTIFY_KLIPPY_DISCONNECTED)
        assertEquals(KlippyState.Disconnected, state.klippyState)
    }

    @Test
    fun unknownKlippyMethodLeavesStateUntouched() {
        val state = PrinterState(klippyState = KlippyState.Ready)
        assertEquals(state, applyKlippyMethod(state, "notify_some_future_event"))
    }

    @Test
    fun webhooksStateInDiffUpdatesKlippyState() {
        var state = PrinterState(klippyState = KlippyState.Disconnected)
        state = reduceDiff(state, MoonrakerJson.parseToJsonElement("""{ "webhooks": { "state": "shutdown" } }""").jsonObject)
        assertEquals(KlippyState.Shutdown, state.klippyState)
    }

    @Test
    fun printStatsStateUpdatesPrintStateField() {
        var state = PrinterState(printState = PrintState.Standby)
        state = reduceDiff(state, MoonrakerJson.parseToJsonElement("""{ "print_stats": { "state": "printing" } }""").jsonObject)
        assertEquals(PrintState.Printing, state.printState)
    }
}
