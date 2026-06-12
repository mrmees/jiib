package works.mees.dinghy.command

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-key TRAILING-COMMIT debouncer for stepper-style controls (quick-rmr).
 *
 * Rapid +/− taps on an adjuster update a local *working value* instantly ([tap] stores the
 * caller-clamped value into [working]); the wire command dispatches ONCE per key, after
 * [quietMs] of no taps, carrying the FINAL working value ([onCommit]). This makes dispatcher
 * rejections structurally rare instead of trying to protect every micro-tap (the owner-rejected
 * per-tap rejection-flash/busy-lock UX).
 *
 * ## Division of labor
 * The CALLER passes the ALREADY-CLAMPED working value into [tap] — this class knows nothing about
 * limits (same contract as AdjusterPanel's `onIncrement` KDoc: clamp authority stays at the call
 * site, 17-07 invariant). This class owns only the timing.
 *
 * ## Commit flow contract
 * - [tap] (re)starts the key's quiet timer; ZERO commits happen during a tap burst.
 * - When the timer fires, [canCommit] gates the dispatch: while it returns `false` (the key's
 *   dispatch is in flight) the commit RE-WAITS one quiet window instead of dispatching into a
 *   guaranteed rejection. Bounded: the dispatcher always clears in-flight in `finally`.
 * - After a commit, the working value is RETAINED for [settleMs] (display retention — prevents
 *   the screen snapping back to the stale live value during the echo window), then auto-cleared.
 *   A new [tap] during the settle window re-arms instead.
 * - [cancel]/[cancelAll] drop the working value without committing (resets supersede).
 * - [flush] commits every key with a still-pending (uncommitted) timer immediately and
 *   synchronously; [dispose] = [flush] + cancel everything (commit-on-dispose: a same-frame nav
 *   never silently drops the adjustment — [[dinghy-compose-write-scope-cancellation]]).
 *
 * ## Accepted corners (documented by design)
 * 1. **flush-while-in-flight:** [flush] does NOT wait on [canCommit] — a flush into an in-flight
 *    key gets dispatcher-rejected and flashes (rare: requires a nav-out inside the commit+echo
 *    window). The rejectedKey flash stays wired as the fallback signal.
 * 2. **settle-clear racing a slow echo:** if the echo takes longer than [settleMs], the display
 *    briefly snaps to the stale live value before the echo lands. Echoes land well under 2s in
 *    the normal case, making the working→live swap invisible.
 *
 * Plain Kotlin, zero Compose/Android imports — host-testable like [CommandDispatcher]. All access
 * is main-confined in production (composition callbacks + Main.immediate scope).
 *
 * @param scope     the coroutine scope timers run on (tests inject a TestScope; production callers
 *                  use the secondary constructor which creates a Main.immediate-owned scope).
 * @param quietMs   the trailing quiet window — a commit fires this long after the LAST tap.
 * @param settleMs  post-commit working-value display retention before auto-clear.
 * @param canCommit fire-time gate: return `false` to reschedule the commit one quiet window
 *                  (e.g. while the key's dispatch key is in the dispatcher's in-flight set).
 * @param onCommit  the single commit-time write path (key, final working value).
 */
class TrailingCommitBatcher(
    private val scope: CoroutineScope,
    private val quietMs: Long = QUIET_WINDOW_MS,
    private val settleMs: Long = SETTLE_CLEAR_MS,
    private val canCommit: (String) -> Boolean = { true },
    private val onCommit: (key: String, value: Double) -> Unit,
    private val ownsScope: Boolean = false,
) {
    /**
     * Production wiring: owns an internally-created `Main.immediate` scope ([dispose] cancels it).
     */
    constructor(
        quietMs: Long = QUIET_WINDOW_MS,
        settleMs: Long = SETTLE_CLEAR_MS,
        canCommit: (String) -> Boolean = { true },
        onCommit: (key: String, value: Double) -> Unit,
    ) : this(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        quietMs = quietMs,
        settleMs = settleMs,
        canCommit = canCommit,
        onCommit = onCommit,
        ownsScope = true,
    )

    private val _working = MutableStateFlow<PersistentMap<String, Double>>(persistentMapOf())

    /**
     * The per-key working values — the screen renders `working[key] ?: liveValue`. PersistentMap
     * keeps the Compose param @Stable (rejectTicks / Phase-22 discipline).
     */
    val working: StateFlow<PersistentMap<String, Double>> = _working.asStateFlow()

    /** Store the ALREADY-CLAMPED [value] for [key] and (re)start its trailing quiet timer. */
    fun tap(key: String, value: Double) {
        TODO("RED stub — implemented in the GREEN commit")
    }

    /** Drop [key]'s working value and timers WITHOUT committing (a reset supersedes it). */
    fun cancel(key: String) {
        TODO("RED stub — implemented in the GREEN commit")
    }

    /** [cancel] every key (dispatch-Failure revert: working values fall back to live). */
    fun cancelAll() {
        TODO("RED stub — implemented in the GREEN commit")
    }

    /**
     * Commit every key with a still-pending (uncommitted) timer immediately and synchronously.
     * Keys already committed and merely in settle-retention are NOT re-committed.
     */
    fun flush() {
        TODO("RED stub — implemented in the GREEN commit")
    }

    /** [flush], then cancel all timers (and the owned production scope). Commit-on-dispose. */
    fun dispose() {
        TODO("RED stub — implemented in the GREEN commit")
    }

    companion object {
        /** Trailing quiet window (ms) — exceeds the dispatcher's 400ms debounce BY DESIGN, so a
         *  rescheduled commit can never hit the debounce either. */
        const val QUIET_WINDOW_MS = 500L

        /** Post-commit working-value display retention (ms) before auto-clear. */
        const val SETTLE_CLEAR_MS = 2_000L
    }
}
