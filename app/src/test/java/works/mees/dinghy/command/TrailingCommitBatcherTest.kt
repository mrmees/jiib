package works.mees.dinghy.command

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host contract tests for [TrailingCommitBatcher] (quick-rmr) — the trailing-commit debouncer
 * behind the stepper batching: N taps inside the quiet window → exactly ONE commit carrying the
 * final working value; taps during an in-flight commit accumulate into a follow-up commit;
 * dispose flushes (commit-on-dispose); cancel drops; per-key independence; post-commit settle
 * retention of the working value.
 *
 * Written RED-first against the not-yet-implemented class (typed assertions, compiling stubs —
 * [[dinghy-wave0-red-scaffold-compile]]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrailingCommitBatcherTest {

    private data class Commit(val key: String, val value: Double)

    /**
     * 5 taps on one key inside the 500ms quiet window → ZERO commits during the burst; one quiet
     * window after the LAST tap → exactly ONE onCommit with the FINAL value.
     */
    @Test
    fun burst_commitsOnceWithFinalValue() = runTest(UnconfinedTestDispatcher()) {
        val commits = mutableListOf<Commit>()
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            onCommit = { key, value -> commits += Commit(key, value) },
        )

        // Taps at t = 0 / 100 / 200 / 300 / 400 ms, values 101..105.
        for (i in 1..5) {
            batcher.tap("flow", 100.0 + i)
            runCurrent()
            assertTrue("no commits during the burst (tap $i)", commits.isEmpty())
            if (i < 5) advanceTimeBy(100)
        }

        // Quiet window from the LAST tap (t=400): fires at t=900.
        advanceTimeBy(499)
        runCurrent()
        assertTrue("no commit before lastTap+quietMs", commits.isEmpty())
        advanceTimeBy(2)
        runCurrent()

        assertEquals("exactly one commit after the quiet window", 1, commits.size)
        assertEquals("commit carries the FINAL working value", Commit("flow", 105.0), commits[0])
        batcher.dispose()
    }

    /** A tap inside the quiet window RESTARTS it: tap@0 + tap@400 → no commit at 500; commit at 900. */
    @Test
    fun tapRestartsQuietWindow() = runTest(UnconfinedTestDispatcher()) {
        val commits = mutableListOf<Commit>()
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            onCommit = { key, value -> commits += Commit(key, value) },
        )

        batcher.tap("scv", 5.0)
        runCurrent()
        advanceTimeBy(400)
        batcher.tap("scv", 5.1)
        runCurrent()

        advanceTimeBy(101) // t=501 — past the FIRST tap's window, inside the restarted one.
        runCurrent()
        assertTrue("no commit at 500ms — the second tap restarted the window", commits.isEmpty())

        advanceTimeBy(400) // t=901 — past lastTap(400)+500.
        runCurrent()
        assertEquals("one commit at lastTap+quietMs", listOf(Commit("scv", 5.1)), commits)
        batcher.dispose()
    }

    /**
     * Taps landing DURING the in-flight commit accumulate into a NEW working value that commits
     * after the next quiet window once [TrailingCommitBatcher]'s canCommit gate reopens — no
     * rejection, no lockout. (canCommit=false simulates the key's dispatch being in flight.)
     */
    @Test
    fun tapDuringInFlightCommit_producesSecondCommit() = runTest(UnconfinedTestDispatcher()) {
        val commits = mutableListOf<Commit>()
        var allowCommit = true
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            canCommit = { allowCommit },
            onCommit = { key, value -> commits += Commit(key, value) },
        )

        // Commit 1 fires normally.
        batcher.tap("flow", 101.0)
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertEquals("first commit fired", listOf(Commit("flow", 101.0)), commits)

        // The key's dispatch is now "in flight": new taps accumulate.
        allowCommit = false
        batcher.tap("flow", 102.0)
        runCurrent()
        advanceTimeBy(501) // quiet window elapses while canCommit=false → NO dispatch (reschedule).
        runCurrent()
        assertEquals("no commit while canCommit=false (rescheduled, not rejected)", 1, commits.size)

        // In-flight clears: the next scheduled check commits ONCE with the new value.
        allowCommit = true
        advanceTimeBy(501)
        runCurrent()
        assertEquals(
            "second commit after the gate reopens — values in order",
            listOf(Commit("flow", 101.0), Commit("flow", 102.0)),
            commits,
        )
        batcher.dispose()
    }

    /** dispose() before the window elapses → exactly one commit, fired synchronously (commit-on-dispose). */
    @Test
    fun flushOnDispose_commitsPendingImmediately() = runTest(UnconfinedTestDispatcher()) {
        val commits = mutableListOf<Commit>()
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            onCommit = { key, value -> commits += Commit(key, value) },
        )

        batcher.tap("flow", 103.0)
        runCurrent()
        assertTrue("not committed yet (mid-window)", commits.isEmpty())

        batcher.dispose() // NO time advance, NO runCurrent — the flush must be synchronous.
        assertEquals(
            "dispose flushes the pending working value synchronously",
            listOf(Commit("flow", 103.0)),
            commits,
        )

        // And nothing further ever fires.
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("no duplicate commit after dispose", 1, commits.size)
    }

    /** cancel(key) drops the working value — no commit ever; working no longer contains the key. */
    @Test
    fun cancelDropsWorking() = runTest(UnconfinedTestDispatcher()) {
        val commits = mutableListOf<Commit>()
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            onCommit = { key, value -> commits += Commit(key, value) },
        )

        batcher.tap("flow", 104.0)
        runCurrent()
        assertEquals("tap stores the working value", 104.0, batcher.working.value["flow"])

        batcher.cancel("flow")
        assertFalse("cancel drops the working value", batcher.working.value.containsKey("flow"))

        advanceTimeBy(10_000)
        runCurrent()
        assertTrue("no commit ever after cancel", commits.isEmpty())
        batcher.dispose()
    }

    /** Interleaved taps on two keys → each key commits its OWN final value exactly once. */
    @Test
    fun independentKeys() = runTest(UnconfinedTestDispatcher()) {
        val commits = mutableListOf<Commit>()
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            onCommit = { key, value -> commits += Commit(key, value) },
        )

        batcher.tap("flow", 101.0)
        runCurrent()
        advanceTimeBy(100)
        batcher.tap("scv", 5.1)
        runCurrent()
        advanceTimeBy(100)
        batcher.tap("flow", 102.0) // flow's final value
        runCurrent()

        advanceTimeBy(601)
        runCurrent()

        assertEquals("each key commits exactly once", 2, commits.size)
        assertEquals(
            "each key commits its own final value",
            setOf(Commit("flow", 102.0), Commit("scv", 5.1)),
            commits.toSet(),
        )
        batcher.dispose()
    }

    /**
     * After a commit, working[key] survives for the settle window (display retention) then
     * auto-clears; a new tap DURING the settle window re-arms (cancels the clear) instead.
     */
    @Test
    fun workingAutoClearsAfterSettle() = runTest(UnconfinedTestDispatcher()) {
        val commits = mutableListOf<Commit>()
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            onCommit = { key, value -> commits += Commit(key, value) },
        )

        // Commit at t=501.
        batcher.tap("flow", 105.0)
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertEquals("committed", 1, commits.size)
        assertEquals("working retained right after commit", 105.0, batcher.working.value["flow"])

        // Still retained late in the settle window (settle clears at commit+2000 = t=2501).
        advanceTimeBy(1_990) // t=2491
        runCurrent()
        assertEquals("working retained through the settle window", 105.0, batcher.working.value["flow"])

        // A tap during the settle window RE-ARMS instead of clearing.
        batcher.tap("flow", 106.0)
        runCurrent()
        advanceTimeBy(2_000) // far past the ORIGINAL settle deadline; re-arm must have cancelled it.
        runCurrent()
        assertEquals(
            "re-armed tap committed its new value",
            listOf(Commit("flow", 105.0), Commit("flow", 106.0)),
            commits,
        )
        assertEquals("working still present (second settle window)", 106.0, batcher.working.value["flow"])

        // The SECOND commit's settle window expires → auto-clear.
        advanceTimeBy(2_000)
        runCurrent()
        assertFalse(
            "working auto-clears after the settle window",
            batcher.working.value.containsKey("flow"),
        )
        batcher.dispose()
    }
}
