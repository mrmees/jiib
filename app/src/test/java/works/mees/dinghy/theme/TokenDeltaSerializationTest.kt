package works.mees.dinghy.theme

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.config.PersistedProfile
import works.mees.dinghy.config.Profile

/**
 * Profile theme PERSISTENCE shape (15-06: the old per-role TokenDelta round-trip AND the deprecated
 * `themeBase`/`themeDeltaArgb` fields are now fully DELETED). Two cases:
 *  1. FRESH-START decode (D-05, no migration): an OLD-shape blob that still carries the now-DELETED
 *     `themeBase`/`themeDeltaArgb` keys decodes cleanly — `ignoreUnknownKeys` SKIPS those keys and the
 *     NEW tuple fields take their defaults. No crash, no migration, no leak into the runtime tuple.
 *  2. New-shape tuple ROUND-TRIP: a [PersistedProfile] carrying the full tuple (incl. a 2-entry
 *     `poolOverrides`) re-encodes/decodes byte-stable.
 *
 * Host-pure: drives kotlinx JSON directly (the exact serializer [works.mees.dinghy.config.ProfileStore]
 * uses), with `ignoreUnknownKeys = true` mirroring the store's lenient decode.
 */
class TokenDeltaSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PersistedProfile.serializer())

    @Test
    fun oldShapeBlob_decodesFreshStart_legacyKeysSkipped_tupleDefaults() {
        // A pre-15-05 blob: only id/host + the DELETED themeBase/themeDeltaArgb keys (no tuple keys).
        val oldBlob = """
            [{"id":"a","host":"192.168.1.120","themeBase":"Light",
              "themeDeltaArgb":{"Accent":4278203955}}]
        """.trimIndent()

        val decoded = json.decodeFromString(serializer, oldBlob)
        assertEquals(1, decoded.size)
        val p = decoded[0]

        // The NEW tuple fields default (D-05 fresh-start — old theme is NOT migrated); the now-unknown
        // themeBase/themeDeltaArgb keys are silently skipped by ignoreUnknownKeys (no crash).
        // D-17/28-04: the pool-size field was also removed from PersistedProfile; old blobs skip it too.
        assertEquals("#3f78ff", p.seedHex)
        assertTrue(p.dark)
        assertEquals("Colorful", p.paletteMode)
        assertEquals(0, p.poolShift)
        assertTrue(p.poolOverrides.isEmpty())

        val tuple = Profile.fromPersisted(p).toThemeTuple()
        assertEquals(ThemePrefs.TUPLE_DEFAULT, tuple) // legacy "Light"/Accent did NOT leak into the tuple
    }

    @Test
    fun newShapeTuple_roundTripsExactly_inclPoolOverrides() {
        val original = PersistedProfile(
            id = "b",
            host = "192.168.1.121",
            seedHex = "#abcdef",
            dark = false,
            paletteMode = "Simple",
            poolShift = 90,
            poolOverrides = mapOf("1" to 0xFF112233L, "3" to 0xFF445566L),
            fsChoice = "L",
        )
        val blob = json.encodeToString(serializer, listOf(original))
        val back = json.decodeFromString(serializer, blob).single()

        assertEquals("#abcdef", back.seedHex)
        assertEquals(false, back.dark)
        assertEquals("Simple", back.paletteMode)
        assertEquals(90, back.poolShift)
        assertEquals(mapOf("1" to 0xFF112233L, "3" to 0xFF445566L), back.poolOverrides)
        assertEquals("L", back.fsChoice)
        assertEquals(original, back)
    }
}
