package works.mees.dinghy.ui.console

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave-0 RED scaffold (08-01) — turned GREEN by 08-02 Task 1 (`ConsoleSeverity`).
 *
 * REQ-CONS-02. Pure prefix → severity classifier, mirrors the Mainsail convention verbatim
 * (RESEARCH Code Examples / 08-PATTERNS). Must NEVER throw — an absent/odd prefix → NORMAL.
 *
 * Production symbols referenced (NOT YET BUILT → RED): [ConsoleSeverity], [ConsoleSeverity.classify].
 */
class ConsoleSeverityTest {

    @Test
    fun errorPrefix_mapsToError() {
        assertEquals(ConsoleSeverity.ERROR, ConsoleSeverity.classify("!! Move out of range"))
    }

    @Test
    fun actionPrefix_mapsToAction() {
        assertEquals(ConsoleSeverity.ACTION, ConsoleSeverity.classify("// action:prompt_begin"))
    }

    @Test
    fun debugPrefix_mapsToDebug() {
        assertEquals(ConsoleSeverity.DEBUG, ConsoleSeverity.classify("// debug: pin state"))
    }

    @Test
    fun otherSlashSlashPrefix_mapsToWarning() {
        assertEquals(ConsoleSeverity.WARNING, ConsoleSeverity.classify("// Klipper state: Ready"))
        assertEquals(ConsoleSeverity.WARNING, ConsoleSeverity.classify("// External Power OFF"))
    }

    @Test
    fun plainLine_mapsToNormal() {
        assertEquals(ConsoleSeverity.NORMAL, ConsoleSeverity.classify("Done printing file"))
        assertEquals(ConsoleSeverity.NORMAL, ConsoleSeverity.classify("M104 S160"))
    }

    @Test
    fun emptyOrNoPrefix_neverThrows_mapsToNormal() {
        assertEquals(ConsoleSeverity.NORMAL, ConsoleSeverity.classify(""))
        assertEquals(ConsoleSeverity.NORMAL, ConsoleSeverity.classify("ok"))
        // A bare "!!" / "//" without the trailing space is not the Klipper convention → NORMAL.
        assertEquals(ConsoleSeverity.NORMAL, ConsoleSeverity.classify("//nospace"))
    }
}
