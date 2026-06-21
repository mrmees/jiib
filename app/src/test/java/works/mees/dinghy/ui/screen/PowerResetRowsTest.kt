package works.mees.dinghy.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.systeminfo.HostActionAvailability

/**
 * Pure host tests for [powerResetRows] — the command-row list builder.
 *
 * No Android context, no Compose runtime. Asserts row count, order, enabled gates, dispatch keys,
 * and disabled-reason propagation in isolation.
 */
class PowerResetRowsTest {

    // Convenience: fully-available avail (all three host actions enabled, no reasons).
    private val availAll = HostActionAvailability(
        canReboot = true,
        canShutdown = true,
        canRestartMoonraker = true,
        powerDisabledReason = null,
        moonrakerDisabledReason = null,
    )

    // Convenience: fully-blocked avail (power + moonraker both blocked with reasons).
    private val availNone = HostActionAvailability(
        canReboot = false,
        canShutdown = false,
        canRestartMoonraker = false,
        powerDisabledReason = "No power support",
        moonrakerDisabledReason = "Moonraker not a managed service",
    )

    private fun buildRows(
        avail: HostActionAvailability = availAll,
        inFlightKeys: Set<String> = emptySet(),
    ) = powerResetRows(
        avail = avail,
        inFlightKeys = inFlightKeys,
        labelReboot = "Reboot",
        labelShutdown = "Shutdown",
        labelRestartMoonraker = "Restart Moonraker",
        labelFirmwareRestart = "Firmware Restart",
        labelRestartKlipper = "Restart Klipper",
    )

    // ── Count + order ─────────────────────────────────────────────────────────

    @Test
    fun `returns exactly 5 rows`() {
        assertEquals(5, buildRows().size)
    }

    @Test
    fun `rows are in the specified order`() {
        val keys = buildRows().map { it.key }
        assertEquals(
            listOf(
                "machine_reboot",
                "machine_shutdown",
                "services_restart_moonraker",
                "fw_restart",
                "host_restart",
            ),
            keys,
        )
    }

    // ── Enabled gates — availability ─────────────────────────────────────────

    @Test
    fun `all rows enabled when avail is full and no in-flight`() {
        buildRows(avail = availAll).forEach { row ->
            assertTrue("Row ${row.key} should be enabled", row.enabled)
        }
    }

    @Test
    fun `reboot enabled follows canReboot`() {
        val rows = buildRows(avail = availNone)
        assertFalse(rows.first { it.key == "machine_reboot" }.enabled)
    }

    @Test
    fun `shutdown enabled follows canShutdown`() {
        val rows = buildRows(avail = availNone)
        assertFalse(rows.first { it.key == "machine_shutdown" }.enabled)
    }

    @Test
    fun `restart_moonraker enabled follows canRestartMoonraker`() {
        val rows = buildRows(avail = availNone)
        assertFalse(rows.first { it.key == "services_restart_moonraker" }.enabled)
    }

    @Test
    fun `firmware_restart always enabled regardless of avail`() {
        val rows = buildRows(avail = availNone)
        assertTrue(rows.first { it.key == "fw_restart" }.enabled)
    }

    @Test
    fun `restart_klipper always enabled regardless of avail`() {
        val rows = buildRows(avail = availNone)
        assertTrue(rows.first { it.key == "host_restart" }.enabled)
    }

    // ── Enabled gates — in-flight ─────────────────────────────────────────────

    @Test
    fun `reboot disabled while machine_reboot in-flight`() {
        val rows = buildRows(inFlightKeys = setOf("machine_reboot"))
        assertFalse(rows.first { it.key == "machine_reboot" }.enabled)
    }

    @Test
    fun `shutdown disabled while machine_shutdown in-flight`() {
        val rows = buildRows(inFlightKeys = setOf("machine_shutdown"))
        assertFalse(rows.first { it.key == "machine_shutdown" }.enabled)
    }

    @Test
    fun `restart_moonraker disabled while services_restart_moonraker in-flight`() {
        val rows = buildRows(inFlightKeys = setOf("services_restart_moonraker"))
        assertFalse(rows.first { it.key == "services_restart_moonraker" }.enabled)
    }

