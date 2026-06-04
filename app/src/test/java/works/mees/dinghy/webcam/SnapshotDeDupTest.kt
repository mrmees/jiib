package works.mees.dinghy.webcam

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.SnapshotPoller
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * 10-08 DE-DUP contract (the regression guard for "identical fetch = SUCCESS, not Transient"):
 *
 * MediaMTX's snapshot endpoint serves the SAME JPEG ~8× before regenerating (measured live on E3:
 * identical md5 for ~3–4s, then a new frame), so the snapshot feed is a SERVER-CAPPED slideshow at
 * ~0.3fps. Without de-dup the poller re-decodes ~8 identical ~100KB JPEGs per new image — wasted
 * CPU/battery on the 2GB Adreno-320 floor. [SnapshotPoller] now byte-compares each fetch against the
 * last and treats an identical fetch as a SUCCESSFUL poll (reset backoff, hold cadence) WITHOUT
 * decoding or re-emitting.
 *
 * These tests prove, with a recording `decode` + a [Call.Factory] serving repeated-then-changed bytes:
 *  - N identical fetches yield EXACTLY ONE decode + ONE emitted frame (no duplicate decode/emit).
 *  - The loop keeps polling at cadence across the identical fetches — NO spurious [Fetch.Transient] /
 *    backoff (the key contract: identical = success, never transient).
 *  - A subsequent CHANGED fetch yields a SECOND decode + a SECOND emit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotDeDupTest {

    private val imageA: ByteArray = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val imageB: ByteArray = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)

    @Test
    fun identicalFetches_decodeOnce_emitOnce_thenChangedFetch_decodesAgain() = runTest {
        // Serve image A four times (the MediaMTX "same JPEG for ~4s" reality), then image B.
        val gets = AtomicInteger(0)
        val script = listOf(imageA, imageA, imageA, imageA, imageB)
        val http = Call.Factory { request ->
            val i = gets.getAndIncrement()
            // After the script, keep serving the final image (more identicals — must NOT add decodes).
            val body = script.getOrElse(i) { imageB }
            jpegCall(request, body)
        }

        // A recording decode: count how many times we actually decode, and what bytes each decode saw.
        val decodes = mutableListOf<ByteArray>()
        val poller = SnapshotPoller(
            callFactory = http,
            decode = { jpeg -> decodes += jpeg; "frame-${jpeg.first()}" }, // non-null → a real frame
            rng = Random(0),
        )

        val emitted = mutableListOf<String>()
        val collector = backgroundScope.launch { poller.frames.toList(emitted) }
        val job = backgroundScope.launch { poller.poll("http://192.168.1.121/snap.jpg?token=X") }

        // Advance through the 4 identical A fetches + the changed B fetch (cadence = 500ms each).
        testScheduler.advanceTimeBy(3_000)
        testScheduler.runCurrent()

        // EXACTLY ONE decode for the run of identical A's, then ONE more for the changed B.
        assertEquals("A decoded once + B decoded once = 2 decodes across the identical run", 2, decodes.size)
        assertTrue("first decode saw image A", decodes[0].contentEquals(imageA))
        assertTrue("second decode saw image B (the changed image)", decodes[1].contentEquals(imageB))

        // The poller kept polling across the identical fetches — it issued WAY more GETs than decodes
        // (≥5: the 4 A's + the B + any further identical B's), proving identical=success kept cadence and
        // NEVER backed off (a Transient/backoff path would have throttled GETs and stalled the cadence).
        assertTrue("identical fetches kept the poll cadence (no backoff)", gets.get() >= 5)

        job.cancel()
        collector.cancel()
    }

    @Test
    fun identicalFetches_emitExactlyOneFrame_noDuplicateEmits() = runTest {
        // Serve the SAME image forever — the conflated channel must receive exactly ONE frame.
        val http = Call.Factory { request -> jpegCall(request, imageA) }
        val emitCount = AtomicInteger(0)
        val poller = SnapshotPoller(
            callFactory = http,
            decode = { _ -> emitCount.incrementAndGet(); "the-only-frame" },
            rng = Random(0),
        )

        // Drain frames into a sink so trySend never overflows-away the count we care about.
        val seen = mutableListOf<String>()
        val collector = backgroundScope.launch { poller.frames.toList(seen) }
        val job = backgroundScope.launch { poller.poll("http://192.168.1.121/snap.jpg") }

        testScheduler.advanceTimeBy(5_000) // ~10 poll cycles
        testScheduler.runCurrent()

        assertEquals("a never-changing image is decoded exactly once", 1, emitCount.get())
        assertEquals("…and emitted exactly once (no duplicate emits)", 1, seen.size)

        job.cancel()
        collector.cancel()
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun jpegCall(request: Request, body: ByteArray): Call = object : Call {
        override fun request(): Request = request
        override fun execute(): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("image/jpeg".toMediaType()))
            .build()

        override fun enqueue(responseCallback: okhttp3.Callback) = throw UnsupportedOperationException()
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): okio.Timeout = okio.Timeout.NONE
        override fun clone(): Call = this
    }
}
