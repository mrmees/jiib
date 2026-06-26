package works.mees.jiib.designsystem.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GREEN tests for the fit-preserving unit-grid formula ([unitGridFor]).
 *
 * Formula:
 *   nTarget = round(contentMinDim / 41.dp).coerceIn(5, 7)
 *   nMaxFit  = (contentMinDim / 64.dp).toInt().coerceAtLeast(5)
 *   N        = min(nTarget, nMaxFit)          -- fit-preserving: N never exceeds what fits at 64dp
 *   uDp      = contentMinDim / N             -- U fills the space evenly
 *
 * The "fit-preserving" form prevents the overflow bug the cross-AI reviewers flagged with the naive
 * `coerceAtLeast(64.dp)`-AFTER-N form (which can produce N*U > contentMinDim if U is clamped up).
 *
 * Key contracts asserted here:
 *  1. N is always in [5, 7]
 *  2. uDp >= 64.dp whenever contentMinDim >= 320.dp
 *  3. N*uDp == contentMinDim (exact fit, no overflow)
 *  4. nMaxFit wins at 320/360dp phone-floor (N=5, not N=7)
 */
class UnitGridTest {

    /**
     * At contentMinDim = 320dp:
     *   nTarget = round(320/41) = round(7.8) = 8 → coerceIn(5,7) = 7
     *   nMaxFit = (320/64).toInt() = 5 → coerceAtLeast(5) = 5
     *   N = min(7, 5) = 5   ← nMaxFit wins; without fit-preserving N=7 would overflow
     *   uDp = 320/5 = 64dp
     */
    @Test
    fun n_clamps_to_5_floor() {
        val grid = unitGridFor(320.dp)
        assertEquals("At 320dp nMaxFit=5 should win over nTarget=7", 5, grid.count)
    }

    /**
     * At contentMinDim = 600dp:
     *   nTarget = round(600/41) = round(14.6) = 15 → coerceIn(5,7) = 7
     *   nMaxFit = (600/64).toInt() = 9 → coerceAtLeast(5) = 9
     *   N = min(7, 9) = 7   ← nTarget ceiling clamps N
     *   uDp = 600/7 ≈ 85.7dp (well above 64dp)
     */
    @Test
    fun n_clamps_to_7_ceiling() {
        val grid = unitGridFor(600.dp)
        assertEquals("At 600dp N should be clamped to ceiling of 7", 7, grid.count)
    }

    /**
     * uDp must never go below 64dp (the minimum touch-target floor) for any normal phone/tablet
     * dimension (contentMinDim >= 320dp).
     *
     * At 320dp: N=5, uDp = 320/5 = 64dp exactly.
     * At 360dp: N=5, uDp = 360/5 = 72dp ≥ 64dp.
     */
    @Test
    fun udp_never_below_64dp() {
        val at320 = unitGridFor(320.dp)
        val at360 = unitGridFor(360.dp)
        assertTrue("uDp at 320dp must be >= 64dp, was ${at320.uDp}", at320.uDp.value >= 64f)
        assertTrue("uDp at 360dp must be >= 64dp, was ${at360.uDp}", at360.uDp.value >= 64f)
    }

    /**
     * uDp = contentMinDim / N — the fit-preserving invariant: N × uDp == contentMinDim exactly.
     * At 360dp: N=5, uDp=360/5=72dp. 5 * 72 = 360 == contentMinDim.
     */
    @Test
    fun udp_equals_dim_over_n() {
        val grid = unitGridFor(360.dp)
        // Dp * Int is supported: uDp.value * count, then compare .value
        val productValue = grid.uDp.value * grid.count
        assertEquals("N * uDp should equal contentMinDim at 360dp", 360f, productValue, 0.01f)
    }

    /**
     * Overflow prevention at 320dp.
     *
     * The naive `coerceAtLeast(64.dp)` form would give N=7 → 7×64=448dp > 320dp.
     * The fit-preserving formula: N=5, uDp=64dp, 5×64=320 ≤ 320. No overflow.
     */
    @Test
    fun no_overflow_at_320dp() {
        val grid = unitGridFor(320.dp)
        val productValue = grid.uDp.value * grid.count
        assertTrue("count * uDp must NOT exceed 320dp (was $productValue)", productValue <= 320f)
        assertTrue("uDp must be >= 64dp at 320dp (was ${grid.uDp})", grid.uDp.value >= 64f)
        assertEquals("count must be 5 at 320dp", 5, grid.count)
        assertEquals("uDp must be exactly 64dp at 320dp", 64.dp, grid.uDp)
    }

    /**
     * Overflow prevention at 360dp.
     *
     * The naive form would give N=7 (nTarget=round(360/41)=9→7) → 7×64=448dp > 360dp.
     * The fit-preserving formula: nMaxFit=(360/64)=5, N=min(7,5)=5, uDp=72dp, 5×72=360. No overflow.
     */
    @Test
    fun no_overflow_at_360dp() {
        val grid = unitGridFor(360.dp)
        val productValue = grid.uDp.value * grid.count
        assertTrue("count * uDp must NOT exceed 360dp (was $productValue)", productValue <= 360f)
        assertTrue("uDp must be >= 64dp at 360dp (was ${grid.uDp})", grid.uDp.value >= 64f)
        assertEquals("count must be 5 at 360dp", 5, grid.count)
    }
}
