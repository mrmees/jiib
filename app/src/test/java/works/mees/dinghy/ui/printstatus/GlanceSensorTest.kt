package works.mees.dinghy.ui.printstatus

import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave-0 RED scaffold (16-01) — turned GREEN by 16-04 (`selectGlanceSensor` / `GlanceSensor`).
 *
 * Encodes the PURE Standby-glance sensor-selection CONTRACT 16-04 will build:
 *
 *   selectGlanceSensor(sensors: Map<String, Double>): GlanceSensor?
 *
 *   - prefer `temperature_sensor mcu` when present
 *   - else prefer `temperature_sensor host`
 *   - else the FIRST entry by stable key order
 *   - the SAME map yields the SAME choice across repeated calls (STABILITY — this is the unit guard
 *     against a Moonraker partial-diff-order-dependent flip, T-16-01-D)
 *   - empty map -> null
 *
 * RED discipline ([[dinghy-wave0-red-scaffold-compile]]): these bodies do NOT reference the not-yet-built
 * `selectGlanceSensor` / `GlanceSensor` (they land in 16-04). The input maps + expected keys are spelled
 * out so the contract is unambiguous; each case `fail(...)`s until 16-04 turns it GREEN. The file
 * compiles day-one (no unbuilt symbol referenced).
 */
class GlanceSensorTest {

    @Test
    fun mcuAndHostPresent_prefersMcu() {
        val sensors = mapOf(
            "temperature_sensor host" to 41.0,
            "temperature_sensor mcu" to 38.0,
        )
        // EXPECT (16-04): selectGlanceSensor(sensors).key == "temperature_sensor mcu"
        require(sensors.size == 2)
        fail("not yet implemented — Wave 1 16-04 selectGlanceSensor (prefers mcu)")
    }

    @Test
    fun hostOnly_returnsHost() {
        val sensors = mapOf("temperature_sensor host" to 41.0)
        // EXPECT (16-04): selectGlanceSensor(sensors).key == "temperature_sensor host"
        require(sensors.size == 1)
        fail("not yet implemented — Wave 1 16-04 selectGlanceSensor (host fallback)")
    }

    @Test
    fun neitherMcuNorHost_returnsFirstByStableKeyOrder() {
        val sensors = mapOf(
            "temperature_sensor chamber" to 30.0,
            "temperature_sensor raspberry_pi" to 45.0,
        )
        // EXPECT (16-04): selectGlanceSensor(sensors).key == "temperature_sensor chamber" (first by
        // stable key order).
        require(sensors.size == 2)
        fail("not yet implemented — Wave 1 16-04 selectGlanceSensor (first by stable order)")
    }

    @Test
    fun sameMap_yieldsSameChoiceAcrossCalls_stability() {
        val sensors = mapOf(
            "temperature_sensor chamber" to 30.0,
            "temperature_sensor raspberry_pi" to 45.0,
        )
        // EXPECT (16-04): selectGlanceSensor(sensors) == selectGlanceSensor(sensors) — diff-order-stable,
        // the unit guard against a partial-diff flip (T-16-01-D).
        require(sensors.isNotEmpty())
        fail("not yet implemented — Wave 1 16-04 selectGlanceSensor (stable across calls)")
    }

    @Test
    fun emptyMap_returnsNull() {
        val sensors = emptyMap<String, Double>()
        // EXPECT (16-04): selectGlanceSensor(sensors) == null
        require(sensors.isEmpty())
        fail("not yet implemented — Wave 1 16-04 selectGlanceSensor (empty -> null)")
    }
}
