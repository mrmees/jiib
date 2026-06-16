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
            "D-05/D-12: registry request wrappers must preserve identify → server.info → list → query → subscribe, " +
                "THEN the one-shot reads (05-03 temperature_store backfill + Phase-20 machine.system_info + " +
                "printer.info hostname + machine.proc_stats + 08-04 gcode_store backfill + configfile one-shot query), in order, once each",
            listOf(
                JsonRpcMethods.IDENTIFY,
                "server.info",
                JsonRpcMethods.OBJECTS_LIST,
                JsonRpcMethods.OBJECTS_QUERY,
                JsonRpcMethods.OBJECTS_SUBSCRIBE,
                JsonRpcMethods.TEMPERATURE_STORE,
                "machine.system_info", // Phase-20 SYS-01 host identity (best-effort, in-handshake)
                "printer.info", // hostname seed (2026-06-15, best-effort, in-handshake)
                "machine.proc_stats", // Phase-20 SYS-02/03 throttle+uptime (best-effort, in-handshake)
                JsonRpcMethods.GCODE_STORE, // 08-04 console backfill (best-effort, in-handshake)
                JsonRpcMethods.OBJECTS_QUERY, // one-shot configfile read
            ),
            handshakeMethods,
        )
        assertEquals(
            "D-12: the configfile one-shot remains a second OBJECTS_QUERY after TEMPERATURE_STORE",
            2,
            handshakeMethods.count { it == JsonRpcMethods.OBJECTS_QUERY },
        )
        assertTrue(
            "D-05: request-helper refactors must not remove any live-proven handshake method",
            handshakeMethods.containsAll(
                listOf(
                    JsonRpcMethods.IDENTIFY,
                    JsonRpcMethods.OBJECTS_LIST,
                    JsonRpcMethods.OBJECTS_QUERY,
                    JsonRpcMethods.OBJECTS_SUBSCRIBE,
                    JsonRpcMethods.TEMPERATURE_STORE,
                ),
            ),
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
        assertEquals(
            "server.info.components must feed live Capabilities.components",
            setOf("history", "file_manager", "spoolman", "webcam"),
            store.capabilities.value.components,
        )

        // State seeded from the query snapshot (golden snapshot has heater_bed ~23.8).
        assertEquals(23.8, store.printerState.value.heaters["heater_bed"]?.temperature)

        // 05-03: the two one-shot reads land on capability-like StateFlows (NOT the throttled hot path).
        // temperature_store backfill maps ONLY the drawn heater sensors (extruder/heater_bed), oldest-first,
        // and ignores the pure `temperature_sensor mcu` entry the store also returns.
        val backfill = store.temperatureBackfill.value
        assertEquals(setOf("extruder", "heater_bed"), backfill.keys.toSet())
        assertEquals(23.0f, backfill["extruder"]?.last())
        // configfile one-shot read populated the min/max extrude hints from the PRIMARY extruder.
        assertEquals(170.0f, store.minExtrudeTemp.value)
        assertEquals(50.0f, store.maxExtrudeDistance.value)
        // printer.info one-shot landed the hostname on the store (the handshake→store integration link
        // that feeds the profile-name seed; the canned harness reply carries hostname "ender5plus").
        assertEquals("ender5plus", store.hostname.value)

        // 08-04: the gcode_store one-shot read REPLACES the console backfill (CONS-02 / D-02). The faithful
        // harness reply carries a "// " response (→ WARNING) and a plain command (→ NORMAL).
        val consoleBackfill = store.consoleBackfill.value
        assertEquals(
            listOf("// External Power OFF", "TURN_OFF_HEATERS"),
            consoleBackfill.map { it.rawMessage },
        )
        assertEquals(
            works.mees.dinghy.ui.console.ConsoleSeverity.WARNING,
            consoleBackfill.first { it.rawMessage == "// External Power OFF" }.severity,
        )

        // 08-04: the SAME single configfile query also populated the macro bodies (MACRO-02), keyed by the
        // lowercased macro name with the `.gcode` body string — NO second configfile query was issued.
        val macroBodies = store.macroBodies.value
        assertEquals(setOf("start_print", "load_filament"), macroBodies.keys)
        assertTrue(macroBodies["start_print"]?.contains("params.EXTRUDER") == true)

        // Pitfall 3: exactly ONE query is scoped to {configfile} — the macro bodies are extracted from
        // that same result, NOT a duplicate configfile query.
        val configfileQueries = fake.sentFrames.toList()
            .map { MoonrakerJson.parseToJsonElement(it).jsonObject }
            .filter { it["method"]?.jsonPrimitive?.content == JsonRpcMethods.OBJECTS_QUERY }
            .count {
                it["params"]?.jsonObject?.get("objects")?.jsonObject?.keys == setOf("configfile")
            }
        assertEquals("Pitfall 3: exactly one {configfile} query feeds both extruder config AND macro bodies", 1, configfileQueries)

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

    @Test
    fun seedsFromSubscribeReply_notJustQuery_closingTheQuerySubscribeGap() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
        val harness = SessionTestHarness()
        // The query snapshot reports heater_bed 23.8; the AUTHORITATIVE subscribe reply reports 99.9.
        // The spine must seed from the subscribe reply (the at-subscription snapshot, CR-02/WR-04),
        // so the final state reflects 99.9 — not the staler query value.
        harness.subscribeSnapshotJson = """
            {"jsonrpc":"2.0","result":{"eventtime":100001.0,"status":{
              "heater_bed":{"temperature":99.9,"target":0.0,"power":0.0}
            }},"id":1}
        """.trimIndent()
        val session = MoonrakerSession(store, rpc, harness.socketEvents())

        val run = launch { session.run() }
        session.connectionState.first { it is ConnectionState.Connected }

        assertEquals(
            "state must be seeded from the objects.subscribe reply (99.9), not the earlier query (23.8)",
            99.9,
            store.printerState.value.heaters["heater_bed"]?.temperature,
        )

        run.cancelAndJoin()
    }
}
