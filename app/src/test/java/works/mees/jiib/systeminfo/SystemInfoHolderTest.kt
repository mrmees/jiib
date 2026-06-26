package works.mees.jiib.systeminfo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.di.SpineHandle
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * Host-side tests for [SystemInfoHolder.ensureLoaded] and [SystemInfoHolder.refreshMcuStats].
 * SpineHandle is constructable in tests (the AppContainerTest.handle() factory demonstrates this),
 * so the holder test is feasible without an Android runtime.
 */
class SystemInfoHolderTest {

    private fun el(s: String) = Json.parseToJsonElement(s)

    // Canned JSON responses for the three query endpoints:
    //   printerInfo  → klipperVersion
    //   serverInfo   → moonrakerVersion
    //   objectsQuery → MCU list + configfile
    private val PRINTER_INFO_JSON = """{"software_version":"v0.12.0"}"""
    private val SERVER_INFO_JSON  = """{"moonraker_version":"v0.9.3"}"""
    private val OBJECTS_JSON      = """
        {"status":{"mcu":{"mcu_version":"abc123","last_stats":{"mcu_awake":0.5}}}}
    """.trimIndent()

    /**
     * Build a SpineHandle wired to a canned [CommandDispatcher]. The dispatcher's request lambda
     * routes on method name — objectsQuery goes to its branch, printerInfo/serverInfo to theirs.
     *
     * @param capsObjects the set of object names in [Capabilities.objects] (simulates handshake output)
     * @param queryCounter mutable counter incremented on each dispatcher call (idempotency probe)
     */
    private fun holder(
        capsObjects: Set<String> = setOf("mcu", "heater_bed"),
        queryCounter: MutableList<String> = mutableListOf(),
    ): SystemInfoHolder {
        val store = PrinterStateStore(
            scope = CoroutineScope(SupervisorJob()),
        )
        val spine = SpineHandle(
            printerState = MutableStateFlow(PrinterState()),
            connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected),
            capabilities = MutableStateFlow(Capabilities(objects = capsObjects)),
            dispatcher = CommandDispatcher(
                request = { method, _, _ ->
                    queryCounter.add(method)
                    when {
                        method.contains("printer.info") -> el(PRINTER_INFO_JSON)
                        method.contains("server.info") -> el(SERVER_INFO_JSON)
                        method.contains("objects.query") -> el(OBJECTS_JSON)
                        else -> JsonNull
                    }
                },
                scope = CoroutineScope(SupervisorJob()),
            ),
            store = store,
            minExtrudeTemp = store.minExtrudeTemp,
            maxExtrudeDistance = store.maxExtrudeDistance,
            temperatureBackfill = store.temperatureBackfill,
            heaterLimits = store.heaterLimits,
            httpBase = "http://test:7125",
            metadata = MutableStateFlow(null),
            lastJob = MutableStateFlow(null),
            webcams = MutableStateFlow(emptyList()),
            activeSpool = MutableStateFlow(null),
            fileBrowser = object : works.mees.jiib.ui.files.FileBrowserClient {},
            sessionInstanceId = 1L,
        )

        return SystemInfoHolder(
            scope = CoroutineScope(SupervisorJob()),
            spine = spine,
            procStatUpdates = MutableSharedFlow(),
        )
    }

    @Test
    fun ensureLoaded_populatesVersionsAndMcuDevices() = runTest {
        val h = holder()

        assertNull("mcuDevices starts null", h.mcuDevices.value)
        assertNull("klipperVersion starts null", h.klipperVersion.value)
        assertNull("moonrakerVersion starts null", h.moonrakerVersion.value)

        h.ensureLoaded()

        assertEquals("v0.12.0", h.klipperVersion.value)
        assertEquals("v0.9.3", h.moonrakerVersion.value)
        assertNotNull("mcuDevices populated after ensureLoaded", h.mcuDevices.value)
        val mcu = h.mcuDevices.value!!.single()
        assertEquals("mcu", mcu.key)
        assertEquals("abc123", mcu.firmwareVersion)
    }

    @Test
    fun ensureLoaded_isIdempotent_versionsQueriedOnlyOnce() = runTest {
        val calls = mutableListOf<String>()
        val h = holder(queryCounter = calls)

        h.ensureLoaded()
        val callsAfterFirst = calls.toList()
        h.ensureLoaded()  // second call — versions must NOT be re-queried

        // versions use printerInfo + serverInfo; after second call the same methods should not appear again
        val firstVersionCalls = callsAfterFirst.count { it.contains("printer.info") || it.contains("server.info") }
        val secondVersionCalls = calls.count { it.contains("printer.info") || it.contains("server.info") } - firstVersionCalls
        assertEquals("versions must only be queried once across repeated ensureLoaded calls", 0, secondVersionCalls)
        // mcuDevices is already non-null after first call, so objectsQuery also not re-run
        val mcuCallsFirst = callsAfterFirst.count { it.contains("objects.query") }
        val mcuCallsSecond = calls.count { it.contains("objects.query") } - mcuCallsFirst
        assertEquals("mcuDevices must not be re-queried once populated", 0, mcuCallsSecond)
    }

    @Test
    fun ensureLoaded_withEmptyCapabilities_leavesMcuDevicesNull() = runTest {
        // Simulates an early call before the handshake fills capabilities.objects — the MCU load
        // must be skipped (retry-safe) leaving mcuDevices null so the screen can retry.
        val h = holder(capsObjects = emptySet())

        h.ensureLoaded()

        // Versions should still be fetched (not gated on caps)
        assertEquals("v0.12.0", h.klipperVersion.value)
        // But MCUs remain null (no retry until caps populate — per spec)
        assertNull("mcuDevices must remain null when capabilities.objects is empty", h.mcuDevices.value)
    }

    @Test
    fun refreshMcuStats_noopWhenMcuDevicesNull() = runTest {
        val calls = mutableListOf<String>()
        val h = holder(queryCounter = calls)

        // Don't call ensureLoaded — mcuDevices is null
        h.refreshMcuStats()

        // Nothing should have been queried
        assert(calls.isEmpty()) { "refreshMcuStats must be a no-op when mcuDevices is null; calls=$calls" }
    }

    @Test
    fun refreshMcuStats_updatesStatsAfterEnsureLoaded() = runTest {
        val h = holder()
        h.ensureLoaded()

        val devicesBefore = h.mcuDevices.value!!
        assertEquals(0.5f, devicesBefore.single().mcuAwake)

        h.refreshMcuStats()

        // refreshStats re-queries and merges; with canned data the result is still parseable
        assertNotNull("mcuDevices still populated after refreshMcuStats", h.mcuDevices.value)
    }
}
