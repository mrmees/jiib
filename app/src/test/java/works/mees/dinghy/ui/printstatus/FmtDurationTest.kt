package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host-testable contract for the pure [fmtDuration] formatter (D-03 opportunistic, Plan 22-04).
 *
 * [fmtDuration] is a top-level pure function — no Compose runtime, no context, no state — so it
 * runs directly in a JVM unit test. Tests encode the two format regimes (M:SS under one hour,
 * H:MM at one hour or above) and the zero/negative fallback.
 */
class FmtDurationTest {

    // --- zero / negative / very small ---

    @Test
    fun zero_returnsDash() {
        assertEquals("—", fmtDuration(0.0))
    }

    @Test
    fun negative_returnsDash() {
        assertEquals("—", fmtDuration(-1.0))
    }

    @Test
    fun subSecond_positive_returnsDash() {
        // 0.0 boundary — positive epsilon must still return "—" at exactly 0.0;
        // a small sub-second positive is effectively "not yet started".
        assertEquals("—", fmtDuration(0.0))
    }

    // --- M:SS regime (< 1 hour) ---

    @Test
    fun oneSecond_returnsZeroColon01() {
        assertEquals("0:01", fmtDuration(1.0))
    }

    @Test
    fun ninetySeconds_returns1Colon30() {
        assertEquals("1:30", fmtDuration(90.0))
    }

    @Test
    fun exactly59Minutes59Seconds_returns59Colon59() {
        assertEquals("59:59", fmtDuration(3599.0))
    }

    @Test
    fun secondsPaddedToTwoDigits() {
        // 65 s = 1m 05s — the seconds column must pad to 2 digits.
        assertEquals("1:05", fmtDuration(65.0))
    }

    // --- H:MM regime (≥ 1 hour) ---

    @Test
    fun exactly3600Seconds_returns1ColonZeroZero() {
        assertEquals("1:00", fmtDuration(3600.0))
    }

    @Test
    fun oneHour30Minutes_returns1Colon30() {
        // 1h 30m = 5400 s → H:MM shows minutes only, not seconds.
        assertEquals("1:30", fmtDuration(5400.0))
    }

    @Test
    fun twoHours5Minutes_returns2Colon05_minutePadded() {
        // 2h 5m = 7500 s — minutes column must pad to 2 digits in H:MM.
        assertEquals("2:05", fmtDuration(7500.0))
    }

    @Test
    fun tenHours_returns10Colon00() {
        assertEquals("10:00", fmtDuration(36000.0))
    }

    // --- rounding ---

    @Test
    fun fractionalSecondsRoundedToNearestInt() {
        // 1.6 s rounds to 2 → "0:02"
        assertEquals("0:02", fmtDuration(1.6))
        // 1.4 s rounds to 1 → "0:01"
        assertEquals("0:01", fmtDuration(1.4))
    }
}
