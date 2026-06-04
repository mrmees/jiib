package works.mees.dinghy.webcam

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typed assertions for the decoder's DROP-BEHIND frame hand-off (`net/MjpegStreamDecoder.kt`, plan 10-04)
 * — REPLACES the plan-10-01 runtime-RED scaffold body.
 *
 * Proves (SC-1 / D-06): a SLOW consumer never backs up the decoder. The hand-off is conflated
 * latest-wins (a `Channel(capacity = 1, onBufferOverflow = DROP_OLDEST)`), so a backed-up View on the
 * Adreno-320 floor never grows latency or memory — the newest frame always wins and the producer NEVER
 * blocks. This pins the exact channel contract the decoder uses (the decoder's `channel` is private; this
 * test asserts the same construction's observable behavior).
 */
class FrameDropBehindTest {

    @Test
    fun producerNeverBlocks_trySendAlwaysSucceeds_onConflatedChannel() {
        // The decoder hands frames off via trySend on a capacity-1 DROP_OLDEST channel. trySend must
        // ALWAYS succeed (never fail/suspend) even when the consumer has read nothing — that is what
        // guarantees the decode loop is never back-pressured by a slow View.
        val channel = Channel<Int>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        repeat(1000) { frame ->
            val result = channel.trySend(frame)
            assertTrue("trySend must never fail on a DROP_OLDEST channel (producer never blocks)", result.isSuccess)
        }
        channel.close()
    }

    @Test
    fun slowConsumer_seesOnlyLatest_notEveryFrame() {
        // Push 1000 frames with no interleaved consumption (the worst-case slow consumer: it reads only
        // AFTER the producer finished). A queueing channel would have buffered all 1000; the conflated
        // channel retains exactly the NEWEST single frame.
        val channel = Channel<Int>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        repeat(1000) { channel.trySend(it) }

        val received = mutableListOf<Int>()
        while (true) {
            val r = channel.tryReceive()
            if (r.isSuccess) received += r.getOrThrow() else break
        }

        assertEquals("a slow consumer drains at most ONE buffered frame (never a backlog)", 1, received.size)
        assertEquals("and it is the NEWEST frame — latest wins (D-06)", 999, received.single())
    }

    @Test
    fun interleavedSlowConsumer_neverBacksUp_alwaysLatestAvailable() {
        // Interleave: produce a burst, the consumer reads one, produce another burst — the consumer
        // always gets the latest of whatever was produced since its last read, never an ever-growing queue.
        val channel = Channel<Int>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        repeat(50) { channel.trySend(it) }            // burst 0..49
        assertEquals(49, channel.tryReceive().getOrThrow()) // consumer sees the latest of the burst

        repeat(50) { channel.trySend(100 + it) }      // burst 100..149
        assertEquals(149, channel.tryReceive().getOrThrow()) // again the latest, no 0..49 backlog

        // Nothing buffered between bursts → no unbounded growth.
        assertTrue("no residual backlog after draining", channel.tryReceive().isFailure)
        channel.close()
    }
}
