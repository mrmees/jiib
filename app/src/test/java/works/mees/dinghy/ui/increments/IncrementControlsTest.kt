package works.mees.dinghy.ui.increments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.ui.finetune.ALL_FINE_TUNE_PARAMS

class IncrementControlsTest {

    @Test fun `has 16 controls — 13 fine-tune plus 3 unlimited`() {
        assertEquals(16, IncrementControls.ALL.size)
        assertEquals(13, IncrementControls.ALL.count { it.group == "Fine-Tune" })
    }

    @Test fun `fine-tune entries mirror the param descriptors exactly`() {
        ALL_FINE_TUNE_PARAMS.forEach { p ->
            val spec = IncrementControls.ALL.firstOrNull { it.key == p.tuner.name }
            assertNotNull("missing spec for ${p.tuner}", spec)
            assertEquals(p.name, spec!!.controlTitle)
            assertEquals(p.steps.toList(), spec.defaultValues)
            assertEquals(3, spec.maxCount)
            assertEquals(p.icon, spec.icon)
        }
    }

    @Test fun `unlimited controls have null maxCount`() {
        listOf("move_microstep", "babystep", "probe_testz").forEach { key ->
            assertNull(IncrementControls.ALL.first { it.key == key }.maxCount)
        }
    }

    @Test fun `babystep default mirrors the command constant`() {
        assertEquals(
            PrinterCommands.BABYSTEP_STEPS.toList(),
            IncrementControls.ALL.first { it.key == "babystep" }.defaultValues,
        )
    }

    @Test fun `keys are unique`() {
        val keys = IncrementControls.ALL.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun `defaultStringMap is canonical strings for every key`() {
        val map = IncrementControls.defaultStringMap()
        assertEquals(16, map.size)
        assertEquals("0.001,0.005,0.01", map["PRESSURE_ADVANCE"])
        assertTrue(map.values.none { it.isBlank() })
    }
}
