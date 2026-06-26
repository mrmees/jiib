package works.mees.jiib.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionUrlsTest {
    private fun clean(s: String) = normalizeHost(s) as HostResult.Clean

    @Test fun trims() = assertEquals("h", clean("  h  ").host)
    @Test fun hostPortPopulatesPortOverride() {
        val r = clean("192.168.1.50:7130")
        assertEquals("192.168.1.50", r.host)
        assertEquals(7130, r.portOverride)
    }

    @Test fun bracketedIpv6WithPort() {
        val r = clean("[::1]:7125")
        assertEquals("[::1]", r.host)
        assertEquals(7125, r.portOverride)
    }

    @Test fun bracketedIpv6WithoutPort() {
        val r = clean("[fe80::1]")
        assertEquals("[fe80::1]", r.host)
        assertEquals(null, r.portOverride)
    }

    @Test fun rejectsUnclosedBracket() =
        assertTrue(normalizeHost("[::1:7125") is HostResult.Rejected)

    @Test fun rejectsBareIpv6() =
        assertTrue(normalizeHost("fe80::1:2:3:4") is HostResult.Rejected)

    @Test fun rejectsPathInHostField() =
        assertTrue(normalizeHost("host.local/foo") is HostResult.Rejected)

    @Test fun rejectsSchemeOnlyHostField() =
        assertTrue(normalizeHost("http://") is HostResult.Rejected)

    @Test fun rejectsFullUrlInHostField() =
        assertTrue(normalizeHost("https://host.local:7125/printer") is HostResult.Rejected)

    @Test fun rejectsBlank() = assertTrue(normalizeHost("   ") is HostResult.Rejected)

    @Test fun plainUrls() {
        val u = buildConnectionUrls("192.168.1.50", 7125, advancedUrl = null, useSecure = false)
        assertEquals("http://192.168.1.50:7125", u.httpBase)
        assertEquals("ws://192.168.1.50:7125/websocket", u.wsUrl)
    }

    @Test fun legacySecure() {
        val u = buildConnectionUrls("h", 7130, advancedUrl = null, useSecure = true)
        assertEquals("https://h:7130", u.httpBase)
        assertEquals("wss://h:7130/websocket", u.wsUrl)
    }

    @Test fun advancedUrlRoutePrefix() {
        val u = buildConnectionUrls("ignored", 7125, advancedUrl = "https://my.host/printer1", useSecure = false)
        assertEquals("https://my.host/printer1", u.httpBase)
        assertEquals("wss://my.host/printer1/websocket", u.wsUrl)
    }

    @Test fun advancedUrlCollapsesDefaultPorts() {
        val http = buildConnectionUrls("x", 7125, advancedUrl = "http://h:80", useSecure = false)
        val https = buildConnectionUrls("x", 7125, advancedUrl = "https://h:443", useSecure = false)
        assertEquals("http://h", http.httpBase)
        assertEquals("ws://h/websocket", http.wsUrl)
        assertEquals("https://h", https.httpBase)
        assertEquals("wss://h/websocket", https.wsUrl)
    }

    @Test fun advancedUrlUppercaseScheme() {
        val u = buildConnectionUrls("x", 7125, advancedUrl = "HTTPS://H.EXAMPLE/Printer", useSecure = false)
        assertEquals("https://h.example/Printer", u.httpBase)
        assertEquals("wss://h.example/Printer/websocket", u.wsUrl)
    }

    @Test fun advancedUrlWsRoundTrip() {
        val u = buildConnectionUrls("x", 7125, advancedUrl = "ws://h:7125/proxy", useSecure = false)
        assertEquals("http://h:7125/proxy", u.httpBase)
        assertEquals("ws://h:7125/proxy/websocket", u.wsUrl)
    }

    @Test fun advancedUrlWssRoundTrip() {
        val u = buildConnectionUrls("x", 7125, advancedUrl = "wss://h:7125/proxy", useSecure = false)
        assertEquals("https://h:7125/proxy", u.httpBase)
        assertEquals("wss://h:7125/proxy/websocket", u.wsUrl)
    }

    @Test fun advancedUrlDeDupesWebsocket() {
        val u = buildConnectionUrls("x", 7125, advancedUrl = "ws://h:7125/websocket", useSecure = false)
        assertEquals("http://h:7125", u.httpBase)
        assertEquals("ws://h:7125/websocket", u.wsUrl)
    }

    @Test fun advancedUrlRejectsQueryFragmentSchemeOnlyAndInvalid() {
        assertThrows(IllegalArgumentException::class.java) {
            buildConnectionUrls("x", 7125, advancedUrl = "https://h?x=1", useSecure = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildConnectionUrls("x", 7125, advancedUrl = "https://h#frag", useSecure = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildConnectionUrls("x", 7125, advancedUrl = "https://", useSecure = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildConnectionUrls("x", 7125, advancedUrl = "not a url", useSecure = false)
        }
    }
}
