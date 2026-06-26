package works.mees.jiib.command

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /** Per-key pending quiet-window timer Jobs (a key here = an UNCOMMITTED working value). */
    private val timerJobs = mutableMapOf<String, Job>()

    /** Per-key post-commit settle-clear Jobs (a key here = committed, in display retention). */
    private val settleJobs = mutableMapOf<String, Job>()

    /** Store the ALREADY-CLAMPED [value] for [key] and (re)start its trailing quiet timer. */
    fun tap(key: String, value: Double) {
        // Cancel-and-restart timer discipline: cancel BEFORE relaunch so no stale timer can fire
        // (the holder's timeoutJob hygiene); a tap during settle retention re-arms (cancels clear).
        settleJobs.remove(key)?.cancel()
        timerJobs.remove(key)?.cancel()
        _working.value = _working.value.put(key, value)
        timerJobs[key] = scope.launch {
            delay(quietMs)
            // canCommit reschedule gate: while the key's dispatch is in flight, re-wait one quiet
            // window instead of dispatching into a guaranteed rejection. Bounded — the dispatcher
            // always removes the in-flight key in `finally`.
            while (!canCommit(key)) delay(quietMs)
            commitNow(key)
        }
    }

    /** Drop [key]'s working value and timers WITHOUT committing (a reset supersedes it). */
    fun cancel(key: String) {
        timerJobs.remove(key)?.cancel()
        settleJobs.remove(key)?.cancel()
        _working.value = _working.value.remove(key)
    }

    /** [cancel] every key (dispatch-Failure revert: working values fall back to live). */
    fun cancelAll() {
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
        settleJobs.values.forEach { it.cancel() }
        settleJobs.clear()
        _working.value = persistentMapOf()
    }

    /**
     * Commit every key with a still-pending (uncommitted) timer immediately and synchronously —
     * NO [canCommit] wait (accepted corner 1). Keys already committed and merely in
     * settle-retention are NOT re-committed.
     */
    fun flush() {
        for (key in timerJobs.keys.toList()) {
            timerJobs.remove(key)?.cancel()
            val value = _working.value[key] ?: continue
            onCommit(key, value)
            scheduleSettleClear(key)
        }
    }

    /** [flush], then cancel all timers (and the owned production scope). Commit-on-dispose. */
    fun dispose() {
        flush()
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
        settleJobs.values.forEach { it.cancel() }
        settleJobs.clear()
        // The dispatched network call rides CommandDispatcher's app-lifetime scope, so it
        // completes even though this (production-owned) scope dies with the screen.
        if (ownsScope) scope.cancel()
    }

    /** Fire [onCommit] with [key]'s current working value and start its settle-clear retention. */
    private fun commitNow(key: String) {
        timerJobs.remove(key)
        val value = _working.value[key] ?: return
        onCommit(key, value)
        scheduleSettleClear(key)
    }

    /** Retain working[key] for [settleMs] (display retention over the echo window), then clear. */
    private fun scheduleSettleClear(key: String) {
        settleJobs.remove(key)?.cancel()
        settleJobs[key] = scope.launch {
            delay(settleMs)
            settleJobs.remove(key)
            _working.value = _working.value.remove(key)
        }
    }

    companion object {
        /** Trailing quiet window (ms) — exceeds the dispatcher's 400ms debounce BY DESIGN, so a
         *  rescheduled commit can never hit the debounce either. */
        const val QUIET_WINDOW_MS = 500L

        /** Post-commit working-value display retention (ms) before auto-clear. */
        const val SETTLE_CLEAR_MS = 2_000L
    }
}
