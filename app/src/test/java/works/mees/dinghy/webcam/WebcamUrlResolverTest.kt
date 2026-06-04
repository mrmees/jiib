package works.mees.dinghy.webcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.net.redactWebcamUrl
import works.mees.dinghy.net.resolveWebcamUrl

/**
 * Typed assertions for the D-09 URL resolver/rewriter (`net/WebcamUrl.kt`, plan 10-02).
 *
 * Proves (CAM-01 / D-09, T-10-05 / Security V7): a relative URL resolves against the configured host;
 * `127.0.0.1`/`localhost`/`0.0.0.0`/`::1` are rewritten to the configured host with port/path/`?token=`
 * preserved; an already-correct absolute URL passes through unchanged; and a resolved URL is REDACTED
 * (`?token=<redacted>`) before any log/crash surface.
 *
 * The E3 golden's tokened snapshot URL is the token preservation+redaction subject.
 */
class WebcamUrlResolverTest {

    // Configured Moonraker host the tablet actually talks to (E3 from the goldens).
    private val cfg = ConnectionConfig(host = "192.168.1.121", port = 7125)

    @Test
    fun relativeUrl_resolvesAgainstConfiguredHost() {
        // The E3 blank-service cam's relative stream URL.
        val resolved = resolveWebcamUrl("/webcam2/?action=stream", cfg)
        assertEquals("http://192.168.1.121:7125/webcam2/?action=stream", resolved)
    }

    @Test
    fun loopbackHosts_rewrittenToConfiguredHost_portPathPreserved() {
        // 127.0.0.1 → host, port + path preserved (NOT the Moonraker 7125 port — the cam's own 8080).
        assertEquals(
            "http://192.168.1.121:8080/?action=stream",
            resolveWebcamUrl("http://127.0.0.1:8080/?action=stream", cfg),
        )
        // localhost → host (no explicit port → default http port omitted, path preserved).
        assertEquals(
            "http://192.168.1.121:8080/?action=snapshot",
            resolveWebcamUrl("http://localhost:8080/?action=snapshot", cfg),
        )
        // 0.0.0.0 and ::1 are also loopback per D-09.
        assertEquals(
            "http://192.168.1.121:8080/x",
            resolveWebcamUrl("http://0.0.0.0:8080/x", cfg),
        )
        assertEquals(
            "http://192.168.1.121:8080/x",
            resolveWebcamUrl("http://[::1]:8080/x", cfg),
        )
    }

    @Test
    fun loopbackRewrite_preservesTokenQuery() {
        // A loopback snapshot WITH a token — the rewrite must keep ?token= verbatim (E3 needs it).
        val resolved = resolveWebcamUrl(
            "http://127.0.0.1/cameras/snapshot/1.jpg?token=ABC123secret",
            cfg,
        )
        assertEquals(
            "http://192.168.1.121/cameras/snapshot/1.jpg?token=ABC123secret",
            resolved,
        )
    }

    @Test
    fun alreadyCorrectAbsoluteUrl_passesThroughUnchanged_tokenPreserved() {
        // The verbatim E3 tokened snapshot URL on the real host — unchanged, token intact.
        val e3Snapshot =
            "http://192.168.1.121/cameras/snapshot/1.jpg?token=udaFhoavcj6K04ZBhQdtqbyLXMIE69pC4TmhvORIMpk"
        val resolved = resolveWebcamUrl(e3Snapshot, cfg)
        assertEquals(e3Snapshot, resolved)
        assertTrue(resolved!!.contains("token=udaFhoavcj6K04ZBhQdtqbyLXMIE69pC4TmhvORIMpk"))
    }

    @Test
    fun blankOrUnparseableInput_returnsNull() {
        assertNull(resolveWebcamUrl(null, cfg))
        assertNull(resolveWebcamUrl("", cfg))
        assertNull(resolveWebcamUrl("   ", cfg))
    }

    @Test
    fun redact_masksTokenValue_leavesRestIntact() {
        val tokened =
            "http://192.168.1.121/cameras/snapshot/1.jpg?token=udaFhoavcj6K04ZBhQdtqbyLXMIE69pC4TmhvORIMpk"
        val redacted = redactWebcamUrl(tokened)
        assertEquals(
            "http://192.168.1.121/cameras/snapshot/1.jpg?token=<redacted>",
            redacted,
        )
        // The secret value is gone; the rest of the URL survives.
        assertFalse(redacted.contains("udaFhoavcj6K04ZBhQdtqbyLXMIE69pC4TmhvORIMpk"))

        // A token mid-query (&token=) is masked too, leaving sibling params intact.
        assertEquals(
            "http://h/s?a=1&token=<redacted>&b=2",
            redactWebcamUrl("http://h/s?a=1&token=sneaky&b=2"),
        )
        // No token → unchanged.
        assertEquals("http://h/s?a=1", redactWebcamUrl("http://h/s?a=1"))
    }

    @Test
    fun resolveThenRedact_endToEnd() {
        // The realistic surface path: resolve a loopback tokened snapshot, then redact for logging.
        val resolved = resolveWebcamUrl(
            "http://localhost/cameras/snapshot/1.jpg?token=SECRET",
            cfg,
        )!!
        assertEquals("http://192.168.1.121/cameras/snapshot/1.jpg?token=SECRET", resolved)
        assertEquals(
            "http://192.168.1.121/cameras/snapshot/1.jpg?token=<redacted>",
            redactWebcamUrl(resolved),
        )
    }
}
