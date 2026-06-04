package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * COMPILING runtime-RED scaffold — fail()s until plan 10-02 REPLACES this body with typed assertions
 * against the URL resolver/rewriter (`net/WebcamUrl.kt`). See WebcamListParseTest's KDoc for why the
 * body is a fail() stub (the cross-wave compile rule).
 *
 * What 10-02 must prove here (CAM-01 / D-09, T-V7): a relative `stream_url`/`snapshot_url` resolves
 * against the configured Moonraker host; `127.0.0.1`/`localhost`/`0.0.0.0`/`::1` hosts are rewritten
 * to the configured host (the #1 "works in Mainsail, blank here" gotcha); the `?token=` query is
 * PRESERVED verbatim (E3 needs it); and the resolved URL is REDACTED (`?token=<redacted>`) before any
 * log/crash surface. The E3 golden carries the tokened snapshot to exercise preservation+redaction.
 */
class WebcamUrlResolverTest {

    @Test
    fun resolvesRelative_rewritesLocalhost_preservesToken_redactsInLogs() {
        fail("RED until plan 10-02 builds WebcamUrl resolver and replaces this scaffold body")
    }
}
