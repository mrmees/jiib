package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.IconRef

/**
 * Host tests for the pure helpers extracted from [StepperRow] (control baseline audit, Phase 4).
 *
 * These encode the StepperRow contract independently of the Compose runtime:
 *  - the `[−]`/`[+]` tiles render the REGISTERED Decrease/Increase ICON tokens — never a literal
 *    `"−"`/`"+"` text label (icon-registry law; the Move literal-text rogue this phase kills).
 *  - those tokens are ligature-backed (usable in an OutlinedControl symbol slot).
 *  - the dim alpha matches the AdjusterPanel disabled/busy convention (0.38).
 */
class StepperRowTest {

    // ── ± tiles render icon tokens, NOT text ─────────────────────────────────

    @Test
    fun decreaseTile_usesRegisteredDecreaseIcon_notText() {
        assertSame(DinghyIcons.Decrease, stepperDecreaseIcon())
    }

    @Test
    fun increaseTile_usesRegisteredIncreaseIcon_notText() {
        assertSame(DinghyIcons.Increase, stepperIncreaseIcon())
    }

    /**
     * The ± tokens MUST be ligature-backed so they can render in an [OutlinedControl] symbol slot
     * (a Drawable-backed token would throw at `ligatureOf`). This is what lets the icon path replace
     * the literal `"−"`/`"+"` text the Move steppers used.
     */
    @Test
    fun decreaseIcon_isLigatureBacked() {
        assertTrue(stepperDecreaseIcon().primary is IconRef.Ligature)
    }

    @Test
    fun increaseIcon_isLigatureBacked() {
        assertTrue(stepperIncreaseIcon().primary is IconRef.Ligature)
    }

    // ── dim alpha matches the AdjusterPanel disabled/busy convention ─────────

    @Test
    fun dimAlpha_matchesAdjusterPanelConvention() {
        assertEquals(0.38f, STEPPER_DIM_ALPHA, 0f)
    }
}
