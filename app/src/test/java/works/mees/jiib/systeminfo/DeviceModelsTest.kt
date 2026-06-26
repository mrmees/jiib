package works.mees.jiib.systeminfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceModelsTest {

    @Test
    fun hostDevice_keyIsStableHostKey() {
        val h = HostDevice("ender5plus", null, null, null, null, null)
        assertEquals(HostDevice.HOST_KEY, h.key)
        assertEquals("host", HostDevice.HOST_KEY)
    }

    @Test
    fun mcuDevice_carriesRawObjectNameAsKey_andDetailDefaultsNull() {
        val m = McuDevice(key = "mcu EBBCan", displayName = "EBBCan")
        assertEquals("mcu EBBCan", m.key)
        assertEquals("EBBCan", m.displayName)
        assertNull(m.firmwareVersion)
        assertNull(m.bytesRetransmit)
    }

    @Test
    fun systemInfo_defaultsHaveNoProviderAndEmptyServices() {
        val s = SystemInfo()
        assertNull(s.provider)
        assertEquals(emptyList<String>(), s.availableServices)
    }
}
