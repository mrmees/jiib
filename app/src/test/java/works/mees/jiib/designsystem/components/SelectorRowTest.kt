package works.mees.jiib.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcons
import org.junit.Test

/**
 * Host tests for the pure helpers extracted from [SelectorRow] (control baseline audit, Phase 3).
 *
 * These encode the selector-tile contract independently of the Compose runtime:
 *  - active tile = [Intent.Accent]; inactive = [Intent.Neutral] (R18 for selector tiles).
 *  - active tile requests the accentSoft FILL; inactive requests the default surface fill (null).
 *  - tile height floors at 1U (`maxOf(uDp, 48.dp)`) — never below the 48dp touch floor.
 *  - the Sort direction overlay resolves to the registered SortAsc (asc) / SortDesc (desc)
 *    tokens (`arrow_drop_up` / `arrow_drop_down`), never the raw arrow_upward/_downward ligature.
 */
class SelectorRowTest {

    // ── intent resolution ────────────────────────────────────────────────────

    @Test
    fun selectorIntent_active_isAccent() {
        assertEquals(Intent.Accent, selectorTileIntent(isActive = true))
    }

    @Test
    fun selectorIntent_inactive_isNeutral() {
        assertEquals(Intent.Neutral, selectorTileIntent(isActive = false))
    }

    // ── fill resolution ──────────────────────────────────────────────────────
    // `wantsAccentFill` models whether the active tile should request the accentSoft fill
    // (true for selector tiles app-wide); the actual Color is pulled from tokens at render time.

    @Test
    fun selectorFill_active_requestsAccentSoftFill() {
        assertEquals(true, selectorWantsAccentFill(isActive = true))
    }

    @Test
    fun selectorFill_inactive_usesDefaultFill() {
        assertEquals(false, selectorWantsAccentFill(isActive = false))
    }

    // ── tile-height math (1U floor) ──────────────────────────────────────────

    @Test
    fun tileHeight_atOrAboveFloor_isUnit() {
        // U = 64dp (phone-landscape floor) → tile is the full 1U.
        assertEquals(64f, selectorTileHeightDp(uDp = 64f), 0f)
    }

    @Test
    fun tileHeight_belowFloor_floorsAt48() {
        // Sub-floor U (e.g. a degenerate 40dp) is clamped up to the 48dp touch floor.
        assertEquals(48f, selectorTileHeightDp(uDp = 40f), 0f)
    }

    // ── direction-arrow token selection ──────────────────────────────────────

    @Test
    fun directionArrow_ascending_isSortAsc() {
        assertSame(JiibIcons.SortAsc, sortDirectionIcon(directionUp = true))
    }

    @Test
    fun directionArrow_descending_isSortDesc() {
        assertSame(JiibIcons.SortDesc, sortDirectionIcon(directionUp = false))
    }

    @Test
    fun directionArrow_none_isNull() {
        assertNull(sortDirectionIcon(directionUp = null))
    }
}
