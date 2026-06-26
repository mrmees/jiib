package works.mees.jiib.state

import works.mees.jiib.ui.console.ConsoleLine
import works.mees.jiib.ui.console.ConsoleSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * 08-02 Task 2 — the object-typed bounded ring [ConsoleScrollback] (CONS-02 / D-02).
 *
 * Proves: push past cap evicts the OLDEST (size pinned at cap, newest retained); [replaceAll] caps
 * to capacity keeping the NEWEST; [snapshot] is a defensive copy (the ring never hands out its
 * backing store); concurrent pushes never corrupt size. RESEARCH Pitfall 1: this holds `ConsoleLine`
 * objects, NOT `Float` samples — it is NOT `render/RingBuffer`.
 */
class ConsoleScrollbackTest {

    private fun line(msg: String) =
        ConsoleLine(rawMessage = msg, severity = ConsoleSeverity.classify(msg), timeEpoch = null)

    @Test
    fun pushPastCap_evictsOldest_retainsNewest() {
        val ring = ConsoleScrollback(capacity = 1000)
        repeat(1001) { ring.push(line("line $it")) }
        val snap = ring.snapshot()
        assertEquals("size pinned at cap", 1000, snap.size)
        // The very first line (line 0) was evicted; the newest (line 1000) is present and last.
        assertEquals("line 1", snap.first().rawMessage)
        assertEquals("line 1000", snap.last().rawMessage)
    }

    @Test
    fun replaceAll_capsToCapacity_keepingNewest() {
        val ring = ConsoleScrollback(capacity = 1000)
        ring.push(line("stale"))
        val incoming = (0 until 1500).map { line("backfill $it") }
        ring.replaceAll(incoming)
        val snap = ring.snapshot()
        assertEquals(1000, snap.size)
        // The newest 1000 of the 1500 are kept (backfill 500..1499); the prior "stale" line is gone.
        assertEquals("backfill 500", snap.first().rawMessage)
        assertEquals("backfill 1499", snap.last().rawMessage)
    }

    @Test
    fun replaceAll_smallerThanCap_keepsAll() {
        val ring = ConsoleScrollback(capacity = 1000)
        ring.replaceAll(listOf(line("a"), line("b"), line("c")))
        assertEquals(listOf("a", "b", "c"), ring.snapshot().map { it.rawMessage })
    }

    @Test
    fun snapshot_isDefensiveCopy() {
        val ring = ConsoleScrollback(capacity = 10)
        ring.push(line("first"))
        val snap = ring.snapshot()
        // Mutating the ring after the snapshot does not change the already-returned list.
        ring.push(line("second"))
        assertEquals(1, snap.size)
        assertEquals("first", snap.single().rawMessage)
        // And the new snapshot reflects the push.
        assertEquals(2, ring.snapshot().size)
    }

    @Test
    fun concurrentPushes_doNotCorruptSize() {
        val ring = ConsoleScrollback(capacity = 1000)
        val threads = 8
        val perThread = 5000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                start.await()
                repeat(perThread) { i -> ring.push(line("t$t-$i")) }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue("workers finished", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        // Far more pushes than capacity → size must be pinned EXACTLY at the cap, never corrupted.
        assertEquals(1000, ring.size)
        assertEquals(1000, ring.snapshot().size)
    }
}
