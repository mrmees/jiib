package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileAdvancedUrlTest {
    @Test fun defaultsToNull() {
        val p = Profile(id = "a", host = "192.168.1.10")
        assertNull(p.advancedUrl)
    }

    @Test fun roundTripsThroughPersisted() {
        val p = Profile(id = "a", host = "h", advancedUrl = "https://h/printer")
        val back = Profile.fromPersisted(p.toPersisted())
        assertEquals("https://h/printer", back.advancedUrl)
    }

    @Test fun toConnectionConfigCarriesAdvancedUrl() {
        val p = Profile(id = "a", host = "h", advancedUrl = "https://proxy/printer")
        assertEquals("https://proxy/printer", p.toConnectionConfig().advancedUrl)
    }
}
