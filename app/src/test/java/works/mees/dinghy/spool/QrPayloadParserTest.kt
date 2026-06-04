package works.mees.dinghy.spool

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED scaffold (SPOOL-05) — QR payload parser, the four accept/reject classes (D-12, Security V5).
 *
 * Wave-0 compile-safety: pure `fail()` bodies, no reference to the unbuilt `parseSpoolId` /
 * `SpoolQrResult` symbols (built in Wave 2 under `ui/spool/scan/QrPayloadParser.kt`).
 *
 * Target contract (11-RESEARCH §QR parser; Wave 2 turns these green) — parse is case-INSENSITIVE and
 * NEVER trusts the URL host (id-only):
 *  ACCEPT → `web+spoolman:s-<digits>`            (Spool(id))
 *  ACCEPT → `WEB+SPOOLMAN:S-<digits>`            (uppercase — same Spool(id))
 *  ACCEPT → `http(s)://<any-host>/spool/show/<digits>` (Spool(id) — host ignored)
 *  REJECT → `web+spoolman:f-<digits>`            (filament scheme → UnsupportedSpoolmanCode)
 *  REJECT → bare UPC/EAN digits, non-numeric ids, other schemes, hostile host → NotASpoolCode
 */
class QrPayloadParserTest {

    @Test
    fun acceptsLowercaseSpoolmanSpoolUri() {
        fail("RED: accept web+spoolman:s-<id> not yet implemented")
    }

    @Test
    fun acceptsUppercaseSpoolmanSpoolUri() {
        fail("RED: accept WEB+SPOOLMAN:S-<id> (case-insensitive) not yet implemented")
    }

    @Test
    fun acceptsSpoolShowUrlIgnoringHost() {
        fail("RED: accept http(s)://*/spool/show/<id> (host untrusted) not yet implemented")
    }

    @Test
    fun rejectsFilamentCodeAsUnsupported() {
        fail("RED: reject web+spoolman:f-<id> as UnsupportedSpoolmanCode not yet implemented")
    }

    @Test
    fun rejectsUpcAndNonNumericAndHostileInputs() {
        fail("RED: reject UPC/EAN, non-numeric, other-scheme, hostile-host as NotASpoolCode not yet implemented")
    }
}
