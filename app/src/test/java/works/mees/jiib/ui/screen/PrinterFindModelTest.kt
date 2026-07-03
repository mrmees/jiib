package works.mees.jiib.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.net.ProbeFailure
import works.mees.jiib.net.ProbeResult
import works.mees.jiib.net.TransportResult

class PrinterFindModelTest {
    private fun probe(httpOk: Boolean, wsOk: Boolean) = ProbeResult(
        httpUrl = "http://h:7125",
        wsUrl = "ws://h:7125/websocket",
        http = TransportResult(ok = httpOk, failure = if (httpOk) null else ProbeFailure.Refused),
        ws = TransportResult(ok = wsOk, failure = if (wsOk) null else ProbeFailure.Unauthorized),
    )

    @Test
    fun bothTransportsOk_addsAndConnects() {
        assertEquals(FindPickEffect.AddAndConnect, findPickDecision(probe(httpOk = true, wsOk = true)))
    }

    @Test
    fun anyTransportFails_opensEditorSeeded() {
        assertEquals(FindPickEffect.OpenEditorSeeded, findPickDecision(probe(httpOk = true, wsOk = false)))
        assertEquals(FindPickEffect.OpenEditorSeeded, findPickDecision(probe(httpOk = false, wsOk = true)))
        assertEquals(FindPickEffect.OpenEditorSeeded, findPickDecision(probe(httpOk = false, wsOk = false)))
    }

    @Test
    fun editorInitialFields_newWithSeed_usesSeedHostPort() {
        val f = editorInitialFields(profile = null, seed = ConnectionSeed(host = "192.168.1.50", port = 7130))
        assertEquals("192.168.1.50", f.host)
        assertEquals("7130", f.port)
        assertEquals("", f.name)
        assertEquals(false, f.keyAlreadySaved)
    }

    @Test
    fun editorInitialFields_existingProfile_ignoresSeed() {
        val p = works.mees.jiib.config.Profile.fromPersisted(
            works.mees.jiib.config.PersistedProfile(id = "x", host = "host.lan", port = 7125, apiKey = "k"),
        )
        val f = editorInitialFields(profile = p, seed = ConnectionSeed(host = "should.ignore", port = 9999))
        assertEquals("host.lan", f.host)
        assertEquals("7125", f.port)
        assertEquals(true, f.keyAlreadySaved)
    }

    @Test
    fun editorInitialFields_newNoSeed_defaults() {
        val f = editorInitialFields(profile = null, seed = null)
        assertEquals("", f.host)
        assertEquals("7125", f.port)
    }

    @Test
    fun discoveredHostname_stripsMoonrakerPrefix() {
        assertEquals("ender5plus", discoveredHostname("moonraker @ ender5plus"))
        assertEquals("voron", discoveredHostname("Moonraker @ voron"))     // case-insensitive
        assertEquals("ender3", discoveredHostname("moonraker @ender3"))    // no space after @
        assertEquals("host", discoveredHostname("  moonraker @ host  "))   // surrounding whitespace
    }

    @Test
    fun discoveredHostname_keepsNamesWithoutThePrefix() {
        assertEquals("ender5plus", discoveredHostname("ender5plus"))
    }

    @Test
    fun discoveredHostname_blankWhenNothingUsefulRemains() {
        assertEquals("", discoveredHostname("moonraker @ "))   // prefix only
        assertEquals("", discoveredHostname("moonraker"))      // bare moonraker carries no host info
        assertEquals("", discoveredHostname("   "))            // empty advert
    }

    @Test
    fun discoveredDefaultName_isHostnameColonPort() {
        assertEquals("ender5plus:7125", discoveredDefaultName("moonraker @ ender5plus", "192.168.1.120", 7125))
        assertEquals("voron:7125", discoveredDefaultName("voron", "192.168.1.50", 7125))
    }

    @Test
    fun discoveredDefaultName_fallsBackToIpWhenNoHostname() {
        assertEquals("192.168.1.120:7125", discoveredDefaultName("moonraker", "192.168.1.120", 7125))
        assertEquals("192.168.1.120:7125", discoveredDefaultName("   ", "192.168.1.120", 7125))
    }
}
