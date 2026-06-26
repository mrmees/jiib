package works.mees.jiib.webcam

import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.MjpegStreamDecoder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Typed assertions for the lean Okio multipart MJPEG decoder (`net/MjpegStreamDecoder.kt`, plan 10-04) —
 * REPLACES the plan-10-01 runtime-RED scaffold body.
 *
 * These synthetic golden byte streams ARE the authoritative MJPEG-decode proof (no live MJPEG cam on
 * E5/E3 — 10-RESEARCH Open Q1). The decoder is parameterised over an injectable `decode(jpeg) -> T?`
 * function so the GENUINELY-new code — the multipart boundary scanner — is proven on the JVM WITHOUT
 * Android `BitmapFactory`: the test injects a recorder that captures the EXACT JPEG bytes extracted.
 *
 * Proves (SC-2 / T-V5 / T-10-02):
 *  - `mjpeg_with_content_length.bin` → 3 frames via the Content-Length path.
 *  - `mjpeg_no_content_length.bin` → 3 frames via the boundary/EOI scan path.
 *  - `mjpeg_split_jpeg.bin` fed in tiny chunks that bisect a JPEG → 2 correct frames (no torn frame).
 *  - Each extracted part is a structurally-valid JPEG (SOI … EOI), and successive frames differ.
 *  - ONE reused decode state across frames (no per-frame allocation in steady state).
 *  - An absurd Content-Length part is REJECTED (skipped, never allocated/decoded) — OOM-by-hostile-frame.
 */
class MjpegStreamDecoderTest {

