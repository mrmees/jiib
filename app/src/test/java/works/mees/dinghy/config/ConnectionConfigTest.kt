package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side proof of the R7 `useSecure` scheme plumbing (26.5-07, roadmap §R7 — owner-blessed
 * option B): [ConnectionConfig.useSecure] switches BOTH centralized URL getters between
 * `ws://`+`http://` (default, today's LAN posture) and `wss://`+`https://` (TLS-fronted Moonraker).
 *
 * Also pins the two safety contracts the new field must not break:
 *  - MIGRATION SAFETY: an old persisted profile blob WITHOUT a `useSecure` key decodes to
 *    `useSecure=false` (kotlinx `ignoreUnknownKeys` + defaulted field — the webcamEnabled precedent),
 *    so every pre-R7 profile keeps connecting over plain ws/http unchanged.
 *  - SPINE-CHURN CONTRACT (RESEARCH Pitfall 1): two [Profile]s differing only in theme still produce
 *    EQUAL [ConnectionConfig]s (no reconnect on a theme edit), while a useSecure flip DOES change the
 *    config (the spine MUST rebind to pick up the new scheme).
 */
class ConnectionConfigTest {

    // ---- Behavior 1: useSecure=false (the default) produces today's ws/http URLs ----

    @Test
    fun useSecureFalse_producesWsAndHttpUrls() {
        val cfg = ConnectionConfig(host = "192.168.1.120", port = 7125, useSecure = false)
        assertEquals("ws://192.168.1.120:7125/websocket", cfg.wsUrl)
        assertEquals("http://192.168.1.120:7125", cfg.httpBase)
    }

    @Test
    fun defaultConstruction_isNotSecure() {
        val cfg = ConnectionConfig(host = "192.168.1.120")
        assertFalse("useSecure must default false (migration safety)", cfg.useSecure)
        assertEquals("ws://192.168.1.120:7125/websocket", cfg.wsUrl)
        assertEquals("http://192.168.1.120:7125", cfg.httpBase)
    }

    // ---- Behavior 2: useSecure=true produces wss/https URLs ----

    @Test
    fun useSecureTrue_producesWssAndHttpsUrls() {
        val cfg = ConnectionConfig(host = "myprinter.local", port = 443, useSecure = true)
        assertEquals("wss://myprinter.local:443/websocket", cfg.wsUrl)
        assertEquals("https://myprinter.local:443", cfg.httpBase)
    }

    // ---- Behavior 3: ConnectionStore.sanitize round-trips useSecure; null defaults false ----

    @Test
    fun sanitize_useSecureTrue_roundTrips() {
        val cfg = ConnectionStore.sanitize("192.168.1.50", 7125, null, useSecure = true)
        assertEquals(true, cfg?.useSecure)
    }

    @Test
    fun sanitize_useSecureNull_defaultsFalse() {
        val cfg = ConnectionStore.sanitize("192.168.1.50", 7125, null, useSecure = null)
        assertEquals(false, cfg?.useSecure)
    }

    // ---- Behavior 4: old-blob migration safety (PersistedProfile without a useSecure key) ----

    @Test
    fun oldProfileBlob_withoutUseSecureKey_decodesToFalse() {
        // A literal pre-R7 blob shape (no useSecure key anywhere) through the PRODUCTION decode
        // path (ProfileStore.sanitize → Profile.fromPersisted).
        val oldBlob = """
            [{"id":"e5plus","name":"Ender 5 Plus","host":"192.168.1.120","port":7125,
              "apiKey":"abc123","seedHex":"#3f78ff","dark":true,"paletteMode":"Colorful",
              "poolShift":0,"maxItems":4,"poolOverrides":{},"fsChoice":"M","webcamEnabled":true}]
        """.trimIndent()
        val profiles = ProfileStore.sanitize(oldBlob)
        assertEquals(1, profiles.size)
        assertFalse("old blob must decode to useSecure=false", profiles[0].useSecure)
        assertFalse(
            "old blob must keep connecting over plain ws://",
            profiles[0].toConnectionConfig().useSecure,
        )
        assertEquals("ws://192.168.1.120:7125/websocket", profiles[0].toConnectionConfig().wsUrl)
    }

    // ---- Behavior 5: the distinctUntilChanged contract survives the new field ----

    @Test
    fun profilesDifferingOnlyInTheme_produceEqualConnectionConfigs() {
        val base = Profile(id = "p1", host = "192.168.1.120", port = 7125, apiKey = "k")
        val themed = base.copy(dark = false, seedHex = "#ff0000", paletteMode = "Simple")
        assertEquals(
            "a theme-only edit must NOT churn the spine (RESEARCH Pitfall 1)",
            base.toConnectionConfig(),
            themed.toConnectionConfig(),
        )
    }

    @Test
    fun useSecureFlip_changesConnectionConfig_soSpineRebinds() {
        val plain = Profile(id = "p1", host = "192.168.1.120", port = 7125, useSecure = false)
        val secure = plain.copy(useSecure = true)
        assertNotEquals(
            "a useSecure flip MUST change the config so distinctUntilChanged rebinds the spine",
            plain.toConnectionConfig(),
            secure.toConnectionConfig(),
        )
    }

    // ---- apiKey redaction preserved (T-04-01-I) on every touched type ----

    @Test
    fun toString_stillRedactsApiKey_withUseSecure() {
        val s = ConnectionConfig("host", 7125, "super-secret-key", useSecure = true).toString()
        assertTrue("toString must redact the key", s.contains("***"))
        assertFalse("toString must not leak the raw key", s.contains("super-secret-key"))
    }

    @Test
    fun persistedProfile_toString_stillRedactsApiKey_withUseSecure() {
        val s = Profile(id = "p", host = "h", apiKey = "super-secret-key", useSecure = true)
            .toPersisted().toString()
        assertTrue("PersistedProfile.toString must redact the key", s.contains("***"))
        assertFalse("PersistedProfile.toString must not leak the raw key", s.contains("super-secret-key"))
    }
}
