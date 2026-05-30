package works.mees.dinghy.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore

/**
 * The resync handshake (review HIGH #2, CONN-04/D-04, STATE-02): identify → objects.list →
 * deriveCapabilities → query → subscribe runs in THAT order exactly once each; the subscribe set is
 * derived from objects.list (A3); the query snapshot overwrites a stale seed (D-04); Capabilities is
 * re-derived on a second connect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HandshakeTest {

    private fun methodsOf(frames: List<String>): List<String> =
        frames.mapNotNull {
            runCatching { MoonrakerJson.parseToJsonElement(it).jsonObject["method"]?.jsonPrimitive?.content }
                .getOrNull()
        }

    @Test
    fun handshakeRunsInOrder_onceEach_andSeedsState() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        val session = MoonrakerSession(
            store = store,
            rpc = rpc,
            socketEvents = harness.socketEvents(),
        )

        val run = launch { session.run() }

        // Drive to Connected.
        session.connectionState.first { it is ConnectionState.Connected }

        val fake = harness.current.get()!!
        val handshakeMethods = methodsOf(fake.sentFrames.toList())

        assertEquals(
            "handshake must run identify → list → query → subscribe in order, once each",
            listOf(
                JsonRpcMethods.IDENTIFY,
                JsonRpcMethods.OBJECTS_LIST,
                JsonRpcMethods.OBJECTS_QUERY,
                JsonRpcMethods.OBJECTS_SUBSCRIBE,
            ),
            handshakeMethods,
        )

        // identify MUST carry all four args Moonraker requires (client_name/version/type/url).
        // A missing `url` is rejected live with code 400 and the handshake never completes —
        // guard it here so the contract can't silently regress (the fake now enforces it too).
        val identifyParams = fake.sentFrames.toList()
            .map { MoonrakerJson.parseToJsonElement(it).jsonObject }
            .first { it["method"]?.jsonPrimitive?.content == JsonRpcMethods.IDENTIFY }["params"]!!
            .jsonObject
        for (required in listOf("client_name", "version", "type", "url")) {
            assertTrue(
                "identify params must include a non-blank '$required' (Moonraker requires it)",
                identifyParams[required]?.jsonPrimitive?.content?.isNotBlank() == true,
            )
        }

        // Capabilities re-derived from objects.list (the golden printer has a bed + macros).
        assertTrue(store.capabilities.value.hasBed)
        assertTrue(store.capabilities.value.macros.isNotEmpty())

        // State seeded from the query snapshot (golden snapshot has heater_bed ~23.8).
        assertEquals(23.8, store.printerState.value.heaters["heater_bed"]?.temperature)

        run.cancelAndJoin()
    }

    @Test
    fun subscribeSetDerivedFromObjectsList_neverSubscribesMissingObject() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        // A minimal printer: no heater_bed, single extruder.
        harness.objectsListJson = """
            {"jsonrpc":"2.0","result":{"objects":["webhooks","toolhead","gcode_move","extruder",
            "print_stats","virtual_sdcard","display_status"]},"id":1}
        """.trimIndent()
        val session = MoonrakerSession(store, rpc, harness.socketEvents())

        val run = launch { session.run() }
        session.connectionState.first { it is ConnectionState.Connected }

        val fake = harness.current.get()!!
        val subscribeFrame = fake.sentFrames.first {
            runCatching {
                MoonrakerJson.parseToJsonElement(it).jsonObject["method"]?.jsonPrimitive?.content
            }.getOrNull() == JsonRpcMethods.OBJECTS_SUBSCRIBE
        }
        val objects = MoonrakerJson.parseToJsonElement(subscribeFrame)
            .jsonObject["params"]!!.jsonObject["objects"]!!.jsonObject

        assertTrue("must NOT subscribe to a missing heater_bed (A3)", "heater_bed" !in objects.keys)
        assertTrue("must subscribe to the present extruder", "extruder" in objects.keys)
        assertTrue(!store.capabilities.value.hasBed)

        run.cancelAndJoin()
    }

    @Test
    fun querySnapshotOverwritesStaleSeed_andCapabilitiesReDerivedOnSecondConnect() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        val session = MoonrakerSession(store, rpc, harness.socketEvents())

        // Deliberately stale seed before connect.
        store.markStale(ConnectionState.Disconnected)
        runCurrent()
        assertTrue(store.printerState.value.stale)

        val run = launch { session.run() }
        session.connectionState.first { it is ConnectionState.Connected }

        // Fresh query snapshot cleared stale (D-04).
        assertTrue("query snapshot must clear stale (D-04)", !store.printerState.value.stale)
        val firstOpens = harness.opens

        // Force a reconnect → a SECOND connect must re-run derive* (Capabilities re-derived).
        store.setCapabilities(works.mees.dinghy.state.Capabilities()) // wipe to prove re-derivation
        harness.current.get()!!.simulateFailure(java.io.IOException("yank"))
        session.connectionState.first { it is ConnectionState.Disconnected || it is ConnectionState.Connecting }
        session.requestReconnectNow()
        advanceUntilIdle()
        session.connectionState.first { it is ConnectionState.Connected }

        assertTrue("a second socket must have been opened (reconnect)", harness.opens > firstOpens)
        assertTrue("Capabilities re-derived on reconnect (STATE-02)", store.capabilities.value.hasBed)

        run.cancelAndJoin()
    }
}