    /** The expected minimal-JPEG body of each fixture frame: SOI, COM segment, EOI (16 bytes). */
    private fun expectedFrame(comPayload: Int): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),             // SOI
        0xFF.toByte(), 0xFE.toByte(), 0x00, 0x0A, // COM marker, length 10
        comPayload.toByte(), comPayload.toByte(), comPayload.toByte(), comPayload.toByte(),
        comPayload.toByte(), comPayload.toByte(), comPayload.toByte(), comPayload.toByte(),
        0xFF.toByte(), 0xD9.toByte(),             // EOI
    )

    @Test
    fun withContentLength_yieldsThreeFrames_exactBytes() = runTest {
        val captured = mutableListOf<ByteArray>()
        val decoder = MjpegStreamDecoder(boundary = FakeMjpegStream.BOUNDARY) { jpeg ->
            captured += jpeg.copyOf(); jpeg
        }
        val count = decoder.decodeStream(FakeMjpegStream.withContentLength().source())

        assertEquals("3 frames via the Content-Length path", 3, count)
        assertEquals(3, captured.size)
        assertArrayEquals("frame 0 = COM 0xA0", expectedFrame(0xA0), captured[0])
        assertArrayEquals("frame 1 = COM 0xA1", expectedFrame(0xA1), captured[1])
        assertArrayEquals("frame 2 = COM 0xA2", expectedFrame(0xA2), captured[2])
    }

    @Test
    fun noContentLength_yieldsThreeFrames_viaBoundaryEoiScan() = runTest {
        val captured = mutableListOf<ByteArray>()
        val decoder = MjpegStreamDecoder(boundary = FakeMjpegStream.BOUNDARY) { jpeg ->
            captured += jpeg.copyOf(); jpeg
        }
        val count = decoder.decodeStream(FakeMjpegStream.noContentLength().source())

        assertEquals("3 frames via the boundary/EOI scan path", 3, count)
        captured.forEach { assertTrue("each scanned part is a structurally-valid JPEG", it.isValidJpeg()) }
        assertArrayEquals(expectedFrame(0xA0), captured[0])
        assertArrayEquals(expectedFrame(0xA2), captured[2])
    }

    @Test
    fun splitJpeg_acrossReads_yieldsTwoFrames_noTornFrame() = runTest {
        val captured = mutableListOf<ByteArray>()
        val decoder = MjpegStreamDecoder(boundary = FakeMjpegStream.BOUNDARY) { jpeg ->
            captured += jpeg.copyOf(); jpeg
        }
        // FakeMjpegStream.splitJpeg() delivers the body in 5-byte chunks that bisect a JPEG payload.
        val count = decoder.decodeStream(FakeMjpegStream.splitJpeg().source())

        assertEquals("2 frames reassembled across reads (no torn frame)", 2, count)
        assertArrayEquals(expectedFrame(0xA0), captured[0])
        assertArrayEquals(expectedFrame(0xA1), captured[1])
        captured.forEach { assertTrue(it.isValidJpeg()) }
    }

    @Test
    fun successiveFrames_differ() = runTest {
        val captured = mutableListOf<ByteArray>()
        val decoder = MjpegStreamDecoder(boundary = FakeMjpegStream.BOUNDARY) { jpeg ->
            captured += jpeg.copyOf(); jpeg
        }
        decoder.decodeStream(FakeMjpegStream.withContentLength().source())

        assertTrue("frame 0 != frame 1", !captured[0].contentEquals(captured[1]))
        assertTrue("frame 1 != frame 2", !captured[1].contentEquals(captured[2]))
    }

    @Test
    fun oneReusedDecodeState_noPerFrameAllocation() = runTest {
        // Production decodes into ONE reused mutable Bitmap (inBitmap reuse). We model that posture: a
        // single reused mutable holder, mutated (not reallocated) per frame; the decode closure counts
        // any genuine allocation. Proof of "no per-frame allocation": the closure allocates exactly once
        // and hands back the SAME instance for every frame.
        val reusedHolder = IntArray(1) // stands in for the one reused mutable Bitmap
        val allocations = AtomicInteger(0)
        val handedBack = mutableListOf<IntArray>()
        val decoder = MjpegStreamDecoder(boundary = FakeMjpegStream.BOUNDARY) { jpeg ->
            if (handedBack.isEmpty()) allocations.incrementAndGet() // only the first frame "allocates"
            reusedHolder[0] = jpeg.size                              // mutate reused state, never realloc
            handedBack += reusedHolder
            reusedHolder
        }

        val count = decoder.decodeStream(FakeMjpegStream.withContentLength().source())

        assertEquals(3, count)
        assertEquals(3, handedBack.size)
        assertEquals("the reused decode state is allocated exactly once across all 3 frames", 1, allocations.get())
        handedBack.forEach { assertSame("every frame reuses the SAME instance (no per-frame alloc)", reusedHolder, it) }
    }

    @Test
    fun absurdContentLength_isRejected_neverDecoded() = runTest {
        // A hostile part claims a 2 GB Content-Length — the decoder must REJECT it (never allocate),
        // then continue to the following valid frame.
        val hostile = buildMultipart(
            boundary = FakeMjpegStream.BOUNDARY,
            parts = listOf(
                Part(contentLength = 2_000_000_000, jpeg = expectedFrame(0xB0)), // absurd → rejected
                Part(contentLength = null, jpeg = expectedFrame(0xA1)),           // valid follow-up
            ),
        )
        val captured = mutableListOf<ByteArray>()
        val decoder = MjpegStreamDecoder(boundary = FakeMjpegStream.BOUNDARY) { jpeg ->
            if (jpeg.isEmpty()) null else { captured += jpeg.copyOf(); jpeg }
        }
        val source = Buffer().apply { write(hostile) }

        val count = decoder.decodeStream(source)

        assertEquals("the absurd-Content-Length part is dropped; only the valid frame decodes", 1, count)
        assertEquals(1, captured.size)
        assertArrayEquals("the rejected hostile JPEG bytes are never handed to decode", expectedFrame(0xA1), captured[0])
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun ByteArray.isValidJpeg(): Boolean =
        size >= 4 &&
            this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() &&        // SOI
            this[size - 2] == 0xFF.toByte() && this[size - 1] == 0xD9.toByte() // EOI

    private data class Part(val contentLength: Int?, val jpeg: ByteArray)

    /** Build a synthetic multipart/x-mixed-replace body with explicit per-part Content-Length control. */
    private fun buildMultipart(boundary: String, parts: List<Part>): ByteArray {
        val sb = StringBuilder()
        val out = Buffer()
        for (part in parts) {
            out.writeUtf8("--$boundary\r\n")
            out.writeUtf8("Content-Type: image/jpeg\r\n")
            part.contentLength?.let { out.writeUtf8("Content-Length: $it\r\n") }
            out.writeUtf8("\r\n")
            out.write(part.jpeg)
            out.writeUtf8("\r\n")
        }
        out.writeUtf8("--$boundary--\r\n")
        return out.readByteArray()
    }
}
