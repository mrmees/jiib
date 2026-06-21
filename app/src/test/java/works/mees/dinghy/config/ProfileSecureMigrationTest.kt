package works.mees.dinghy.config

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileSecureMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun fromPersistedMigratesUseSecureToAdvancedUrl() {
        val p = Profile.fromPersisted(
            PersistedProfile(id = "legacy", host = "secure.local", port = 7130, useSecure = true),
        )

        assertEquals("https://secure.local:7130", p.advancedUrl)
        assertFalse("runtime Profile must not resurrect the retired toggle", p.useSecure)
        assertEquals("https://secure.local:7130", p.toConnectionConfig().httpBase)
        assertEquals("wss://secure.local:7130/websocket", p.toConnectionConfig().wsUrl)
    }

    @Test fun sanitizeMigratesLegacySecureProfiles() {
        val raw = json.encodeToString(
            ListSerializer(PersistedProfile.serializer()),
            listOf(PersistedProfile(id = "legacy", host = "secure.local", port = 7130, useSecure = true)),
        )

        val p = ProfileStore.sanitize(raw).single()

        assertEquals("https://secure.local:7130", p.advancedUrl)
        assertFalse(p.useSecure)
        assertEquals("wss://secure.local:7130/websocket", p.toConnectionConfig().wsUrl)
    }

    @Test fun fromPersistedDoesNotOverwriteExistingAdvancedUrl() {
        val p = Profile.fromPersisted(
            PersistedProfile(id = "x", host = "h", port = 7125, useSecure = true,
                advancedUrl = "https://proxy.example.com/printer"),
        )
        assertEquals("https://proxy.example.com/printer", p.advancedUrl)
    }

    @Test fun toPersistedWritesAdvancedUrlNotUseSecure() {
        val opened = Profile.fromPersisted(
            PersistedProfile(id = "legacy", host = "secure.local", port = 7130, useSecure = true),
        )

        val saved = opened.copy(name = "Edited").toPersisted()

        assertEquals("https://secure.local:7130", saved.advancedUrl)
        assertFalse("save must clear the retired useSecure bit", saved.useSecure)
    }
}
