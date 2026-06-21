package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McuParseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun enumerate_filtersMcuObjects_mainboardFirst() {
        val objs = setOf("toolhead", "mcu EBBCan", "extruder", "mcu", "mcu host", "gcode_macro X")
        assertEquals(listOf("mcu", "mcu EBBCan", "mcu host"), mcuObjectNames(objs))
    }

    @Test fun displayName_mapping() {
        assertEquals("Mainboard", mcuDisplayName("mcu"))
        assertEquals("Host MCU", mcuDisplayName("mcu host"))
        assertEquals("EBBCan", mcuDisplayName("mcu EBBCan"))
    }

    @Test fun parseDevices_readsVersionConstantsStatsAndInterface() {
        val result = json.parseToJsonElement(
            """
            {"eventtime":1.0,"status":{
              "mcu":{"mcu_version":"v0.12.0-1","mcu_constants":{"MCU":"stm32f407","CLOCK_FREQ":168000000},
                     "last_stats":{"mcu_awake":0.05,"mcu_task_avg":0.0001,"mcu_task_stddev":0.00002,
                                   "bytes_write":1000,"bytes_read":2000,"bytes_retransmit":3}},
              "mcu EBBCan":{"mcu_version":"v0.12.0-can","mcu_constants":{"MCU":"stm32g0b1"},
                     "last_stats":{"mcu_awake":0.01,"bytes_retransmit":0}},
              "configfile":{"settings":{
                 "mcu":{"serial":"/dev/serial/by-id/usb-Klipper_stm32f407"},
                 "mcu ebbcan":{"canbus_uuid":"aabbccddeeff"}}}}}
            """.trimIndent(),
        )
        val devices = parseMcuDevices(listOf("mcu", "mcu EBBCan"), result)
        val board = devices.first { it.key == "mcu" }
        assertEquals("v0.12.0-1", board.firmwareVersion)
        assertEquals("stm32f407", board.chip)
        assertEquals(168000000L, board.clockHz)
        assertTrue(board.interfaceDesc!!.contains("usb-Klipper_stm32f407"))
        assertEquals(3L, board.bytesRetransmit)
        val can = devices.first { it.key == "mcu EBBCan" }
        assertEquals("stm32g0b1", can.chip)
        assertNull(can.clockHz)            // per-field degrade
        assertTrue(can.interfaceDesc!!.contains("aabbccddeeff"))  // CAN uuid via lowercased section
    }

    @Test fun parseDevices_sparseResult_allFieldsDegradeNoThrow() {
        val result = json.parseToJsonElement("""{"status":{"mcu":{}}}""")
        val devices = parseMcuDevices(listOf("mcu"), result)
        assertEquals(1, devices.size)
        assertNull(devices[0].firmwareVersion)
        assertNull(devices[0].chip)
        assertEquals("Mainboard", devices[0].displayName)
    }

    @Test fun parseDevices_nullResult_returnsBareRowsPerName() {
        val devices = parseMcuDevices(listOf("mcu"), null)
        assertEquals(1, devices.size)
        assertEquals("mcu", devices[0].key)
    }

    @Test fun refreshStats_mergesIntoExistingStaticDetail() {
        val existing = listOf(McuDevice(key = "mcu", displayName = "Mainboard", chip = "stm32f407"))
        val result = json.parseToJsonElement(
            """{"status":{"mcu":{"last_stats":{"mcu_awake":0.9,"bytes_retransmit":7}}}}""",
        )
        val merged = mergeStats(existing, parseMcuStats(listOf("mcu"), result))
        assertEquals("stm32f407", merged[0].chip)      // static preserved
        assertEquals(0.9f, merged[0].mcuAwake)
        assertEquals(7L, merged[0].bytesRetransmit)
    }

    @Test fun throttle_decodesPiBitsAndOccurredMirror() {
        // bit0 under-voltage now + bit19 (0x80000) soft-temp-limit occurred
        val conditions = decodeThrottleConditions(ThrottledState(bits = 0x1 or 0x80000))
        assertTrue(conditions.any { it.contains("Under-voltage", ignoreCase = true) })
        assertTrue(conditions.any { it.contains("temperature", ignoreCase = true) })
    }

    @Test fun throttle_nullStateIsEmpty() {
        assertEquals(emptyList<String>(), decodeThrottleConditions(null))
    }
}
