package works.mees.jiib.ui.console

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import works.mees.jiib.net.MoonrakerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (08-01) — turned GREEN by 08-04 (`parseGcodeStore`).
 *
 * REQ-CONS-02. Walks the REAL probed `server.gcode_store` result shape committed as
 * `/fixtures/gcode_store_e5.json` (probed 2026-06-02 — `{gcode_store:[{message,time,type}]}`, type
 * enum command/response; see docs/moonraker-capabilities.md). Per-entry severity is derived from the
 * message prefix; a malformed entry (missing `message`) is SKIPPED not fatal (T-08-01-T house rule);
 * an empty store → empty list.
 *
 * Production symbol referenced (NOT YET BUILT → RED): top-level `parseGcodeStore(JsonObject)` in
 * `works.mees.jiib.ui.console`.
 */
class GcodeStoreParseTest {

    /** The real probed result object `{gcode_store:[...]}` from the committed fixture. */
    private fun fixtureResult(): JsonObject {
        val res = javaClass.getResource("/fixtures/gcode_store_e5.json")
            ?: error("fixture /fixtures/gcode_store_e5.json missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun realFixture_parsesEntriesWithSeverityFromPrefix() {
        val lines = parseGcodeStore(fixtureResult())
        assertTrue("the real 20-entry store must yield lines", lines.size >= 10)
        // The fixture carries both "// ..." responses (→ WARNING) and plain commands (→ NORMAL).
        assertTrue(lines.any { it.severity == ConsoleSeverity.WARNING })
        assertTrue(lines.any { it.severity == ConsoleSeverity.NORMAL })
        // A known "// External Power OFF" line classifies WARNING.
        val powerOff = lines.first { it.rawMessage == "// External Power OFF" }
        assertEquals(ConsoleSeverity.WARNING, powerOff.severity)
        // A known plain command classifies NORMAL.
        val cmd = lines.first { it.rawMessage == "TURN_OFF_HEATERS" }
        assertEquals(ConsoleSeverity.NORMAL, cmd.severity)
    }

    @Test
    fun malformedEntryMissingMessage_skippedNotFatal() {
        val store = buildJsonObject {
            put("gcode_store", MoonrakerJson.parseToJsonElement(
                """[ {"time":1.0,"type":"command"}, {"message":"M104 S0","time":2.0,"type":"command"} ]"""
            ))
        }
        val lines = parseGcodeStore(store)
        assertEquals(listOf("M104 S0"), lines.map { it.rawMessage })
    }

    @Test
    fun emptyStore_yieldsEmptyList() {
        val empty = buildJsonObject { put("gcode_store", MoonrakerJson.parseToJsonElement("[]")) }
        assertEquals(emptyList<ConsoleLine>(), parseGcodeStore(empty))
        // A result with NO gcode_store key at all is also non-fatal.
        assertEquals(emptyList<ConsoleLine>(), parseGcodeStore(buildJsonObject {}))
    }
}
