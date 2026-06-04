package works.mees.dinghy.webcam

import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

/**
 * Hardened MJPEG byte-stream test double (Phase 10, plan 10-01, Wave 0).
 *
 * Wraps one of the synthetic `.bin` fixtures (see `/fixtures/README_mjpeg_fixtures.txt`) as an Okio
 * [BufferedSource] — the exact streaming primitive the real `MjpegStreamDecoder` (built in 10-04)
 * will consume via `ResponseBody.source()`. References ONLY okio + this test package, so it COMPILES
 * day-one with no production webcam symbol (the cross-wave compile rule).
 *
 * Faithful-mock discipline (10-RESEARCH.md § Pitfall 6): the two modes encode the REAL behaviors a
 * lenient fake would hide —
 *  - [withContentLength] / [noContentLength]: a body WITH a `Content-Length` per part vs one WITHOUT
 *    (the latter forces the decoder's boundary / SOI–EOI scan path).
 *  - [splitAcrossReads] = true: the underlying [Source] hands the bytes back in arbitrary small
 *    chunks INCLUDING a chunk boundary that falls in the middle of a JPEG payload, so the decoder is
 *    proven to reassemble a JPEG split across two `source.read()` calls (the `mjpeg_split_jpeg.bin`
 *    intent — the split is a read-CHUNKING property applied here, not a property of the bytes).
 */
class FakeMjpegStream private constructor(
    private val fixtureBytes: ByteArray,
    private val chunkSize: Int?,
) {

    /** A fresh [BufferedSource] over the fixture, chunked per the chosen mode. */
    fun source(): BufferedSource {
        val whole = Buffer().apply { write(fixtureBytes) }
        val raw: Source = if (chunkSize == null) whole else ChunkingSource(whole, chunkSize)
        return raw.buffer()
    }

    /** The raw fixture bytes (e.g. to assert N frames decoded == frames in the body). */
    fun bytes(): ByteArray = fixtureBytes.copyOf()

    /**
     * A [Source] that yields AT MOST [chunkSize] bytes per [read], so a consumer that requests more
     * gets the body split across multiple reads — including mid-JPEG. This is the "split JPEG across
     * reads" hardening: a decoder that assumed one read == one part/frame breaks here.
     */
    private class ChunkingSource(
        delegate: Source,
        private val chunkSize: Int,
    ) : ForwardingSource(delegate) {
        override fun read(sink: Buffer, byteCount: Long): Long =
            super.read(sink, minOf(byteCount, chunkSize.toLong()))
    }

    companion object {
        const val BOUNDARY = FakeWebcamHttp.BOUNDARY

        private fun fixture(name: String): ByteArray =
            requireNotNull(FakeMjpegStream::class.java.getResourceAsStream("/fixtures/$name")) {
                "MJPEG fixture /fixtures/$name not found on the test classpath"
            }.use { it.readBytes() }

        /** Body with a `Content-Length` on every part (3 frames). Delivered whole unless chunked. */
        fun withContentLength(splitAcrossReads: Boolean = false): FakeMjpegStream =
            FakeMjpegStream(fixture("mjpeg_with_content_length.bin"), if (splitAcrossReads) 7 else null)

        /** Body with NO `Content-Length` on any part (3 frames) — forces the boundary-scan path. */
        fun noContentLength(splitAcrossReads: Boolean = false): FakeMjpegStream =
            FakeMjpegStream(fixture("mjpeg_no_content_length.bin"), if (splitAcrossReads) 7 else null)

        /** The split-JPEG body (2 frames) delivered in tiny chunks that bisect a JPEG payload. */
        fun splitJpeg(): FakeMjpegStream =
            FakeMjpegStream(fixture("mjpeg_split_jpeg.bin"), chunkSize = 5)
    }
}
