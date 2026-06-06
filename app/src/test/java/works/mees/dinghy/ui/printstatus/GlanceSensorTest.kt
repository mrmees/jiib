package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure Standby-glance sensor-selection contract (16-04). Turned GREEN from the 16-01 RED scaffold.
 *
 *   selectGlanceSensor(sensors: Map<String, Double>): GlanceSensor?
 *
 *   - prefer `temperature_sensor mcu` when present
 *   - else prefer `temperature_sensor host`
 *   - else the FIRST entry by stable key order
 *   - the SAME map yields the SAME choice across repeated calls (STABILITY — the unit guard against a
 *     Moonraker partial-diff-order-dependent flip, T-16-04-D)
 *   - empty map -> null
 */
class GlanceSensorTest {

    @Test
    fun mcuAndHostPresent_prefersMcu() {
        val sensors = mapOf(
            "temperature_sensor host" to 41.0,
            "temperature_sensor mcu" to 38.0,
        )
        assertEquals("temperature_sensor mcu", selectGlanceSensor(sensors)?.name)
        assertEquals(38.0, selectGlanceSensor(sensors)!!.temperature, 0.0001)
    }

    @Test
    fun hostOnly_returnsHost() {
        val sensors = mapOf("temperature_sensor host" to 41.0)
        assertEquals("temperature_sensor host", selectGlanceSensor(sensors)?.name)
    }

    @Test
    fun neitherMcuNorHost_returnsFirstByStableKeyOrder() {
        val sensors = mapOf(
            "temperature_sensor chamber" to 30.0,
            "temperature_sensor raspberry_pi" to 45.0,
        )
        // First by stable (sorted) key order: "chamber" < "raspberry_pi".
        assertEquals("temperature_sensor chamber", selectGlanceSensor(sensors)?.name)
    }

    @Test
    fun sameMap_yieldsSameChoiceAcrossCalls_stability() {
        val sensors = mapOf(
            "temperature_sensor chamber" to 30.0,
            "temperature_sensor raspberry_pi" to 45.0,
        )
        // Diff-order-stable: identical input always yields the identical choice (T-16-04-D).
        assertEquals(selectGlanceSensor(sensors), selectGlanceSensor(sensors))
        // And independent of insertion order.
        val reordered = mapOf(
            "temperature_sensor raspberry_pi" to 45.0,
            "temperature_sensor chamber" to 30.0,
        )
        assertEquals(selectGlanceSensor(sensors), selectGlanceSensor(reordered))
    }

    @Test
    fun emptyMap_returnsNull() {
        assertNull(selectGlanceSensor(emptyMap()))
    }
}
