package works.mees.dinghy.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.ui.finetune.FineTuneVm
import works.mees.dinghy.ui.printstatus.PrintStatusMode
import works.mees.dinghy.ui.printstatus.TerminalKind
import works.mees.dinghy.ui.printstatus.classifyPrintStatus

/**
 * The LIVE preview/fixture-module test (SC-2) — converted from the 18-01 Wave-0 compile scaffold now
 * that [SampleFixtures] exists. Asserts the reusable fixtures cover every PrintStatus state, expose
 * the three FineTune capability variants (present / FW-retraction-absent / busy), and carry a
 * non-empty spool list + temp series — the seed the exemplars (and the Phase-22 backfill) reuse.
 *
 * Still PURE: no Moonraker, no network, no coroutines (SampleFixtures is plain immutable data).
 */
class SampleFixturesTest {

    @Test
    fun printStatusModes_coverAll4StatesAcrossTheTerminalKinds() {
        val modes = SampleFixtures.printStatusModes
        // All six entries are distinct (the 4 states, Terminal expanded to its three kinds).
        assertEquals(6, modes.toSet().size)
        // Every non-terminal state is present.
        assertTrue("Standby present", modes.contains(PrintStatusMode.Standby))
        assertTrue("Printing present", modes.contains(PrintStatusMode.Printing))
        assertTrue("Paused present", modes.contains(PrintStatusMode.Paused))
        // All three terminal kinds present.
        val terminalKinds = modes.filterIsInstance<PrintStatusMode.Terminal>().map { it.kind }.toSet()
        assertEquals("all 3 terminal kinds covered", TerminalKind.entries.toSet(), terminalKinds)
    }

    @Test
    fun forMode_reproducesEachModesPrintState() {
        // The inverse of classifyPrintStatus: a fixture built forMode(m) classifies back to m.
        for (mode in SampleFixtures.printStatusModes) {
            val state = SampleFixtures.forMode(mode)
            assertEquals("forMode($mode) must classify back to $mode", mode, classifyPrintStatus(state))
        }
    }

    @Test
    fun fineTuneVariants_arePresentAbsentAndBusy() {
        // Present: every capability gate true with non-null display-scaled values.
        val present = SampleFixtures.fineTuneAllPresent
        assertTrue("present sets all capability gates", present.hasGcodeMove && present.hasToolhead &&
            present.hasExtruder && present.hasFan && present.hasFwRetraction)
        assertNotNull("present has a non-null speedPct", present.speedPct)
        assertNotNull("present has a non-null retractLength", present.retractLength)
        assertTrue("present is not busy", !present.groupBusy)

        // Absent (FW-retraction): the HIDDEN path — gate false + retraction sub-values null.
        val absent = SampleFixtures.fineTuneNoFwRetraction
        assertTrue("FW-retraction gate is false", !absent.hasFwRetraction)
        assertEquals("retractLength null in the absent variant", null, absent.retractLength)
        assertTrue("the other gates stay present", absent.hasGcodeMove && absent.hasExtruder)

        // Busy: the whole-group lock.
        assertTrue("busy variant sets groupBusy", SampleFixtures.fineTuneBusy.groupBusy)

        assertEquals("three FineTune variants exposed", 3, SampleFixtures.fineTuneVariants.size)
    }

    @Test
    fun spoolListAndTempSeries_areNonEmpty() {
        assertTrue("dense spool list is non-empty", SampleFixtures.spoolList.isNotEmpty())
        assertTrue("temp series is non-empty", SampleFixtures.tempSeries.isNotEmpty())
        // At least one spool carries a multi-color filament (the split-swatch exemplar).
        assertTrue(
            "a multi-color spool exists",
            SampleFixtures.spoolList.any { (it.filament?.multiColorHexes ?: "").contains(',') },
        )
    }

    @Test
    fun defaultFineTuneVm_isStillAllAbsent() {
        // The contract the absent variant builds on: a bare FineTuneVm() is all-absent/null.
        val vm = FineTuneVm()
        assertTrue("default FineTuneVm has no capabilities", !vm.hasGcodeMove && !vm.hasToolhead &&
            !vm.hasExtruder && !vm.hasFan && !vm.hasFwRetraction)
        assertEquals("default speedPct is null (shows em-dash, never a fabricated 0)", null, vm.speedPct)
    }
}
