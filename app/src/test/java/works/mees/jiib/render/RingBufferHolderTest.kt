package works.mees.jiib.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Wave-0 holder test (03-VALIDATION.md): the bounded [RingBuffer] (D-12) is the toolkit-agnostic
 * data substrate both render primitives draw. Mirrors ConflationTest's plain-JUnit4 idiom.
 *
 * Locks the capacity bound, oldest-eviction, snapshot stability (defensive copy — Pitfall 4: the
 * draw side reuses ITS arrays, the buffer must hand a clean copy), insertion order, and that
 * concurrent push/snapshot never throws nor returns a torn array.
 */
class RingBufferHolderTest {

    @Test
    fun overCapacity_evictsOldest_reportsCapacitySize() {
        val buf = RingBuffer(capacity = 120)
        for (i in 1..200) buf.push(i.toFloat())

        assertEquals("size is bounded by capacity", 120, buf.size)
        val snap = buf.snapshot()
        assertEquals(120, snap.size)
        // 200 pushed, capacity 120 → first retained is the 81st pushed value (1..80 evicted).
        assertEquals("oldest retained is the 81st pushed value", 81f, snap.first())
        assertEquals("newest is the last pushed value", 200f, snap.last())
    }

    @Test
    fun snapshot_isStableDefensiveCopy() {
        val buf = RingBuffer(capacity = 8)
        for (i in 1..5) buf.push(i.toFloat())

        val snap = buf.snapshot()
        val before = snap.copyOf()

        // Mutating the buffer after the snapshot must NOT alter the returned array.
        for (i in 6..20) buf.push(i.toFloat())
        assertArrayEquals("snapshot is a defensive copy, immune to later pushes", before, snap, 0f)
    }

    @Test
    fun burstThenSnapshot_yieldsLatestCapacityInInsertionOrder() {
        val buf = RingBuffer(capacity = 4)
        for (i in 1..10) buf.push(i.toFloat())

        val snap = buf.snapshot()
        // Latest `capacity` values, oldest→newest.
        assertArrayEquals(floatArrayOf(7f, 8f, 9f, 10f), snap, 0f)
    }

    @Test
    fun emptyBuffer_snapshotIsEmpty() {
        val buf = RingBuffer(capacity = 16)
        assertEquals(0, buf.size)
        assertEquals(0, buf.snapshot().size)
    }

    @Test
    fun belowCapacity_keepsAllInOrder() {
        val buf = RingBuffer(capacity = 16)
        for (i in 1..5) buf.push(i.toFloat())
        assertEquals(5, buf.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f), buf.snapshot(), 0f)
    }

    @Test
    fun concurrentPushAndSnapshot_neverThrowsNorTearsArray() {
        val buf = RingBuffer(capacity = 120)
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val error = AtomicReference<Throwable?>(null)

        val writers = (0 until 2).map {
            Runnable {
                start.await()
                for (i in 0 until 5_000) buf.push(i.toFloat())
            }
        }
        val readers = (0 until 2).map {
            Runnable {
                start.await()
                for (i in 0 until 5_000) {
                    val snap = buf.snapshot()
                    // A torn/over-capacity array would surface here.
                    assertTrue(snap.size <= 120)
                }
            }
        }
        (writers + readers).forEach { task ->
            pool.submit {
                try {
                    task.run()
                } catch (t: Throwable) {
                    error.compareAndSet(null, t)
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue("concurrent work completed", pool.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals("no concurrent push/snapshot threw or tore an array", null, error.get())
        assertEquals("size stays bounded under concurrency", 120, buf.size)
    }
}