    @Test
    fun `firmware_restart disabled while fw_restart in-flight`() {
        val rows = buildRows(inFlightKeys = setOf("fw_restart"))
        assertFalse(rows.first { it.key == "fw_restart" }.enabled)
    }

    @Test
    fun `restart_klipper disabled while host_restart in-flight`() {
        val rows = buildRows(inFlightKeys = setOf("host_restart"))
        assertFalse(rows.first { it.key == "host_restart" }.enabled)
    }

    @Test
    fun `unrelated in-flight key does not disable any row`() {
        val rows = buildRows(avail = availAll, inFlightKeys = setOf("some_other_key"))
        rows.forEach { row ->
            assertTrue("Row ${row.key} should not be disabled by unrelated key", row.enabled)
        }
    }

    // ── Disabled-reason propagation ───────────────────────────────────────────

    @Test
    fun `reboot row carries powerDisabledReason when blocked`() {
        val rows = buildRows(avail = availNone)
        val row = rows.first { it.key == "machine_reboot" }
        assertEquals("No power support", row.disabledReason)
    }

    @Test
    fun `shutdown row carries powerDisabledReason when blocked`() {
        val rows = buildRows(avail = availNone)
        val row = rows.first { it.key == "machine_shutdown" }
        assertEquals("No power support", row.disabledReason)
    }

    @Test
    fun `restart_moonraker row carries moonrakerDisabledReason when blocked`() {
        val rows = buildRows(avail = availNone)
        val row = rows.first { it.key == "services_restart_moonraker" }
        assertEquals("Moonraker not a managed service", row.disabledReason)
    }

    @Test
    fun `firmware_restart row has null disabledReason`() {
        val rows = buildRows(avail = availNone)
        assertNull(rows.first { it.key == "fw_restart" }.disabledReason)
    }

    @Test
    fun `restart_klipper row has null disabledReason`() {
        val rows = buildRows(avail = availNone)
        assertNull(rows.first { it.key == "host_restart" }.disabledReason)
    }

    @Test
    fun `enabled rows have null disabledReason`() {
        buildRows(avail = availAll).forEach { row ->
            assertNull("Row ${row.key} should have null disabledReason when enabled", row.disabledReason)
        }
    }

    // ── Intent classification ─────────────────────────────────────────────────

    @Test
    fun `reboot and shutdown are Danger intent`() {
        val rows = buildRows()
        assertEquals(PowerRowIntent.Danger, rows.first { it.key == "machine_reboot" }.intent)
        assertEquals(PowerRowIntent.Danger, rows.first { it.key == "machine_shutdown" }.intent)
    }

    @Test
    fun `restart rows are Warn intent`() {
        val rows = buildRows()
        assertEquals(PowerRowIntent.Warn, rows.first { it.key == "services_restart_moonraker" }.intent)
        assertEquals(PowerRowIntent.Warn, rows.first { it.key == "fw_restart" }.intent)
        assertEquals(PowerRowIntent.Warn, rows.first { it.key == "host_restart" }.intent)
    }

    // ── Destructive flag ──────────────────────────────────────────────────────

    @Test
    fun `reboot and shutdown are destructive`() {
        val rows = buildRows()
        assertTrue(rows.first { it.key == "machine_reboot" }.isDestructive)
        assertTrue(rows.first { it.key == "machine_shutdown" }.isDestructive)
    }

    @Test
    fun `restart rows are not destructive`() {
        val rows = buildRows()
        assertFalse(rows.first { it.key == "services_restart_moonraker" }.isDestructive)
        assertFalse(rows.first { it.key == "fw_restart" }.isDestructive)
        assertFalse(rows.first { it.key == "host_restart" }.isDestructive)
    }

    // ── Confirm copy present ──────────────────────────────────────────────────

    @Test
    fun `all rows carry non-zero confirmTitleRes and confirmMsgRes`() {
        buildRows().forEach { row ->
            assertNotNull("Row ${row.key} has null confirmTitleRes", row.confirmTitleRes)
            assertNotNull("Row ${row.key} has null confirmMsgRes", row.confirmMsgRes)
            assertTrue("Row ${row.key} has zero confirmTitleRes", row.confirmTitleRes != 0)
            assertTrue("Row ${row.key} has zero confirmMsgRes", row.confirmMsgRes != 0)
        }
    }
}
