package works.mees.dinghy.ui.finetune

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for [fmtValue] — the shared Fine-Tune / AdjusterPanel display formatter.
 *
 * CR-03 (26-rev): with `decimals = 0` the formatted string has NO decimal point, yet the old
 * implementation applied `trimEnd('0')` unconditionally — `fmtValue(1499.5, 0)` formatted to
 * "1500" then trimmed to "15"; `fmtValue(2999.9999, 0)` to "3". Reachable for every Double-typed
 * 0-decimal tuner (MAX_VELOCITY, MAX_ACCEL, RETRACT_SPEED, UNRETRACT_SPEED) whenever Klipper
 * reports a fractional value (float noise or an off-grid config). These tests pin the fix:
 * trailing zeros are stripped ONLY when the string actually contains a decimal point.
 */
class FmtValueTest {

    @Test
    fun `null renders DASH`() {
        assertEquals(DASH, fmtValue(null, 0))
        assertEquals(DASH, fmtValue(null, 3))
    }

    @Test
    fun `whole numbers drop the trailing point-zero`() {
        assertEquals("1500", fmtValue(1500.0, 0))
        assertEquals("100", fmtValue(100.0, 1))
        assertEquals("0", fmtValue(0.0, 3))
    }

    // ── CR-03 regression: 0-decimal fractional values ending in zero ──────────────────────────

    @Test
    fun `fractional value rounding to a zero-ended integer is NOT corrupted at 0 decimals`() {
        assertEquals("1500", fmtValue(1499.5, 0))      // was "15"
        assertEquals("3000", fmtValue(2999.9999, 0))   // was "3"
        assertEquals("100", fmtValue(99.999, 0))       // was "1"
        assertEquals("10", fmtValue(10.4, 0))          // was "1"
    }

    @Test
    fun `fractional value at 0 decimals rounds normally when not zero-ended`() {
        assertEquals("23", fmtValue(22.5001, 0))
        assertEquals("1499", fmtValue(1499.4, 0))
    }

    // ── Decimal-bearing tuners keep the trailing-zero trim behavior ────────────────────────────

    @Test
    fun `trailing zeros after the point are still trimmed`() {
        assertEquals("0.05", fmtValue(0.050, 3))
        assertEquals("0.1", fmtValue(0.10, 2))
        assertEquals("2.5", fmtValue(2.50, 1))
    }

    @Test
    fun `value rounding to a whole at positive decimals trims the point too`() {
        // 0.9999 at 2dp formats "1.00" → trims to "1" (point present, so trimming is safe).
        assertEquals("1", fmtValue(0.9999, 2))
    }
}
