package works.mees.dinghy.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.net.ProbeFailure
import works.mees.dinghy.net.ProbeResult
import works.mees.dinghy.net.TransportResult

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
        val p = works.mees.dinghy.config.Profile.fromPersisted(
            works.mees.dinghy.config.PersistedProfile(id = "x", host = "host.lan", port = 7125, apiKey = "k"),
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
}
