package works.mees.dinghy.net

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pure, OVERFLOW-SAFE exponential backoff with jitter (D-01, review MEDIUM).
 *
 * Computes a nominal `base * 2^attempt` interval with full jitter, but CLAMPS the doubling exponent so a
 * huge [attempt] count (e.g. 1000) yields a large-but-finite positive [Duration] — never an overflow,
 * never a negative/zero interval — while keeping NO semantic ceiling that makes the reconnect loop give
 * up. "No ceiling" (retry forever) is achieved via bounded arithmetic, not a give-up budget: once the
 * exponent clamps, the interval stops growing but the loop keeps retrying indefinitely.
 *
 * Jitter is full ("equal jitter" / AWS-style decorrelated-ish): the returned delay is a uniformly random
 * point in `(0, nominal]`, which de-synchronizes a fleet of clients hammering a just-rebooted printer.
 *
 * Pure: same ([attempt], [base], deterministic [rng]) → same output. No I/O, no coroutines, no clock —
 * unit-testable with virtual time and a fixed RNG.
 */
fun backoffDelay(
    attempt: Int,
    base: Duration = DEFAULT_BASE,
    rng: Random = Random.Default,
): Duration {
    val baseMs = base.inWholeMilliseconds.coerceAtLeast(1L)

    // Clamp the shift so 2^shift * baseMs cannot overflow a positive Long. The clamp is the OVERFLOW
    // guard, NOT a give-up ceiling: beyond MAX_SHIFT the nominal interval is just held flat-but-large.
    val safeAttempt = attempt.coerceIn(0, MAX_SHIFT)
    val maxShiftForBase = (java.lang.Long.numberOfLeadingZeros(baseMs) - 1).coerceAtLeast(0)
    val shift = safeAttempt.coerceAtMost(maxShiftForBase)
    val nominalMs = baseMs shl shift // safe: shift chosen so this stays positive

    // Full jitter in (0, nominal]: never zero (so the loop always waits a little), never above nominal.
    val fraction = rng.nextDouble().coerceIn(0.0, 1.0)
    val jitteredMs = (nominalMs * fraction).toLong().coerceAtLeast(1L)
    return jitteredMs.milliseconds
}

/** Default base interval for the reconnect supervisor (D-01). */
val DEFAULT_BASE: Duration = 500.milliseconds

/**
 * Upper bound on the doubling exponent. With a 500 ms base, 2^[MAX_SHIFT] · 500 ms is on the order of
 * minutes-to-hours — large enough to be gentle to the LAN (T-02-09), small enough to never overflow a
 * Long even with a tiny base. This is an ARITHMETIC clamp, not a retry give-up.
 */
const val MAX_SHIFT: Int = 30
