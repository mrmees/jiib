package works.mees.jiib.ui.temperature

import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.command.SetHeaterArgs
import works.mees.jiib.command.TrailingCommitBatcher
import works.mees.jiib.command.dispatch

/**
 * Regression proof for the quick-rmr post-review HIGH fix: the heater **Off button must NEVER be
 * droppable**. The original wiring (batcher.cancel + bare immediate `dispatch(target=0)`) raced
 * the dispatcher's guards — if the scheduled nonzero commit had JUST fired, the same-key S0 was
 * rejected by the in-flight guard (or, after a fast clear, by the 400ms debounce keyed on that
 * accepted dispatch) with NO retry, leaving the heater hot while the UI implied "off".
 *
 * Off now routes through [TrailingCommitBatcher.tap] (working value 0.0): the canCommit
 * reschedule-while-in-flight loop guarantees eventual delivery after the key clears, and the
 * 500ms quiet window strictly exceeds the 400ms debounce, so the S0 always lands exactly once.
 *
 * These tests mirror the LIVE TemperatureScreen wiring exactly: the same `set_heater_$name`
 * dispatch key, the same setHeater command, the same clamp at commit, a REAL [CommandDispatcher]
 * with virtual timeSource (the CommandDispatcherTest pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureHeaterOffTest {

    private fun heaterDispatchKey(sensorName: String): String = "set_heater_$sensorName"

    /**
     * Scheduled nonzero commit in flight + Off → ZERO S0 attempts while the key is in flight
     * (rescheduled, never rejected) → exactly ONE S0 dispatch after the key clears.
     */
    @Test
    fun offDuringInFlightCommit_dispatchesS0ExactlyOnceAfterKeyClears() =
        runTest(UnconfinedTestDispatcher()) {
            val requests = mutableListOf<Pair<String, JsonElement?>>()
            val gate = CompletableDeferred<Unit>()
            var hangNextRequest = true
            val dispatcher = CommandDispatcher(
                request = { method, params, _ ->
                    requests += method to params
                    if (hangNextRequest) {
                        hangNextRequest = false
                        gate.await() // the FIRST request stays in flight until released.
                    }
                    JsonNull
                },
                scope = backgroundScope,
                timeSource = { testScheduler.currentTime },
            )
            val batcher = TrailingCommitBatcher(
                scope = backgroundScope,
                canCommit = { name -> heaterDispatchKey(name) !in dispatcher.inFlight.value },
                onCommit = { name, value ->
                    dispatcher.dispatch(
                        CommandRegistry.setHeater,
                        SetHeaterArgs(
                            name,
                            PrinterCommands.clampHeaterTarget(value.roundToInt()),
                            heaterDispatchKey(name),
                        ),
                    )
                },
            )

            // Scheduled nonzero commit fires at t=501 and HANGS in flight.
            batcher.tap("extruder", 215.0)
            runCurrent()
            advanceTimeBy(501)
            runCurrent()
            assertEquals("the scheduled nonzero commit dispatched", 1, requests.size)
            assertTrue(
                "the heater key is in flight",
                heaterDispatchKey("extruder") in dispatcher.inFlight.value,
            )

            // OFF lands while the key is in flight (the exact race the old wiring lost).
            batcher.tap("extruder", 0.0)
            runCurrent()
            assertEquals(
                "the display shows 0 instantly (working target wins)",
                0.0,
                batcher.working.value["extruder"],
            )

            // Quiet window elapses while in flight → the commit RESCHEDULES (no rejected attempt:
            // the request lambda is only reached for ACCEPTED dispatches, so size stays 1).
            advanceTimeBy(501)
            runCurrent()
            assertEquals("no S0 attempt while the key is in flight", 1, requests.size)

            // The in-flight request completes → key clears → the next poll commits the S0 once.
            gate.complete(Unit)
            runCurrent()
            assertFalse(
                "the heater key cleared",
                heaterDispatchKey("extruder") in dispatcher.inFlight.value,
            )
            advanceTimeBy(501)
            runCurrent()

            assertEquals("exactly one S0 dispatch after the key clears", 2, requests.size)
            assertTrue(
                "the second wire command carries TARGET=0",
                requests[1].second.toString().contains("TARGET=0"),
            )
            batcher.dispose()
        }

    /**
     * Off tapped INSIDE the 400ms debounce window of the just-accepted nonzero commit (key already
     * cleared by a fast reply). The old bare immediate dispatch would be DEBOUNCE-rejected and
     * dropped; the batcher route waits its 500ms quiet window — strictly past the debounce — so
     * the S0 is accepted.
     */
    @Test
    fun offInsideDebounceWindow_stillDelivers() = runTest(UnconfinedTestDispatcher()) {
        val requests = mutableListOf<Pair<String, JsonElement?>>()
        val dispatcher = CommandDispatcher(
            request = { method, params, _ ->
                requests += method to params
                JsonNull // replies instantly — the key clears as soon as the dispatch runs.
            },
            scope = backgroundScope,
            timeSource = { testScheduler.currentTime },
        )
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            canCommit = { name -> heaterDispatchKey(name) !in dispatcher.inFlight.value },
            onCommit = { name, value ->
                dispatcher.dispatch(
                    CommandRegistry.setHeater,
                    SetHeaterArgs(
                        name,
                        PrinterCommands.clampHeaterTarget(value.roundToInt()),
                        heaterDispatchKey(name),
                    ),
                )
            },
        )

        // Nonzero commit accepted at t=501; key clears immediately (fast reply).
        batcher.tap("heater_bed", 60.0)
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertEquals("the nonzero commit dispatched", 1, requests.size)
        assertFalse(
            "fast reply — the key already cleared",
            heaterDispatchKey("heater_bed") in dispatcher.inFlight.value,
        )

        // Off at t=550 — 49ms after the accepted dispatch, deep inside the 400ms debounce window.
        // A bare immediate dispatch HERE would be debounce-rejected and dropped forever.
        advanceTimeBy(49)
        batcher.tap("heater_bed", 0.0)
        runCurrent()

        // The batcher commits at t=1050 — 549ms after the accepted dispatch, past the debounce.
        advanceTimeBy(501)
        runCurrent()

        assertEquals("the S0 was accepted (quiet window > debounce by design)", 2, requests.size)
        assertTrue(
            "the second wire command carries TARGET=0",
            requests[1].second.toString().contains("TARGET=0"),
        )
        batcher.dispose()
    }
}
