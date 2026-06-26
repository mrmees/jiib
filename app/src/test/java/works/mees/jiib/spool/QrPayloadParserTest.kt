package works.mees.jiib.spool

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.ui.spool.scan.SpoolQrResult
import works.mees.jiib.ui.spool.scan.parseSpoolId

/**
 * SPOOL-05 — QR payload parser decision table (D-12, the Security V5 untrusted-input boundary).
 *
 * Parse is case-INSENSITIVE and NEVER trusts the URL host (id-only):
 *  ACCEPT → `web+spoolman:s-<digits>`                  (Spool(id))
 *  ACCEPT → `WEB+SPOOLMAN:S-<digits>`                  (uppercase — same Spool(id))
 *  ACCEPT → `http(s)://<any-host>/spool/show/<digits>` (Spool(id) — host ignored)
 *  REJECT → `web+spoolman:f-<digits>`                  (filament scheme → UnsupportedSpoolmanCode)
 *  REJECT → UPC/EAN, non-numeric, other schemes, hostile host, overlong id → NotASpoolCode
 */
class QrPayloadParserTest {

    @Test
    fun acceptsLowercaseSpoolmanSpoolUri() {
        assertEquals(SpoolQrResult.Spool(123), parseSpoolId("web+spoolman:s-123"))
    }

    @Test
    fun acceptsUppercaseSpoolmanSpoolUri() {
        assertEquals(SpoolQrResult.Spool(123), parseSpoolId("WEB+SPOOLMAN:S-123"))
    }

    @Test
    fun acceptsSpoolShowUrlIgnoringHost() {
        // Friendly host and a deliberately HOSTILE host both yield the id ONLY — the host is never used.
        assertEquals(SpoolQrResult.Spool(7), parseSpoolId("https://spoolman.local/spool/show/7"))
        assertEquals(SpoolQrResult.Spool(9), parseSpoolId("http://evil.example/spool/show/9"))
    }

    @Test
    fun spoolResultNeverCarriesTheHost() {
        // The sealed result exposes ONLY an Int id; there is no host/url field to leak (D-12).
        val result = parseSpoolId("http://evil.example/spool/show/42")
        assertEquals(SpoolQrResult.Spool(42), result)
    }

    @Test
    fun rejectsFilamentCodeAsUnsupported() {
        assertEquals(SpoolQrResult.UnsupportedSpoolmanCode, parseSpoolId("web+spoolman:f-3"))
        // Uppercase filament scheme is still the recognized-but-unsupported code.
        assertEquals(SpoolQrResult.UnsupportedSpoolmanCode, parseSpoolId("WEB+SPOOLMAN:F-3"))
    }

    @Test
    fun rejectsUpcAndNonNumericAndHostileInputs() {
        assertEquals(SpoolQrResult.NotASpoolCode, parseSpoolId("012345678905")) // UPC
        assertEquals(SpoolQrResult.NotASpoolCode, parseSpoolId("abc"))           // non-numeric
        assertEquals(SpoolQrResult.NotASpoolCode, parseSpoolId("web+spoolman:s-")) // no digits
        assertEquals(SpoolQrResult.NotASpoolCode, parseSpoolId("https://x/other/5")) // not /spool/show/
        assertEquals(SpoolQrResult.NotASpoolCode, parseSpoolId("ftp://host/spool/show/5")) // foreign scheme
        assertEquals(SpoolQrResult.NotASpoolCode, parseSpoolId("web+spoolman:s-12a")) // non-numeric id
    }

    @Test
    fun rejectsOverlongIdWithoutOverflowCrash() {
        // A digit run that overflows Int must yield NotASpoolCode via toIntOrNull, never throw.
        assertEquals(SpoolQrResult.NotASpoolCode, parseSpoolId("web+spoolman:s-99999999999999999999"))
        assertEquals(SpoolQrResult.NotASpoolCode, parseSpoolId("https://h/spool/show/99999999999999999999"))
    }
}
