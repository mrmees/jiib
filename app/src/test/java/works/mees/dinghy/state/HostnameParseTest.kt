package works.mees.dinghy.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostnameParseTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
    @Test fun extracts_hostname() {
        assertEquals("ender5plus", parsePrinterInfoHostname(obj("""{"hostname":"ender5plus","state":"ready"}""")))
    }
    @Test fun trims_and_blanks_to_null() {
        assertEquals("ender3", parsePrinterInfoHostname(obj("""{"hostname":"  ender3 "}""")))
        assertNull(parsePrinterInfoHostname(obj("""{"hostname":"   "}""")))
    }
    @Test fun missing_or_malformed_is_null() {
        assertNull(parsePrinterInfoHostname(obj("""{"state":"ready"}""")))
        assertNull(parsePrinterInfoHostname(null))
    }
    @Test fun non_string_hostname_is_null() {
        // a number/bool must NOT become a printer name (Codex S4)
        assertNull(parsePrinterInfoHostname(obj("""{"hostname":12345}""")))
        assertNull(parsePrinterInfoHostname(obj("""{"hostname":true}""")))
    }
}
