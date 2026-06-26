package works.mees.jiib.spool

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.GoldenFixtures
import works.mees.jiib.net.MoonrakerJson

/**
 * Null-safe spool/filament/vendor model parser (SPOOL-02; D-08).
 *
 * Goldens: `spoolman-live-ender5-proxy-spool3.json` (envelope-wrapped single spool) and
 * `spoolman-live-direct-spool3-before.json` (bare spool object — the direct REST shape).
 *
 * Asserts:
 *  - Omitted fields degrade to null/empty, never throw.
 *  - `color_hex` normalizes consistently with/without a leading `#`, uppercased (D-08).
 *  - A `multi_color_hexes`-bearing filament splits into the per-color list (D-08).
 */
class SpoolmanModelParserTest {

    @Test
    fun parsesEnvelopeWrappedSpoolDetail() {
        val raw = GoldenFixtures.raw("spoolman-live-ender5-proxy-spool3.json")
        assertNotNull("spool3 envelope golden must load", raw)

        // The proxy-v2 envelope nests the single spool under result.response.
        val response = MoonrakerJson.parseToJsonElement(raw)
            .jsonObject["result"]!!
            .jsonObject["response"]!!
        val spool = MoonrakerJson.decodeFromJsonElement(SpoolmanSpool.serializer(), response)

        assertEquals(3, spool.id)
        assertEquals(579.0, spool.remainingWeight!!, 0.0001)
        assertNull("spool 3 omits location", spool.location)
        assertEquals("CMYK Yellow", spool.filament?.name)
        assertEquals("Sunlu", spool.filament?.vendor?.name)
        assertEquals(210, spool.filament?.settingsExtruderTemp)
        // "F6FA00" → "#F6FA00" (already 6 hex, no leading #, uppercased).
        assertEquals("#F6FA00", spool.filament?.normalizedColorHex)
    }

    @Test
    fun parsesBareDirectSpoolObject() {
        val raw = GoldenFixtures.raw("spoolman-live-direct-spool3-before.json")
        assertNotNull("direct-spool3 bare-object golden must load", raw)

        // The direct REST shape is the bare spool object (no envelope).
        val bare = MoonrakerJson.parseToJsonElement(raw)
        val spool = MoonrakerJson.decodeFromJsonElement(SpoolmanSpool.serializer(), bare)

        assertEquals(3, spool.id)
        assertEquals("PLA+ 2.0", spool.filament?.material)
        assertEquals(141154.4037744578, spool.usedLength!!, 0.0001)
        // Omitted optionals tolerated.
        assertNull(spool.lotNumber)
        assertTrue("empty extra object → empty map", spool.extra.isEmpty())
    }

    @Test
    fun normalizesColorHexAndSplitsMultiColor() {
        // D-08 normalization: with/without #, 6/8 hex, reject invalid to null.
        assertEquals("#FF0000", normalizeColorHex("ff0000"))
        assertEquals("#FF0000", normalizeColorHex("#ff0000"))
        assertEquals("#FF0000", normalizeColorHex("  FF0000 "))
        assertEquals("#FF0000FF", normalizeColorHex("ff0000ff"))
        assertNull(normalizeColorHex("xyz"))
        assertNull(normalizeColorHex(""))
        assertNull(normalizeColorHex("12345"))
        assertNull(normalizeColorHex(null))

        // multi_color_hexes splits to the per-color swatch list.
        val filament = SpoolmanFilament(colorHex = "112233", multiColorHexes = "ff0000,00ff00")
        assertEquals(listOf("#FF0000", "#00FF00"), filament.colorSwatches)

        // No multi-color → fall back to the single normalized color.
        val single = SpoolmanFilament(colorHex = "64794b")
        assertEquals(listOf("#64794B"), single.colorSwatches)

        // An invalid `extra` value never breaks construction; extraJson tolerates bad JSON.
        val spool = SpoolmanSpool(id = 9, extra = mapOf("note" to "{not valid json", "dried" to "true"))
        assertNull("bad inner JSON → null, no throw", spool.extraJson("note"))
        assertNotNull("valid inner JSON parses", spool.extraJson("dried"))
        assertNull("absent key → null", spool.extraJson("missing"))
    }
}
