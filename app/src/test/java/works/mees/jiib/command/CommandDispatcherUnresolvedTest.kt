package works.mees.jiib.command

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.jiib.net.JsonRpcMethods

@OptIn(ExperimentalCoroutinesApi::class)
class CommandDispatcherUnresolvedTest {
    private class FakeRpc {
        val calls = mutableListOf<kotlinx.coroutines.CompletableDeferred<JsonElement>>()
        suspend fun request(method: String, params: JsonElement?, timeoutMs: Long): JsonElement {
            val d = kotlinx.coroutines.CompletableDeferred<JsonElement>(); calls += d; return d.await()
        }
        fun complete(i: Int) { calls[i].complete(JsonNull) }
    }

    @Test
    fun hardLock_timeout_setsUnresolved_thenAcknowledgeClears() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val d = CommandDispatcher(request = rpc::request, scope = this, debounceMs = 0L,
            timeSource = { testScheduler.currentTime })
        d.dispatch("bed_mesh_calibrate", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.HardLock, timeoutMs = 1_000L)
        runCurrent(); advanceTimeBy(1_001L); runCurrent()
        assertEquals("timeout latches unresolved", "bed_mesh_calibrate", d.unresolvedHardLock.value)
        d.acknowledgeUnresolved()
        assertNull("acknowledge clears unresolved", d.unresolvedHardLock.value)
    }

    @Test
    fun hardLock_cleanCompletion_doesNotLatch() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val d = CommandDispatcher(request = rpc::request, scope = this, debounceMs = 0L,
            timeSource = { testScheduler.currentTime })
        d.dispatch("home_all", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.HardLock)
        runCurrent(); rpc.complete(0); runCurrent()
        assertNull("clean HardLock completion must not latch unresolved", d.unresolvedHardLock.value)
    }
}
