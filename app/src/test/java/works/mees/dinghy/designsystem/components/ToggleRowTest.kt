package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for the pure helper backing [ToggleRow] (control baseline audit, Phase 5; switch
 * restyle 2026-06-17).
 *
 * Post-restyle the trailing affordance is a SWITCH (sliding knob), not a text pill — the
 * `toggleStateLabelRes` pill-text helper is gone. The surviving pure contract is "wants accent":
 * the checked state drives BOTH the row border (accentLine vs outline) AND the switch colors
 * (accent knob/accentSoft track vs text3 knob/outline track).
 */
class ToggleRowTest {

    @Test
    fun wantsAccent_whenChecked() {
        assertTrue(toggleWantsAccent(true))
    }

    @Test
    fun doesNotWantAccent_whenUnchecked() {
        assertFalse(toggleWantsAccent(false))
    }
}
