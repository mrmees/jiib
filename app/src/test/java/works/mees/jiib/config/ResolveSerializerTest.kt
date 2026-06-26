package works.mees.jiib.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Host-pure proof of [ResolveSerializer] — the FIFO serializer that fixes the flaky-discovery bug
 * (the old listener DROPPED a service found while another resolve was in flight, losing the 2nd
 * printer). The serializer keeps the framework's one-resolve-at-a-time requirement but QUEUES
 * pending finds instead of discarding them, and resolves each distinct service exactly once.
 */
class ResolveSerializerTest {

    @Test
    fun firstFound_resolvesImmediately() {
        val s = ResolveSerializer()
        assertEquals("a", s.onFound("a"))
    }

    @Test
    fun secondFoundWhileBusy_isQueuedNotDropped() {
        val s = ResolveSerializer()
        assertEquals("a", s.onFound("a"))     // a resolves now
        assertNull(s.onFound("b"))            // b found while a in flight → QUEUED (was: dropped)
        assertEquals("b", s.onResolveDone())  // a done → b is next
        assertNull(s.onResolveDone())         // b done → idle
    }

    @Test
    fun queue_drainsFifo() {
        val s = ResolveSerializer()
        assertEquals("a", s.onFound("a"))
        assertNull(s.onFound("b"))
        assertNull(s.onFound("c"))
        assertEquals("b", s.onResolveDone())
        assertEquals("c", s.onResolveDone())
        assertNull(s.onResolveDone())
    }

    @Test
    fun duplicateFoundWhileBusy_isIgnored() {
        val s = ResolveSerializer()
        assertEquals("a", s.onFound("a"))
        assertNull(s.onFound("a"))            // duplicate announcement → ignored, NOT enqueued
        assertNull(s.onResolveDone())         // nothing queued
    }

    @Test
    fun reAnnounceAfterCompletion_isNotReResolved() {
        val s = ResolveSerializer()
        assertEquals("a", s.onFound("a"))
        assertNull(s.onResolveDone())         // a done, idle
        assertNull(s.onFound("a"))            // re-announce of already-resolved a → ignored
    }

    @Test
    fun resolveDone_whenIdle_returnsNull() {
        val s = ResolveSerializer()
        assertNull(s.onResolveDone())
    }
}
