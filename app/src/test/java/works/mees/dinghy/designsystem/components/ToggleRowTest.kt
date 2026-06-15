package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.R

/**
 * Host tests for the pure helpers extracted from [ToggleRow] (control baseline audit, Phase 5).
 *
 * These encode the ToggleRow contract independently of the Compose runtime (mirrors the
 * [StepperRow] / [SelectorRow] `internal fun` + `*Test` pattern):
 *  - the right-hand pill is a TEXT pill driven by the registered `common_on`/`common_off`
 *    string resources — checked → `common_on`, unchecked → `common_off` (NO glyph; icon law).
 *  - "wants accent" tracks the checked state: it drives BOTH the row/pill border (accentLine vs
 *    outline) AND the pill text color (accent vs text2).
 */
class ToggleRowTest {

    // ── pill text resolves to the ON/OFF string resources ────────────────────

    @Test
    fun checked_usesCommonOnResource() {
        assertEquals(R.string.common_on, toggleStateLabelRes(true))
    }

    @Test
    fun unchecked_usesCommonOffResource() {
        assertEquals(R.string.common_off, toggleStateLabelRes(false))
    }

    @Test
    fun onAndOff_areDistinctResources() {
        assertNotEquals(toggleStateLabelRes(true), toggleStateLabelRes(false))
    }

    // ── "wants accent" tracks the checked state ──────────────────────────────

    @Test
    fun wantsAccent_whenChecked() {
        assertTrue(toggleWantsAccent(true))
    }

    @Test
    fun doesNotWantAccent_whenUnchecked() {
        assertFalse(toggleWantsAccent(false))
    }
}
