package works.mees.jiib.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.jiib.ui.route.NavDest
import works.mees.jiib.ui.route.knownNavDests
import works.mees.jiib.ui.shell.parseStartDest

/**
 * SC-4b unit half (the pure extra-string → [NavDest] mapping) + the V5 / T-18-04-02 input-validation
 * mitigation: the `start_dest` extra crosses the EXPORTED-MainActivity trust boundary, so [parseStartDest]
 * MUST be total — a recognized name maps to its [NavDest]; null/blank/garbage → `null` and NEVER throws.
 *
 * (Converted from the 18-01 Wave-0 compile scaffold, whose `// TODO(18-04):` marker pointed here.)
 */
class StartDestMappingTest {

    /** Every real [NavDest] simple name round-trips through [parseStartDest] to its own constant. */
    @Test
    fun parseStartDest_everyKnownName_mapsToItsDest() {
        for (dest in knownNavDests) {
            assertEquals(dest, parseStartDest(dest::class.simpleName))
        }
    }

    /** The worked example from the plan/validation map (`--es start_dest FineTune`). */
    @Test
    fun parseStartDest_fineTune_mapsToFineTune() {
        assertEquals(NavDest.FineTune, parseStartDest("FineTune"))
    }

    /** Null / blank / whitespace-only → null (ignore, fall through to the default screen). */
    @Test
    fun parseStartDest_nullOrBlank_returnsNull() {
        assertNull(parseStartDest(null))
        assertNull(parseStartDest(""))
        assertNull(parseStartDest("   "))
        assertNull(parseStartDest("\t\n"))
    }

    /**
     * Garbage / unknown / wrong-case input → null, and NEVER throws (T-18-04-02 safe sealed-interface parse).
     * `Dest.valueOf` would throw on every one of these; [parseStartDest] must not.
     */
    @Test
    fun parseStartDest_garbage_returnsNullNeverThrows() {
        val garbage = listOf(
            "NotARealScreen",
            "finetune",        // wrong case — simpleName is case-sensitive
            "FINETUNE",
            "PrintStatus ; rm -rf",
            "NavDest.FineTune",  // qualified form is not a bare simpleName
            "123",
            " ",
            "FineTune\nMove",  // injection-style multi-token
        )
        for (raw in garbage) {
            assertNull("expected null for $raw", parseStartDest(raw))
        }
    }

    /** Leading/trailing whitespace around a valid name is tolerated (trimmed), not rejected. */
    @Test
    fun parseStartDest_trimsSurroundingWhitespace() {
        assertEquals(NavDest.Move, parseStartDest("  Move  "))
    }
}
