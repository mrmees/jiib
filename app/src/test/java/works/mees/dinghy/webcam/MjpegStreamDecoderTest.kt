package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * COMPILING runtime-RED scaffold — fail()s until plan 10-04 REPLACES this body with typed assertions
 * against the MJPEG decoder (`net/MjpegStreamDecoder.kt`). See WebcamListParseTest's KDoc for why the
 * body is a fail() stub (the cross-wave compile rule).
 *
 * What 10-04 must prove here (SC-2 / decode, T-V5 — these goldens ARE the authoritative MJPEG proof;
 * no live MJPEG cam exists on E5/E3, see 10-RESEARCH.md Open Q1): feeding the synthetic multipart
 * bodies via [FakeMjpegStream] yields the expected frame count for ALL three modes —
 * with-Content-Length, no-Content-Length (boundary/SOI-EOI scan path), and split-JPEG (a JPEG split
 * across two `source.read()` calls); ONE reused bitmap, no per-frame allocation; and an absurd
 * `Content-Length` part is REJECTED (the OOM-by-hostile-frame guard).
 */
class MjpegStreamDecoderTest {

    @Test
    fun decodesWithAndWithoutContentLength_andSplitJpeg_oneReusedBitmap_rejectsAbsurdContentLength() {
        fail("RED until plan 10-04 builds MjpegStreamDecoder and replaces this scaffold body")
    }
}
