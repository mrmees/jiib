package works.mees.jiib.spool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.GoldenFixtures
import works.mees.jiib.net.MoonrakerJson

/**
 * Proxy-v2 envelope parser (SPOOL-02/08; D-07).
 *
 * Asserts against the verbatim live goldens:
 *  - `spoolman-live-ender5-proxy-pla.json` → `X-Total-Count == 7`, 5 paginated rows, `error:null`
 *    treated as SUCCESS (not "no data").
 *  - A malformed envelope → empty + unsuccessful, never throws; one bad row drops, the rest survive.
 *  - `parseSpoolmanStatus` hand-walks the bare status object tolerating missing fields.
 */
class SpoolmanProxyParserTest {

    private fun resultOf(goldenName: String) =
        MoonrakerJson.parseToJsonElement(GoldenFixtures.raw(goldenName)).jsonObject["result"]

    @Test
    fun parsesXTotalCountAndErrorNullFromPlaGolden() {
        val envelope = resultOf("spoolman-live-ender5-proxy-pla.json")
        assertNotNull("proxy-pla golden must carry a result envelope", envelope)

        val parsed = parseSpoolmanSpools(envelope)
        assertTrue("error:null means SUCCESS, not no-data", parsed.success)
        assertEquals("X-Total-Count from response_headers", 7, parsed.totalCount)
        assertEquals("5 paginated rows in response[]", 5, parsed.rows.size)
        // Spot-check a couple of decoded rows incl. defensive color.
        assertEquals(setOf(4, 3, 5, 1, 6), parsed.rows.map { it.id }.toSet())
        val red = parsed.rows.first { it.id == 6 }
        assertEquals("#FF0000", red.filament?.normalizedColorHex)
    }

    @Test
    fun decodesSpoolRowsTolerantlyDroppingBadRows() {
        // A handcrafted envelope: one good spool row + one structurally-bad row (a bare string).
        val good = MoonrakerJson.parseToJsonElement(
            """{"id":42,"remaining_weight":100.0,"archived":false,"extra":{}}""",
        )
        val envelope: JsonObject = buildJsonObject {
            put("error", null as String?)
            put(
                "response",
                buildJsonArray {
                    add(good)
                    add(MoonrakerJson.parseToJsonElement("\"not-a-spool-object\""))
                },
            )
            put("response_headers", buildJsonObject { put("X-Total-Count", "2") })
        }

        val parsed = parseSpoolmanSpools(envelope)
        assertTrue(parsed.success)
        assertEquals("one bad row drops, the good one survives", 1, parsed.rows.size)
        assertEquals(42, parsed.rows.single().id)
        assertEquals(2, parsed.totalCount)
    }

    @Test
    fun malformedEnvelopeYieldsEmptyNeverThrows() {
        // A non-object result → empty + unsuccessful, no throw.
        val bare = MoonrakerJson.parseToJsonElement("\"garbage\"")
        val parsed = parseSpoolmanSpools(bare)
        assertTrue(parsed.rows.isEmpty())
        assertFalse(parsed.success)
        assertNull(parsed.totalCount)

        // Null result → empty.
        assertTrue(parseSpoolmanSpools(null).rows.isEmpty())

        // A non-null error → NOT success, even with a populated response.
        val errored: JsonObject = buildJsonObject {
            put("error", "spoolman offline")
            put("response", buildJsonArray { })
        }
        assertFalse(parseSpoolmanSpools(errored).success)
    }

    @Test
    fun parsesMaterialsVendorsLocationsEnvelopes() {
        val materials = parseSpoolmanMaterials(resultOf("spoolman-live-ender5-proxy-materials.json"))
        assertTrue(materials.success)
        assertEquals(11, materials.rows.size)
        assertTrue(materials.rows.contains("PLA+ 2.0"))
        assertNull("materials endpoint does not paginate → no X-Total-Count", materials.totalCount)

        val vendors = parseSpoolmanVendors(resultOf("spoolman-live-ender5-proxy-vendors.json"))
        assertTrue(vendors.success)
        assertEquals(6, vendors.rows.size)
        assertEquals(6, vendors.totalCount)
        assertTrue(vendors.rows.any { it.name == "ABCWavePrint" })

        val locations = parseSpoolmanLocations(resultOf("spoolman-live-ender5-proxy-locations.json"))
        assertEquals(listOf("Ender 5"), locations.rows)

        // color-red-filaments → filament rows with top-level color_hex.
        val filaments = parseSpoolmanFilaments(resultOf("spoolman-live-ender5-proxy-color-red-filaments.json"))
        assertTrue(filaments.rows.isNotEmpty())
        assertEquals("#E63034", filaments.rows.first().normalizedColorHex)
    }

    @Test
    fun parsesStatusFromGoldenTolerantOfMissingFields() {
        val status = parseSpoolmanStatus(resultOf("spoolman-live-ender5-status-before-set.json"))
        assertTrue(status.spoolmanConnected)
        assertEquals(5, status.activeSpoolId)
        assertTrue("pending_reports is empty in the golden", status.pendingReports.isEmpty())
        assertFalse(status.hasPendingReports)

        // Missing fields degrade to defaults; never throws.
        val sparse = parseSpoolmanStatus(buildJsonObject { put("spoolman_connected", true) })
        assertTrue(sparse.spoolmanConnected)
        assertNull(sparse.activeSpoolId)
        assertTrue(sparse.pendingReports.isEmpty())

        // A non-empty pending_reports is observable.
        val pending = parseSpoolmanStatus(
            buildJsonObject {
                put("spoolman_connected", true)
                put("spool_id", 3)
                put(
                    "pending_reports",
                    buildJsonArray {
                        add(buildJsonObject { put("spool_id", 3); put("filament_used", 12.5) })
                    },
                )
            },
        )
        assertTrue(pending.hasPendingReports)
        assertEquals(1, pending.pendingReports.size)
        assertEquals(12.5, pending.pendingReports.single().filamentUsedMm, 0.0001)

        // A non-object result → defaults, never throws.
        val empty = parseSpoolmanStatus(MoonrakerJson.parseToJsonElement("\"x\""))
        assertFalse(empty.spoolmanConnected)
    }
}
