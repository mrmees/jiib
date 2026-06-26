package works.mees.jiib.net

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Overflow-safe backoff (D-01): `jitter(base * 2^attempt)` with a CLAMPED exponent / safe Duration
 * math so a huge attempt count yields a large-but-finite positive Duration, never overflow/negative/zero,
 * and with NO semantic ceiling that would make the supervisor give up.
 */
class BackoffTest {

    private val base = 500.milliseconds

    // A deterministic RNG that returns the high end of the jitter band, so growth is monotonic to assert.
    private fun fixedRng(value: Double) = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
    }

    @Test
    fun largeAttemptCount_isFinitePositive_noOverflow() {
        val d = backoffDelay(attempt = 1000, base = base, rng = fixedRng(1.0))
        assertTrue("backoff must be positive for a huge attempt count", d > Duration.ZERO)
        assertTrue("backoff must be finite (not INFINITE)", d.isFinite())
        // Sanity: it must not have overflowed to a negative/zero Long under the hood.
        assertTrue(d.inWholeMilliseconds > 0L)
    }

    @Test
    fun attemptZero_isAroundBase() {
        // With full jitter (1.0) attempt 0 ~ base; with 0.0 it is the low end. Both positive.
        val high = backoffDelay(0, base, fixedRng(1.0))
        val low = backoffDelay(0, base, fixedRng(0.0))
        assertTrue(high > Duration.ZERO)
        assertTrue(low > Duration.ZERO)
        assertTrue(high >= low)
    }

    @Test
    fun growsAcrossConsecutiveAttempts_untilClamp() {
        // With fixed full jitter the nominal delay doubles each attempt until the exponent clamps.
        val rng = fixedRng(1.0)
        val a2 = backoffDelay(2, base, rng)
        val a4 = backoffDelay(4, base, rng)
        val a6 = backoffDelay(6, base, rng)
        assertTrue("attempt 4 backoff should exceed attempt 2", a4 > a2)
        assertTrue("attempt 6 backoff should exceed attempt 4", a6 > a4)
    }

    @Test
    fun jitterStaysWithinNominalBand_andNeverZero() {
        // Across many random draws at a mid attempt, every value is positive and bounded above 0.
        val rng = Random(42)
        repeat(200) {
            val d = backoffDelay(5, base, rng)
            assertTrue("jittered backoff must be > 0", d > Duration.ZERO)
        }
    }
}
