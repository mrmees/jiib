package works.mees.dinghy.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-03 / threat T-15-05-01 — the DETERMINISTIC FAIL-SAFE contract for the theme TUPLE (15-05 rework).
 * Persistence exists BEFORE any validating editor, so a corrupt/partial blob must NEVER crash or
 * black-screen the printer display: [ThemePrefs.sanitizeTuple] ALWAYS resolves to a complete, usable
 * [ThemePrefs.ThemeTuple] with NO exception, validating PER-ENTRY.
 *
 * Host-pure: drives [ThemePrefs.sanitizeTuple] directly (a pure function over the stored primitives),
 * no DataStore I/O.
 */
class ThemePrefsFallbackTest {

    private fun sanitize(
        seed: String? = "#3f78ff",
        dark: Boolean? = true,
        mode: String? = "Colorful",
        shift: Int? = 0,
        maxItems: Int? = 4,
        fs: String? = FontScale.M.name,
        overrides: Map<String, Long> = emptyMap(),
    ) = ThemePrefs.sanitizeTuple(seed, dark, mode, shift, maxItems, fs, overrides)

    /** A tuple is "fully usable" if every field is a sane, in-range value (the resolver can generate). */
    private fun assertFullyUsable(t: ThemePrefs.ThemeTuple) {
        assertTrue("seed is a valid hex", Regex("^#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$").matches(t.seedHex))
        assertTrue(t.paletteMode in setOf("Colorful", "Simple", "HighContrast"))
        assertTrue(t.poolShift in 0..360)
        assertTrue(t.maxItems in 1..64)
        assertTrue(t.fs > 0f)
    }

    // ---- seed --------------------------------------------------------------------------------------

    @Test
    fun invalidSeed_fallsBackToDefault_neverThrows() {
        val t = sanitize(seed = "chartreuse")
        assertEquals(ThemePrefs.DEFAULT_SEED, t.seedHex)
        assertFullyUsable(t)
    }

    @Test
    fun nullSeed_fallsBackToDefault() {
        assertEquals(ThemePrefs.DEFAULT_SEED, sanitize(seed = null).seedHex)
    }

    @Test
    fun validSeed_withAndWithoutHash_andAlpha_honoured() {
        assertEquals("#abcdef", sanitize(seed = "#abcdef").seedHex)
        assertEquals("abcdef", sanitize(seed = "abcdef").seedHex)       // no leading #
        assertEquals("#abcdefAB", sanitize(seed = "#abcdefAB").seedHex) // 8-digit (alpha)
    }

    // ---- mode --------------------------------------------------------------------------------------

    @Test
    fun invalidMode_fallsBackToColorful_neverThrows() {
        val t = sanitize(mode = "Plaid")
        assertEquals(ThemePrefs.DEFAULT_MODE, t.paletteMode)
        assertFullyUsable(t)
    }

    @Test
    fun caseMismatchMode_isInvalid_fallsBackToColorful() {
        assertEquals(ThemePrefs.DEFAULT_MODE, sanitize(mode = "simple").paletteMode)
        assertEquals("HighContrast", sanitize(mode = "HighContrast").paletteMode) // exact name honoured
    }

    // ---- shift / maxItems --------------------------------------------------------------------------

    @Test
    fun outOfRangeShift_fallsBackToDefault() {
        assertEquals(ThemePrefs.DEFAULT_SHIFT, sanitize(shift = -10).poolShift)
        assertEquals(ThemePrefs.DEFAULT_SHIFT, sanitize(shift = 400).poolShift)
        assertEquals(120, sanitize(shift = 120).poolShift)
    }

    @Test
    fun outOfRangeMaxItems_fallsBackToDefault() {
        assertEquals(ThemePrefs.DEFAULT_MAX_ITEMS, sanitize(maxItems = 0).maxItems)
        assertEquals(ThemePrefs.DEFAULT_MAX_ITEMS, sanitize(maxItems = 65).maxItems)
        assertEquals(12, sanitize(maxItems = 12).maxItems)
    }

    // ---- fs ----------------------------------------------------------------------------------------

    @Test
    fun invalidFs_fallsBackToM_neverThrows() {
        val t = sanitize(fs = "XL")
        assertEquals(FontScale.M.multiplier, t.fs)
        assertFullyUsable(t)
    }

    // ---- poolOverrides PER-ENTRY tolerance (the load-bearing fail-safe) -----------------------------

    @Test
    fun goodOverrides_areKept() {
        val t = sanitize(overrides = mapOf("0" to 0xFF112233L, "2" to 0xFF445566L))
        assertEquals(2, t.poolOverrides.size)
        assertEquals(0xFF112233L, t.poolOverrides[0])
        assertEquals(0xFF445566L, t.poolOverrides[2])
    }

    @Test
    fun oneBadOverrideEntry_isDropped_goodEntriesSurvive_neverThrows() {
        val good = 0xFF112233L
        val t = sanitize(
            overrides = mapOf(
                "1" to good,                 // GOOD
                "nope" to 0xFF000000L,       // non-numeric key → drop just this
                "5" to 0x7_FFFF_FFFFL,       // out-of-range ARGB → drop just this
                "-3" to 0xFF999999L,         // negative index → drop just this
            ),
        )
        assertEquals("only the good entry survives", 1, t.poolOverrides.size)
        assertEquals(good, t.poolOverrides[1])
        assertFalse(t.poolOverrides.containsKey(5))
        assertFalse(t.poolOverrides.containsKey(-3))
        assertFullyUsable(t)
    }

    @Test
    fun negativeArgb_isGarbage_dropped() {
        val t = sanitize(overrides = mapOf("0" to -1L))
        assertTrue(t.poolOverrides.isEmpty())
    }

    // ---- status-key overrides (D-03) ride the SAME wire map; junk fails safe -----------------------

    @Test
    fun goodStatusOverride_isKept_inStatusMap_notPoolMap() {
        val t = sanitize(overrides = mapOf("stop" to 0xFFAB12CDL, "1" to 0xFF112233L))
        assertEquals("status map carries the status key", 0xFFAB12CDL, t.statusOverrides["stop"])
        assertEquals("pool map carries the int key", 0xFF112233L, t.poolOverrides[1])
        assertFalse("status key is NOT in the pool map", t.poolOverrides.containsKey(0))
    }

    @Test
    fun junkStatusOverrideValue_isDropped_neverThrows() {
        // An out-of-range ARGB on a valid status key drops just that status entry (fail-safe → generated).
        val t = sanitize(
            overrides = mapOf(
                "stop" to 0x7_FFFF_FFFFL,   // out-of-range ARGB → drop just this status entry
                "go" to 0xFF445566L,        // GOOD status entry survives
            ),
        )
        assertFalse("junk stop dropped", t.statusOverrides.containsKey("stop"))
        assertEquals("good go kept", 0xFF445566L, t.statusOverrides["go"])
        assertFullyUsable(t)
    }

    // ---- everything corrupt at once still yields the full default tuple -----------------------------

    @Test
    fun fullyCorruptBlob_resolvesToCompleteDefaultTuple() {
        val t = sanitize(
            seed = "???",
            mode = "???",
            shift = -999,
            maxItems = 9999,
            fs = "???",
            overrides = mapOf("Nope" to 0xDEAD_BEEF_DEADL),
        )
        assertEquals(ThemePrefs.TUPLE_DEFAULT, t)
        assertFullyUsable(t)
    }
}
