package works.mees.dinghy.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8

/**
 * Lean Okio multipart `multipart/x-mixed-replace` MJPEG decoder (CAM-01 rung 1; 10-RESEARCH § Pattern 2/3,
 * Pitfall 4/5; Security DoS T-10-02). The GENUINELY-new custom code of Phase 10 — the multipart boundary
 * scanner — lives here, and it is host-testable WITHOUT Android: the JPEG-byte extraction is parameterised
 * over an injectable [decode] function so the byte-scanner can be proven on the JVM (the test injects a
 * recorder; production injects [BitmapFactory.decodeByteArray] via [bitmaps]).
 *
 * Wire format scanned (per `--<boundary>` part):
 * ```
 *   --<boundary>\r\n
 *   Content-Type: image/jpeg\r\n
 *   [Content-Length: N\r\n]          <- OPTIONAL (ustreamer usually sends it; some servers don't)
 *   \r\n
 *   <JPEG bytes: FF D8 … FF D9>
 *   \r\n
 *   …
 *   --<boundary>--\r\n               <- trailer
 * ```
 * If `Content-Length` is present we read exactly N bytes; otherwise we scan for the JPEG EOI (`FF D9`) /
 * the next boundary (the no-Content-Length path). A JPEG split across multiple `source.read()` chunks is
 * reassembled transparently — Okio's [BufferedSource.indexOf]/`require`/`readByteArray` block until enough
 * bytes arrive (the split-JPEG fixture proof, Pitfall 6).
 *
 * DECODE DISCIPLINE (D-06, parameterised into [decode]):
 *  - ONE reused mutable bitmap via `inBitmap`+`inMutable`; NO per-frame allocation in steady state.
 *  - `inSampleSize` computed ONCE (the camera res is stable) so the decoded bitmap ≈ the view px.
 *  - An `inBitmap` size-mismatch (`IllegalArgumentException`) drops `inBitmap` for one frame, re-establishes (Pitfall 5).
 *
 * DROP-BEHIND (D-06 / SC-1): frames hand off over a [Channel] with [BufferOverflow.DROP_OLDEST] capacity 1
 * (conflated latest-wins). A slow consumer NEVER backs up the decoder — the newest frame always wins; the
 * decode loop never blocks on the View.
 *
 * OOM GUARD (Security T-10-02): an absurd `Content-Length` / an oversized accumulated part is REJECTED
 * (skipped, not decoded) at [MAX_PART_BYTES] — a hostile/oversized frame cannot OOM the 2GB floor.
 *
 * CANCELLATION (MoonrakerSocket awaitClose idiom): the loop honors coroutine cancellation; the CALLER owns
 * the [okhttp3.ResponseBody] and must close it when the scope ends (no leak).
 */
class MjpegStreamDecoder<T>(
    private val boundary: String,
    private val decode: (jpeg: ByteArray) -> T?,
) {

    private val channel = Channel<T>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Drop-behind frame stream (conflated latest-wins) — a slow consumer never backs up the decoder. */
    val frames: Flow<T> = channel.receiveAsFlow()

    private val boundaryMarker = "--$boundary".encodeUtf8()
    private val headerEnd = "\r\n\r\n".encodeUtf8()
    private val crlf = "\r\n".encodeUtf8()

    /**
     * Stream [source] to exhaustion (or coroutine cancellation), decoding each part via [decode] and
     * handing frames off drop-behind. Returns the number of frames successfully decoded. Closes the
     * frame channel on completion. Does NOT close [source] — the caller owns the response body.
     */
    suspend fun decodeStream(source: BufferedSource): Int {
        var frameCount = 0
        try {
            while (currentCoroutineContext().isActive) {
                val jpeg = readNextPart(source) ?: break // end of stream / trailer
                val decoded = decode(jpeg) ?: continue    // un-decodable part → skip, keep streaming
                channel.trySend(decoded)                  // drop-behind: never suspends, newest wins
                frameCount++
            }
        } finally {
            channel.close()
        }
        return frameCount
    }

    /**
     * Read the next JPEG part's bytes, or null at end-of-stream / the `--boundary--` trailer.
     *
     * Advances past the next `--<boundary>` marker, reads the part headers, then the JPEG body — by
     * `Content-Length` when present, else by scanning to the JPEG EOI / next boundary. Okio blocks until
     * enough bytes arrive, so a JPEG split across reads is reassembled transparently.
     */
    private fun readNextPart(source: BufferedSource): ByteArray? {
        // 1. Find the next boundary marker.
        val markerIndex = source.indexOf(boundaryMarker)
        if (markerIndex < 0) return null // no further boundary → stream ended.
        source.skip(markerIndex + boundaryMarker.size) // consume up to and including "--<boundary>".

        // 2. Trailer check: "--<boundary>--" means end of stream.
        if (source.request(2) && source.buffer[0] == '-'.code.toByte() && source.buffer[1] == '-'.code.toByte()) {
            return null
        }

        // 3. Read the part headers (up to the blank line) and look for an OPTIONAL Content-Length.
        val headerEndIndex = source.indexOf(headerEnd)
        if (headerEndIndex < 0) return null // malformed: no header terminator → stream ended.
        val headerBytes = source.readByteArray(headerEndIndex + headerEnd.size)
        val contentLength = parseContentLength(headerBytes.decodeToString())

        // 4. Read the JPEG body.
        return if (contentLength != null) {
            // OOM guard (T-10-02): reject an absurd Content-Length — skip the part, do NOT allocate it.
            if (contentLength <= 0 || contentLength > MAX_PART_BYTES) {
                skipToNextBoundaryOrEof(source)
                ByteArray(0) // a 0-length "part" → decode(...) returns null → skipped by the loop.
            } else {
                source.require(contentLength.toLong()) // blocks until N bytes arrive (split-safe).
                source.readByteArray(contentLength.toLong())
            }
        } else {
            // No Content-Length: scan to the JPEG EOI (FF D9), bounded by the OOM ceiling.
            readJpegByEoi(source)
        }
    }

    /**
     * No-Content-Length path: read bytes up to and including the JPEG EOI marker (`FF D9`). Bounded at
     * [MAX_PART_BYTES] — an unterminated/hostile part that never yields an EOI is rejected (returns empty,
     * which the loop skips) rather than accumulating without bound (T-10-02).
     */
    private fun readJpegByEoi(source: BufferedSource): ByteArray {
        val eoiIndex = source.indexOf(JPEG_EOI)
        if (eoiIndex < 0 || eoiIndex + JPEG_EOI.size > MAX_PART_BYTES) {
            // No EOI within the ceiling → drop the rest defensively (skip to a boundary if any).
            skipToNextBoundaryOrEof(source)
            return ByteArray(0)
        }
        return source.readByteArray(eoiIndex + JPEG_EOI.size)
    }

    /** Skip to (but not past) the next boundary marker, or drain the source at EOF. Used after a reject. */
    private fun skipToNextBoundaryOrEof(source: BufferedSource) {
        val next = source.indexOf(boundaryMarker)
        if (next < 0) {
            // No further boundary — drain so the outer loop's next indexOf returns -1 (clean stop).
            source.skip(source.buffer.size)
            while (!source.exhausted()) source.skip(source.buffer.size)
        } else {
            source.skip(next)
        }
    }

    private companion object {
        /** JPEG end-of-image marker (`FF D9`). */
        val JPEG_EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte()).let {
            okio.ByteString.of(*it)
        }

        /**
         * Hostile-frame OOM ceiling (Security T-10-02). A real MJPEG JPEG frame at the floor's display
         * resolution is well under a few hundred KB; 8 MB is a generous cap that still rejects an absurd
         * `Content-Length` (e.g. 2 GB) before any allocation. TUNABLE on-device in plan 10-08.
         */
        const val MAX_PART_BYTES: Int = 8 * 1024 * 1024

        /** Extract `Content-Length: N` from the part headers (case-insensitive), or null when absent. */
        fun parseContentLength(headers: String): Int? =
            headers.lineSequence()
                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.toIntOrNull()
    }
}

