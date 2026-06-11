package works.mees.dinghy.command

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.ConnectionError
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.net.RpcConnectionException
import works.mees.dinghy.net.RpcError

/**
 * Virtual-time proof of the UI-affordance layer [CommandDispatcher] adds over
 * `JsonRpcClient.request()`: debounce, in-flight/busy guard, timeout-driven re-enable, and typed
 * failure events for toasting. The transport itself (id-correlation, withTimeout, no-connection
 * fail-fast) is already proven in `JsonRpcClientTest` and is NOT re-tested here — the dispatcher
 * receives a substitutable `request` lambda so each behavior is exercised deterministically under
 * `runTest`'s virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommandDispatcherTest {

    /**
     * A controllable stand-in for `JsonRpcClient::request`. Records each invocation and lets a test
     * resolve / fail / hang any given call by index. Time is the test scheduler's virtual clock so
     * debounce windows are deterministic.
     */
    private class FakeRpc {
        val calls = mutableListOf<String>()
        /** Per-call `timeoutMs` the dispatcher passed to `request`, parallel-indexed with [calls]. */
        val timeouts = mutableListOf<Long>()
        private val deferreds = mutableListOf<CompletableDeferred<JsonElement>>()

        suspend fun request(method: String, params: JsonElement?, timeoutMs: Long): JsonElement {
            calls += method
            timeouts += timeoutMs
            val d = CompletableDeferred<JsonElement>()
            deferreds += d
            return d.await()
        }

        fun complete(index: Int) {
            deferreds[index].complete(kotlinx.serialization.json.JsonNull)
        }

        fun fail(index: Int, cause: Throwable) {
            deferreds[index].completeExceptionally(cause)
        }
    }

    @Test
    fun doubleTapWithinDebounceWindow_firesUnderlyingCallExactlyOnce() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            debounceMs = 400L,
            timeSource = { testScheduler.currentTime },
        )

        dispatcher.dispatch("estop", "printer.emergency_stop")
        runCurrent()
        // Second tap 100ms later — inside the 400ms debounce window.
        advanceTimeBy(100L)
        dispatcher.dispatch("estop", "printer.emergency_stop")
        runCurrent()

        assertEquals("double-tap inside debounce → request invoked exactly once", 1, rpc.calls.size)

        rpc.complete(0)
        runCurrent()
    }

    @Test
    fun inFlightKey_blocksReEntry_andStateFlowReflectsAddThenRemove() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )

        dispatcher.dispatch("estop", "printer.emergency_stop")
        runCurrent()
        assertTrue("key added to inFlight while running", "estop" in dispatcher.inFlight.value)

        // A second dispatch while in-flight is dropped (the first call has not resolved).
        dispatcher.dispatch("estop", "printer.emergency_stop")
        runCurrent()
        assertEquals("in-flight key blocks re-entry", 1, rpc.calls.size)

        rpc.complete(0)
        runCurrent()
        assertFalse("key removed from inFlight after completion", "estop" in dispatcher.inFlight.value)
    }

    @Test
    fun timeoutOnNeverCompletingCall_firesAndReEnablesKey() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeoutMs = 10_000L,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch("estop", "printer.emergency_stop")
        runCurrent()
        assertTrue("estop" in dispatcher.inFlight.value)

        // The call never completes → the dispatcher's own withTimeout fires.
        advanceTimeBy(10_001L)
        runCurrent()

        assertFalse("key re-enabled after timeout", "estop" in dispatcher.inFlight.value)
        assertTrue("a failure event is emitted on timeout", events.any { it is DispatchEvent.Failure })
        collectJob.cancel()
    }

    @Test
    fun rpcConnectionException_emitsFailureEvent_andReEnablesKey() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch("estop", "printer.emergency_stop")
        runCurrent()
        rpc.fail(0, RpcConnectionException(ConnectionError.NetworkUnavailable, "no active connection"))
        runCurrent()

        assertFalse("no in-flight leak on transport failure", "estop" in dispatcher.inFlight.value)
        assertTrue("transport failure surfaces a Failure event", events.any { it is DispatchEvent.Failure })
        collectJob.cancel()
    }

    /**
     * Regression guard for G1 (BLOCKER): a printer-rejected gcode must NOT crash the app.
     *
     * When Moonraker returns a JSON-RPC error envelope for `printer.gcode.script` (an out-of-range
     * move, a failing macro, a heater fault) `JsonRpcClient.request()` completes the pending deferred
     * exceptionally with an [RpcError] — a peer of `Exception`, distinct from `RpcConnectionException`.
     * Before the Task-1 fix, the dispatch() launch block caught only `RpcConnectionException` and
     * `TimeoutCancellationException`, so the `RpcError` re-threw UNCAUGHT in the unsupervised
     * `scope.launch` lambda and killed the process (confirmed FATAL EXCEPTION on flox).
     *
     * This test feeds the EXACT cause the real client produces (mirroring JsonRpcClient line 155 — the
     * mock-vs-reality gap that let G1 ship behind green tests) and asserts: (a) no exception escapes the
     * launch scope (runTest completing without an unhandled exception proves it), (b) a
     * [DispatchEvent.Failure] is surfaced carrying the printer's rejection text, and (c) the key is
     * re-enabled (removed from inFlight) exactly as on a transport failure or timeout.
     */
    @Test
    fun gcodeScriptRpcError_emitsFailureEvent_andDoesNotCrash() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch("move_x", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()
        assertTrue("key in flight while the gcode runs", "move_x" in dispatcher.inFlight.value)

        // Mirror JsonRpcClient.dispatch() completing the deferred with an RpcError on a server error
        // envelope — the printer rejecting an out-of-range jog move.
        rpc.fail(0, RpcError(code = -32000, message = "Move out of range: 418.000 -9.000 32.000 [20.000]"))
        runCurrent()

        assertFalse("no in-flight leak after a printer-rejected gcode", "move_x" in dispatcher.inFlight.value)
        val failure = events.filterIsInstance<DispatchEvent.Failure>().firstOrNull()
        assertTrue("a printer rejection surfaces a Failure event (not a crash)", failure != null)
        assertTrue(
            "the surfaced message carries the printer's rejection text",
            failure!!.message.contains("Move out of range"),
        )
        collectJob.cancel()
    }

    /**
     * G4 (MED): a long-running but VALID gcode must NOT show a false error.
     *
     * `printer.gcode.script` only returns its JSON-RPC reply when the gcode COMPLETES, so a Z-home
     * (move-to-center + probe) routinely runs well past the old flat `DEFAULT_TIMEOUT_MS = 10_000L`.
     * Before this fix the dispatcher applied that 10s deadline to EVERY command, so the outer
     * `withTimeout` fired at 10s and emitted a Failure toast while the printer was still homing
     * successfully. Here we advance past the OLD 10s default with the underlying call still pending
     * (the printer is still homing), THEN complete it — and assert NO Failure was emitted.
     */
    @Test
    fun slowButSuccessfulGcode_emitsNoFailureEvent() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch("home_z", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()
        assertTrue("key in flight while the gcode runs", "home_z" in dispatcher.inFlight.value)

        // Past the OLD 10s default — the printer is STILL homing (call not yet completed).
        advanceTimeBy(11_000L)
        runCurrent()
        assertTrue("a still-homing gcode is not timed out at the old 10s default", "home_z" in dispatcher.inFlight.value)

        // The home eventually succeeds.
        rpc.complete(0)
        runCurrent()

        assertFalse("key removed after the slow gcode finishes", "home_z" in dispatcher.inFlight.value)
        assertFalse(
            "a slow-but-successful gcode must emit NO Failure event",
            events.any { it is DispatchEvent.Failure },
        )
        collectJob.cancel()
    }

    /**
     * G4: `gcode.script` is dispatched with the long [CommandDispatcher.GCODE_TIMEOUT_MS], while
     * instant calls (emergency_stop, queries) keep the short default. Captures the `timeoutMs` each
     * `request` call received via [FakeRpc.timeouts].
     */
    @Test
    fun gcodeScript_usesLongTimeout_notTheDefault() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeoutMs = CommandDispatcher.DEFAULT_TIMEOUT_MS,
            timeSource = { testScheduler.currentTime },
        )

        dispatcher.dispatch("home_z", JsonRpcMethods.GCODE_SCRIPT)
        dispatcher.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP)
        runCurrent()

        assertEquals("both calls were dispatched", 2, rpc.calls.size)
        val gcodeIdx = rpc.calls.indexOf(JsonRpcMethods.GCODE_SCRIPT)
        val estopIdx = rpc.calls.indexOf(JsonRpcMethods.EMERGENCY_STOP)
        assertEquals(
            "gcode.script uses the long gcode timeout",
            CommandDispatcher.GCODE_TIMEOUT_MS,
            rpc.timeouts[gcodeIdx],
        )
        assertEquals(
            "an instant call keeps the short default timeout",
            CommandDispatcher.DEFAULT_TIMEOUT_MS,
            rpc.timeouts[estopIdx],
        )

        rpc.complete(0)
        rpc.complete(1)
        runCurrent()
    }

    /**
     * G4: a request-await timeout on a slow-but-valid gcode surfaces a CALM "still running" message,
     * not the alarming "command could not be sent". `JsonRpcClient.request()` now throws
     * `RpcConnectionException(ConnectionError.Timeout, ...)` for a request-await timeout; the
     * dispatcher must branch on `e.reason` and emit a non-alarming message.
     */
    @Test
    fun requestTimeout_surfacesNonAlarmingStillRunningMessage() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch("home_z", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()
        // The exact shape JsonRpcClient now produces on a request-await timeout.
        rpc.fail(
            0,
            RpcConnectionException(
                ConnectionError.Timeout,
                "request 'printer.gcode.script' (id=1) timed out after 120000ms",
            ),
        )
        runCurrent()

        val failure = events.filterIsInstance<DispatchEvent.Failure>().firstOrNull()
        assertTrue("a timeout still surfaces a Failure event for user feedback", failure != null)
        assertTrue(
            "a request-await timeout reads as a calm 'still running' / 'taking longer' message",
            failure!!.message.contains("still running") || failure.message.contains("taking longer"),
        )
        assertFalse(
            "a slow-gcode timeout must NOT read as a send failure",
            failure.message.contains("could not be sent"),
        )
        collectJob.cancel()
    }

    /**
     * G4: a genuine no-connection / send failure (typed [ConnectionError.NetworkUnavailable]) STILL
     * reports the accurate "command could not be sent" — true transport failures are unchanged.
     */
    @Test
    fun genuineConnectionFailure_stillReportsCouldNotBeSent() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP)
        runCurrent()
        rpc.fail(0, RpcConnectionException(ConnectionError.NetworkUnavailable, "no active connection"))
        runCurrent()

        val failure = events.filterIsInstance<DispatchEvent.Failure>().firstOrNull()
        assertTrue("a true transport failure surfaces a Failure event", failure != null)
        assertTrue(
            "a genuine send failure still reads 'could not be sent'",
            failure!!.message.contains("could not be sent"),
        )
        collectJob.cancel()
    }

    @Test
    fun failureMessageNeverEmbedsApiKeyOrToken() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch("estop", "printer.emergency_stop")
        runCurrent()
        // Even if a cause carried a secret, the dispatcher's surfaced message must not leak it.
        rpc.fail(0, RpcConnectionException(ConnectionError.NetworkUnavailable, "send failed: ?token=SECRETKEY"))
        runCurrent()

        val failure = events.filterIsInstance<DispatchEvent.Failure>().first()
        assertFalse("API key/token must never appear in a toast message", failure.message.contains("SECRETKEY"))
        assertFalse(failure.message.contains("token="))
        collectJob.cancel()
    }

    @Test
    fun distinctKeysDoNotBlockEachOther() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )

        dispatcher.dispatch("estop", "printer.emergency_stop")
        dispatcher.dispatch("fw_restart", "printer.firmware_restart")
        runCurrent()

        assertEquals("distinct keys run concurrently", 2, rpc.calls.size)
        assertTrue("estop" in dispatcher.inFlight.value)
        assertTrue("fw_restart" in dispatcher.inFlight.value)

        rpc.complete(0)
        rpc.complete(1)
        runCurrent()
    }

    // --- Phase-6: planned registry dispatch overload must preserve this class's guarantees. ---

    @Test
    fun registryDispatchOverload_preservesGcodeAndDefaultTimeouts() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeoutMs = CommandDispatcher.DEFAULT_TIMEOUT_MS,
            timeSource = { testScheduler.currentTime },
        )

        dispatcher.dispatch(CommandRegistry.homeAll, Unit)
        dispatcher.dispatch(CommandRegistry.emergencyStop, Unit)
        runCurrent()

        assertEquals("both registry commands were dispatched", 2, rpc.calls.size)
        val gcodeIdx = rpc.calls.indexOf(JsonRpcMethods.GCODE_SCRIPT)
        val estopIdx = rpc.calls.indexOf(JsonRpcMethods.EMERGENCY_STOP)
        assertEquals(
            "registry gcode commands preserve the long gcode timeout",
            CommandDispatcher.GCODE_TIMEOUT_MS,
            rpc.timeouts[gcodeIdx],
        )
        assertEquals(
            "registry non-gcode commands preserve the short default timeout",
            CommandDispatcher.DEFAULT_TIMEOUT_MS,
            rpc.timeouts[estopIdx],
        )

        rpc.complete(0)
        rpc.complete(1)
        runCurrent()
    }

    @Test
    fun registryDispatchOverload_preservesRpcErrorFailureBehavior() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch(CommandRegistry.jog, JogArgs(axis = "X", mm = 10.0, feedMmMin = 3000))
        runCurrent()
        rpc.fail(0, RpcError(code = -32000, message = "Move out of range"))
        runCurrent()

        val failure = events.filterIsInstance<DispatchEvent.Failure>().firstOrNull()
        assertTrue("registry gcode RpcError still emits Failure instead of crashing", failure != null)
        assertTrue("registry failure carries printer rejection text", failure!!.message.contains("Move out of range"))
        assertFalse("registry gcode key is re-enabled after RpcError", CommandRegistry.jog.dispatchKey(JogArgs("X", 10.0, 3000)) in dispatcher.inFlight.value)
        collectJob.cancel()
    }

    @Test
    fun registryDispatchOverload_preservesFailureRedaction() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val collectJob = launch { dispatcher.events.collect { events += it } }

        dispatcher.dispatch(CommandRegistry.emergencyStop, Unit)
        runCurrent()
        rpc.fail(0, RpcConnectionException(ConnectionError.NetworkUnavailable, "send failed: ?token=SECRETKEY"))
        runCurrent()

        val failure = events.filterIsInstance<DispatchEvent.Failure>().first()
        assertFalse("registry failure message must never embed API key material", failure.message.contains("SECRETKEY"))
        assertFalse("registry failure message must never embed token query params", failure.message.contains("token="))
        collectJob.cancel()
    }

    // --- R10 (26.5-03): intentional rejections (busy / debounce) must emit on [rejectedKey] so the
    // UI can render visible feedback instead of a silent drop. The guard SEMANTICS (order, timing,
    // early-return behavior) are protected surface and asserted UNCHANGED by the regression case. ---

    @Test
    fun inFlightReDispatch_emitsRejectedKey_withoutReIssuingCall() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            timeSource = { testScheduler.currentTime },
        )
        val rejected = mutableListOf<String>()
        val collectJob = launch { dispatcher.rejectedKey.collect { rejected += it } }

        dispatcher.dispatch("move_x", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()
        // Second tap while the first is still in-flight: existing busy-guard behavior (no re-issue)
        // PLUS the new feedback emission.
        dispatcher.dispatch("move_x", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()

        assertEquals("in-flight re-tap emits the key on rejectedKey", listOf("move_x"), rejected)
        assertEquals("the busy guard still blocks re-entry (semantics unchanged)", 1, rpc.calls.size)

        rpc.complete(0)
        runCurrent()
        collectJob.cancel()
    }

    @Test
    fun debounceReDispatch_emitsRejectedKey_withoutReIssuingCall() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            debounceMs = 400L,
            timeSource = { testScheduler.currentTime },
        )
        val rejected = mutableListOf<String>()
        val collectJob = launch { dispatcher.rejectedKey.collect { rejected += it } }

        dispatcher.dispatch("set_fan", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()
        rpc.complete(0)
        runCurrent()

        // Re-tap 100ms after the accepted dispatch — inside the 400ms debounce window, with the
        // first call already completed (so the busy guard does NOT apply; this isolates debounce).
        advanceTimeBy(100L)
        dispatcher.dispatch("set_fan", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()

        assertEquals("debounce drop emits the key on rejectedKey", listOf("set_fan"), rejected)
        assertEquals("the debounce guard still drops the re-tap (semantics unchanged)", 1, rpc.calls.size)
        collectJob.cancel()
    }

    @Test
    fun acceptedDispatches_emitNothingOnRejectedKey() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request,
            scope = this,
            debounceMs = 400L,
            timeSource = { testScheduler.currentTime },
        )
        val rejected = mutableListOf<String>()
        val collectJob = launch { dispatcher.rejectedKey.collect { rejected += it } }

        dispatcher.dispatch("set_fan", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()
        rpc.complete(0)
        runCurrent()

        // Past the debounce window — this second dispatch is ACCEPTED.
        advanceTimeBy(401L)
        dispatcher.dispatch("set_fan", JsonRpcMethods.GCODE_SCRIPT)
        runCurrent()
        rpc.complete(1)
        runCurrent()

        assertEquals("both dispatches were accepted", 2, rpc.calls.size)
        assertTrue("accepted dispatches emit NOTHING on rejectedKey", rejected.isEmpty())
        collectJob.cancel()
    }
}
