package works.mees.dinghy.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.config.Profile

/**
 * Per-printer theme TUPLE derivation (D-03/D-08, RESEARCH Pattern 3) — the 15-05 rework.
 *
 * Exercises [Profile.toThemeTuple] (real, Task 1): a profile's persisted theme PRIMITIVES
 * (`seedHex`/`dark`/`paletteMode`/`poolShift`/`poolOverrides`) resolve to the correct
 * [ThemePrefs.ThemeTuple], reusing [ThemePrefs.sanitizeTuple] so corrupt primitives fail safe to the
 * defaults PER ENTRY and NEVER throw (V5/T-15-05-01). Host-pure: no AppContainer/DataStore dependency.
 */
class ProfileThemeSeedTest {

    private fun profile(
        seedHex: String = "#3f78ff",
        dark: Boolean = true,
        mode: String = "Colorful",
        shift: Int = 0,
        overrides: Map<String, Long> = emptyMap(),
    ) = Profile(
        id = "a",
        host = "192.168.1.120",
        seedHex = seedHex,
        dark = dark,
        paletteMode = mode,
        poolShift = shift,
        poolOverrides = overrides,
    )

    @Test
    fun mapsTuplePrimitivesToResolved() {
        // fs is now app-global (FontScalePrefs); sanitizeTuple always defaults to FontScale.M.multiplier
        // and activeThemeTuple overrides it with the app-global choice. The per-profile fsChoice is retired.
        val t = profile(seedHex = "#abcdef", dark = false, mode = "Simple", shift = 90).toThemeTuple()
        assertEquals("#abcdef", t.seedHex)
        assertFalse(t.dark)
        assertEquals("Simple", t.paletteMode)
        assertEquals(90, t.poolShift)
        assertEquals(FontScale.M.multiplier, t.fs)
    }

    @Test
    fun appliesValidPoolOverride() {
        val argb = 0xFF112233L
        val t = profile(overrides = mapOf("1" to argb)).toThemeTuple()
        assertEquals(argb, t.poolOverrides[1])
    }

    @Test
    fun fullyDefaultPrimitives_resolveToTupleDefault() {
        assertEquals(ThemePrefs.TUPLE_DEFAULT, profile().toThemeTuple())
    }

    // ---- Corrupt-primitive table: every junk input sanitizes to a default, NEVER throws (V5) ----

    @Test
    fun junkSeed_failsSafeToDefaultSeed() {
        assertEquals(ThemePrefs.DEFAULT_SEED, profile(seedHex = "not-a-hex").toThemeTuple().seedHex)
        assertEquals(ThemePrefs.DEFAULT_SEED, profile(seedHex = "#12345").toThemeTuple().seedHex) // 5 digits
        assertEquals(ThemePrefs.DEFAULT_SEED, profile(seedHex = "").toThemeTuple().seedHex)
    }

    @Test
    fun badMode_failsSafeToColorful() {
        assertEquals(ThemePrefs.DEFAULT_MODE, profile(mode = "Banana").toThemeTuple().paletteMode)
        assertEquals(ThemePrefs.DEFAULT_MODE, profile(mode = "colorful").toThemeTuple().paletteMode) // case-exact
    }

    @Test
    fun outOfRangeShift_failsSafeToDefault() {
        assertEquals(ThemePrefs.DEFAULT_SHIFT, profile(shift = -1).toThemeTuple().poolShift)
        assertEquals(ThemePrefs.DEFAULT_SHIFT, profile(shift = 999).toThemeTuple().poolShift)
        assertEquals(180, profile(shift = 180).toThemeTuple().poolShift) // in-range honoured
    }

    @Test
    fun tupleFs_isAlwaysMDefault_fsIsAppGlobal() {
        // Per-printer fsChoice is retired; sanitizeTuple always produces FontScale.M.multiplier.
        // The actual app-global value is injected by activeThemeTuple in AppContainer.
        assertEquals(FontScale.M.multiplier, profile().toThemeTuple().fs)
    }

    /**
     * The PER-ENTRY tolerance contract (T-15-05-01, CONFIRMED Codex finding): the poolOverrides map is
     * String-keyed so ONE malformed entry drops in isolation while the GOOD entries survive — a Map<Int,..>
     * could fail the whole decode on a single bad value.
     */
    @Test
    fun poolOverrides_oneBadEntryDropped_goodEntriesSurvive() {
        val good = 0xFF445566L
        val t = profile(
            overrides = mapOf(
                "2" to good,                    // GOOD
                "banana" to 0xFF000000L,        // non-numeric key → drop just this
                "-1" to 0xFF111111L,            // negative index → drop just this
                "3" to 0x1_FFFF_FFFFL,          // out-of-range ARGB → drop just this
            ),
        ).toThemeTuple()
        assertEquals("only the good entry survives", 1, t.poolOverrides.size)
        assertEquals(good, t.poolOverrides[2])
        assertFalse(t.poolOverrides.containsKey(-1))
        assertFalse(t.poolOverrides.containsKey(3))
    }

    @Test
    fun corruptEverything_failsSafeToTupleDefault_neverThrows() {
        val t = profile(
            seedHex = "###",
            mode = "Nope",
            shift = -50,
            overrides = mapOf("x" to 0x1_FFFF_FFFFL),
        ).toThemeTuple()
        // dark default(true) + every other field defaulted + no surviving overrides == the full default tuple.
        assertEquals(ThemePrefs.TUPLE_DEFAULT, t)
        assertTrue(t.poolOverrides.isEmpty())
    }

    /**
     * The SWITCH contract (D-08): two distinct active profiles resolve to two DISTINCT tuples, so a
     * switch genuinely changes the resolved tokens `AppContainer.seedTheme` would apply.
     */
    @Test
    fun switchingActiveProfile_yieldsTheNewProfilesTuple() {
        val a = profile(seedHex = "#aa1122", dark = true, mode = "Colorful").toThemeTuple()
        val b = profile(seedHex = "#22ccdd", dark = false, mode = "Simple").toThemeTuple()
        assertEquals("#aa1122", a.seedHex)
        assertEquals("#22ccdd", b.seedHex)
        assertFalse(a == b)
    }
}
