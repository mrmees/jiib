package works.mees.dinghy.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.ui.finetune.FineTuneVm
import works.mees.dinghy.ui.printstatus.PrintStatusMode
import works.mees.dinghy.ui.printstatus.TerminalKind

/**
 * Phase-18 Wave-0 **compile scaffold** for the preview/fixture module (SC-2).
 *
 * ⚠ This is a COMPILE SCAFFOLD, NOT a failing "RED" test. It MUST compile day-one and PASS today
 * by asserting CURRENT facts about EXISTING typed symbols ([PrintStatusMode], [TerminalKind],
 * [FineTuneVm]). Per [[dinghy-wave0-red-scaffold-compile.md]] the whole test sourceset is compiled
 * before the `--tests` filter runs, so a scaffold that referenced a not-yet-built symbol (the future
 * `works.mees.dinghy.preview.SampleFixtures`) as a LIVE Kotlin import would brick EVERY per-wave test
 * run. The future symbol is therefore referenced ONLY in a `// TODO(18-02):` comment below; plan 18-02
 * converts that comment to a live typed assertion when `SampleFixtures` exists.
 */
class SampleFixturesTest {

    /**
     * Current fact: the four Print-Status modes the preview fixtures must cover all exist and are
     * distinct (the `@PreviewParameter` axis 18-02 will expose via `SampleFixtures.printStatusModes`).
     */
    @Test
    fun printStatusModes_existAndAreDistinct() {
        val modes = listOf(
            PrintStatusMode.Standby,
            PrintStatusMode.Printing,
            PrintStatusMode.Paused,
            PrintStatusMode.Terminal(TerminalKind.Complete),
            PrintStatusMode.Terminal(TerminalKind.Cancelled),
            PrintStatusMode.Terminal(TerminalKind.Error),
        )
        // All six are distinct (Terminal carries its kind).
        assertEquals(6, modes.toSet().size)
        // The three terminal kinds are exactly the enum.
        assertEquals(3, TerminalKind.entries.size)

        // TODO(18-02): convert to a live assertion when works.mees.dinghy.preview.SampleFixtures exists —
        //   assert SampleFixtures.printStatusModes covers all 4 PrintStatusMode states (Standby, Printing,
        //   Paused, Terminal×kinds) so the @PreviewParameter provider renders every state. Do NOT import
        //   SampleFixtures until it is built (compile-day-one rule).
    }

    /**
     * Current fact: a default [FineTuneVm] constructs with all-absent capability gates and null values
     * (the "absent" fixture variant 18-02 builds present/absent/busy stand-ins from).
     */
    @Test
    fun fineTuneVm_defaultIsAllAbsentAndEmpty() {
        val vm = FineTuneVm()
        assertTrue("default FineTuneVm has no capabilities", !vm.hasGcodeMove && !vm.hasToolhead &&
            !vm.hasExtruder && !vm.hasFan && !vm.hasFwRetraction)
        assertTrue("default FineTuneVm is not group-busy", !vm.groupBusy)
        assertEquals("default speedPct is null (shows em-dash, never a fabricated 0)", null, vm.speedPct)

        // TODO(18-02): convert to a live assertion when SampleFixtures exposes the FineTune present/absent/
        //   busy fixture variants — assert the "present" fixture sets capability gates true with non-null
        //   display-scaled values, and the "busy" fixture sets groupBusy = true.
    }
}
