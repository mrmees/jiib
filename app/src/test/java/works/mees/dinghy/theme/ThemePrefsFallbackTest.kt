package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-02 / threat T-03-01 — the DETERMINISTIC FAIL-SAFE contract. This phase introduces persistence
 * BEFORE any validating editor (the editor is Phase 4 SET-01), so a corrupt/partial persisted blob
 * must NEVER crash or black-screen the printer display: the read path must always resolve to a
 * COMPLETE, fully-usable theme with NO exception.
 *
 * Host-pure: drives [ThemePrefs.sanitize] directly (a pure function over the stored primitives), so
 * no DataStore I/O / Robolectric is needed. Each case asserts (1) no throw, (2) a complete usable
 * theme after [resolve].
 */
class ThemePrefsFallbackTest {

    private fun sanitize(
        base: String? = ThemeBase.Dark.name,
        fs: String? = FontScale.M.name,
        roleKeys: Set<String>? = emptySet(),
        argb: Map<String, Long> = emptyMap(),
    ) = ThemePrefs.sanitize(base, fs, roleKeys) { argb[it] }

    /** A resolved theme is "fully usable" if resolve() produces a complete token set without throwing. */
    private fun assertFullyUsable(r: ThemePrefs.Resolved) {
        val tokens = resolve(r.base, r.deltas, r.fs)
        // A complete set: spot-check that a representative token from every group is present + opaque
        // where it should be (the data class guarantees all 30 fields are non-null by construction).
        assertEquals(0xFF, tokens.bg.toArgb() ushr 24 and 0xFF)
        assertEquals(0xFF, tokens.text.toArgb() ushr 24 and 0xFF)
        assertTrue(tokens.fs > 0f)
    }

    // ---- Case 1: invalid base → Dark ------------------------------------------------------------

    @Test
    fun invalidBase_fallsBackToDark_neverThrows() {
        val r = sanitize(base = "Chartreuse")
        assertEquals(ThemeBase.Dark, r.base)
        assertFullyUsable(r)
    }

    @Test
    fun nullBase_fallsBackToDark() {
        assertEquals(ThemeBase.Dark, sanitize(base = null).base)
    }

    @Test
    fun caseMismatchBase_isTreatedAsInvalid_fallsBackToDark() {
        // Persisted enum names are case-exact; "dark" (lowercase) is NOT a valid name → Dark default.
        assertEquals(ThemeBase.Dark, sanitize(base = "dark").base)
        // Sanity: an exact valid Light name IS honoured.
        assertEquals(ThemeBase.Light, sanitize(base = "Light").base)
    }

    // ---- Case 2: invalid fs → M (1.15) ----------------------------------------------------------

    @Test
    fun invalidFs_fallsBackToM_neverThrows() {
        val r = sanitize(fs = "XL")
        assertEquals(FontScale.M.multiplier, r.fs)
        assertFullyUsable(r)
    }

    @Test
    fun nullFs_fallsBackToM() {
        assertEquals(FontScale.M.multiplier, sanitize(fs = null).fs)
    }

    // ---- Case 3: malformed/partial delta map → keep valid, drop junk ----------------------------

    @Test
    fun partialDeltaMap_keepsValidEntries_dropsJunkRoles_neverThrows() {
        val good = Color(0xFF112233).toArgb().toLong() and 0xFFFFFFFFL
        val r = sanitize(
            roleKeys = setOf("Accent", "Banana", "Go"), // "Banana" is not a Role → dropped
            argb = mapOf(
                "Accent" to good,
                "Banana" to 0x123L,        // junk role's value is irrelevant
                "Go" to (Color(0xFF445566).toArgb().toLong() and 0xFFFFFFFFL),
            ),
        )
        assertEquals(2, r.deltas.overrides.size)
        assertTrue(r.deltas.overrides.containsKey(TokenDelta.Role.Accent))
        assertTrue(r.deltas.overrides.containsKey(TokenDelta.Role.Go))
        assertFullyUsable(r)
    }

    @Test
    fun roleKeyWithMissingArgbValue_isDropped() {
        // The role is in the key-set but no ARGB long was stored for it → drop it (inherit base).
        val r = sanitize(roleKeys = setOf("Stop"), argb = emptyMap())
        assertTrue(r.deltas.isEmpty)
        // The resolved Stop token therefore inherits the base.
        assertEquals(TokensDark.stop, resolve(r.base, r.deltas, r.fs).stop)
    }

    // ---- Case 4: garbage ARGB for one role → drop just that override ----------------------------

    @Test
    fun garbageArgbForOneRole_dropsOnlyThatOverride_keepsRest_neverThrows() {
        val goodAccent = Color(0xFF112233).toArgb().toLong() and 0xFFFFFFFFL
        val r = sanitize(
            roleKeys = setOf("Accent", "Stop"),
            argb = mapOf(
                "Accent" to goodAccent,
                "Stop" to 0x7_FFFF_FFFFL, // out of [0, 0xFFFFFFFF] → garbage → drop just Stop
            ),
        )
        assertEquals(1, r.deltas.overrides.size)
        assertEquals(goodAccent, r.deltas.overrides[TokenDelta.Role.Accent])
        assertFalse(r.deltas.overrides.containsKey(TokenDelta.Role.Stop))

        val tokens = resolve(r.base, r.deltas, r.fs)
        assertEquals(Color(0xFF112233), tokens.accent)   // valid override applied
        assertEquals(TokensDark.stop, tokens.stop)        // garbage override → inherited base
        assertFullyUsable(r)
    }

    @Test
    fun negativeArgb_isGarbage_dropped() {
        val r = sanitize(roleKeys = setOf("Bg"), argb = mapOf("Bg" to -1L))
        assertTrue(r.deltas.isEmpty)
    }

    // ---- Everything corrupt at once still yields the full default theme -------------------------

    @Test
    fun fullyCorruptBlob_resolvesToCompleteDefaultTheme() {
        val r = sanitize(
            base = "???",
            fs = "???",
            roleKeys = setOf("Nope", "Accent"),
            argb = mapOf("Accent" to 0xDEAD_BEEF_DEADL), // garbage
        )
        assertEquals(ThemeBase.Dark, r.base)
        assertEquals(FontScale.M.multiplier, r.fs)
        assertTrue(r.deltas.isEmpty)
        assertEquals(ThemePrefs.DEFAULT, r)
        assertFullyUsable(r)
    }
}
