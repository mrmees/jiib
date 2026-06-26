package works.mees.jiib.systeminfo

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.ui.systeminfo.buildDeviceList

/**
 * Unit tests for [buildDeviceList] — the pure device-list assembly helper used by
 * [works.mees.jiib.ui.systeminfo.SystemInformationContent] to build the Field device list.
 */
class DeviceListTest {

    @Test
    fun buildDeviceList_hostFirstThenMcus() {
        val host = HostDevice("e5", null, null, null, null, null)
        val list = buildDeviceList(host, listOf(McuDevice("mcu", "Mainboard")))
        assertEquals(listOf("host", "mcu"), list.map { it.key })
    }

    @Test
    fun buildDeviceList_nullMcus_hostOnly() {
        assertEquals(1, buildDeviceList(HostDevice("e5", null, null, null, null, null), null).size)
    }
}