/**
 * Decode policy constants for the production [BitmapFactory] path (D-06). Named + centralised so plan
 * 10-08 can PIN them on-device (flox / Adreno-320) WITHOUT surgery — A1/A2 are ASSUMED here, not facts.
 */
object MjpegDecodePolicy {
    /**
     * The decode fps cap (A1 — ASSUMED). Holds zero-jank on the Adreno-320 floor per RESEARCH; re-pin on
     * flox in 10-08 (lower to ~8–10 if jank appears). The decoder itself is rate-agnostic (it decodes as
     * fast as frames arrive, drop-behind); the holder/throttle enforces this cap.
     */
    const val TARGET_FPS: Int = 12

    /**
     * Whether to decode opaque camera frames as RGB_565 (half the bytes of ARGB_8888, no alpha) — the A2
     * fallback memory posture for the 2GB floor. Default false (ARGB_8888); flip to true in 10-08 if 8888
     * GC-churns at the chosen resolution.
     */
    const val PREFER_RGB_565: Boolean = false

    /**
     * Largest legal `inSampleSize` (a power of two). The actual value is computed per-stream from the
     * frame size vs the view px; this caps the downscale so a tiny view never throws the decoder off.
     */
    const val MAX_SAMPLE_SIZE: Int = 16

    /** Compute the largest power-of-two `inSampleSize` keeping the decoded frame ≥ the target view px (D-06). */
    fun computeInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0 || srcWidth <= 0 || srcHeight <= 0) return 1
        var sample = 1
        var halfW = srcWidth / 2
        var halfH = srcHeight / 2
        while (halfW >= reqWidth && halfH >= reqHeight && sample < MAX_SAMPLE_SIZE) {
            sample *= 2
            halfW /= 2
            halfH /= 2
        }
        return sample
    }
}

/**
 * Build a production [MjpegStreamDecoder] that decodes each JPEG part into ONE reused mutable [Bitmap] via
 * `inBitmap`+`inSampleSize`+`inMutable` (D-06). [inSampleSize] is computed once by the caller (a cheap
 * `inJustDecodeBounds` first pass on the first frame — RESEARCH Pattern 3) and held fixed; the same
 * [BitmapFactory.Options] (carrying the reused `inBitmap`) is threaded across every frame so there is NO
 * per-frame allocation in steady state. An `inBitmap` size-mismatch (`IllegalArgumentException`, Pitfall 5)
 * drops `inBitmap` for one frame and re-establishes from the freshly-decoded bitmap.
 */
fun bitmaps(boundary: String, inSampleSize: Int): MjpegStreamDecoder<Bitmap> {
    val options = BitmapFactory.Options().apply {
        inMutable = true
        this.inSampleSize = inSampleSize.coerceAtLeast(1)
        inPreferredConfig =
            if (MjpegDecodePolicy.PREFER_RGB_565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    }
    return MjpegStreamDecoder(boundary) { jpeg ->
        if (jpeg.isEmpty()) return@MjpegStreamDecoder null
        try {
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)?.also { options.inBitmap = it }
        } catch (e: IllegalArgumentException) {
            // inBitmap size mismatch (cam res changed mid-stream) — drop reuse for one frame, re-establish.
            options.inBitmap = null
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)?.also { options.inBitmap = it }
        }
    }
}
