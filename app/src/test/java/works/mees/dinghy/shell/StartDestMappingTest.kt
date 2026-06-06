package works.mees.dinghy.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import works.mees.dinghy.ui.route.Dest

/**
 * Phase-18 Wave-0 **compile scaffold** for the debug-gated `start_dest` intent hook (SC-4b, unit half).
 *
 * ⚠ COMPILE SCAFFOLD, NOT a failing "RED" test — it MUST compile day-one and PASS today. The pure
 * extra-string → [Dest] mapping function (e.g. `parseStartDest`, RESEARCH Q3 / Security §V5: safe
 * enum parse with default, unknown value → ignore/never crash) is built in plan 18-04. Per
 * [[dinghy-wave0-red-scaffold-compile.md]] the whole test sourceset compiles before the `--tests`
 * filter, so the not-yet-built mapping fn appears ONLY in the `// TODO(18-04):` comment below. The
 * body asserts a CURRENT fact about the EXISTING [Dest] enum so the scaffold compiles + passes now.
 */
class StartDestMappingTest {

    /**
     * Current fact: [Dest] (the real route enum, ui/route/TopRoute.kt) carries the targets a
     * `start_dest` extra will name — including [Dest.FineTune], the canonical example from the plan —
     * and `Dest.valueOf` is the safe-parse primitive the mapping fn will wrap with a default.
     */
    @Test
    fun dest_enum_carriesStartDestTargets() {
        // FineTune is the worked example in the validation map (`--es start_dest FineTune`).
        assertNotNull(Dest.FineTune)
        // Safe-parse primitive resolves a known name to its constant (the mapping fn wraps this).
        assertEquals(Dest.FineTune, Dest.valueOf("FineTune"))
        // The enum is non-empty (the mapping fn's value space).
        assertEquals(Dest.entries.toSet().size, Dest.entries.size)

        // TODO(18-04): convert to a live assertion when the pure start_dest mapping fn (parseStartDest)
        //   exists — assert (a) a known extra ("FineTune") maps to Dest.FineTune, and (b) an unknown/garbage
        //   extra maps to null (or the safe default) and NEVER throws (Security §V5 safe enum parse).
        //   Do NOT import the mapping fn until 18-04 builds it (compile-day-one rule).
    }
}
