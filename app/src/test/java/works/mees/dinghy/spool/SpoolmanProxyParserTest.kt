package works.mees.dinghy.spool

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import works.mees.dinghy.net.GoldenFixtures
import works.mees.dinghy.net.MoonrakerJson

/**
 * RED scaffold (SPOOL-02/08) — proxy-v2 envelope parser.
 *
 * Wave-0 compile-safety: pure `fail()` bodies, no reference to the unbuilt `SpoolmanParsers` symbols
 * (built in Wave 1). Loads the live golden so the GoldenFixtures link is exercised at compile + run.
 *
 * Target assertions (Wave 1 turns these green):
 *  - Parsing `spoolman-live-ender5-proxy-pla.json`'s `result` envelope yields `X-Total-Count == "7"`
 *    (read from `response_headers`) and `error == null` (error:null means SUCCESS, not "no data").
 *  - The `response` array decodes to 5 spool rows; one malformed row drops, the rest survive.
 */
class SpoolmanProxyParserTest {

    @Test
    fun parsesXTotalCountAndErrorNullFromPlaGolden() {
        // The golden's top-level `result` IS the proxy-v2 envelope ({response, response_headers, error}).
        val envelope = MoonrakerJson.parseToJsonElement(GoldenFixtures.raw("spoolman-live-ender5-proxy-pla.json"))
            .jsonObject["result"]
        assertNotNull("proxy-pla golden must carry a result envelope", envelope)
        fail("RED: proxy-v2 envelope parser (X-Total-Count==\"7\", error==null) not yet implemented")
    }

    @Test
    fun decodesSpoolRowsTolerantlyDroppingBadRows() {
        fail("RED: tolerant spool-row decode (mapNotNull, one bad row drops) not yet implemented")
    }
}
