package works.mees.dinghy.command

import kotlinx.coroutines.CompletableDeferred
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
import works.mees.dinghy.net.RpcConnectionException

/**
 * Virtual-time proof of the UI-affordance layer [CommandDispatcher] adds over
 * `JsonRpcClient.request()`: debounce, in-flight/busy guard, timeout-driven re-enable, and typed
 * failure events for toasting. The transport itself (id-correlation, withTimeout, no-connection
 * fail-fast) is already proven in `JsonRpcClientTest` and is NOT re-tested here — the dispatcher
 * receives a substitutable `request` lambda so each behavior is exercised deterministically under
 * `runTest`'s virtual clock.
 */
class CommandDispatcherTest {

    /**
     * A controllable stand-in for `JsonRpcClient::request`. Records each invocation and lets a test
     * resolve / fail / hang any given call by index. Time is the test scheduler's virtual clock so
     * debounce windows are deterministic.
     */
    private class FakeRpc {
        val calls = mutableListOf<String>()
        private val deferreds = mutableListOf<CompletableDeferred<JsonElement>>()

        suspend fun request(method: String, params: JsonElement?, timeoutMs: Long): JsonElement {
            calls += method
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
}
