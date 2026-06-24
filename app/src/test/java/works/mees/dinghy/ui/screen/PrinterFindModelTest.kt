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
}
