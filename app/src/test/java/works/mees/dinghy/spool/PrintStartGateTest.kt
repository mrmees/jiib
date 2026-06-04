package works.mees.dinghy.spool

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED scaffold (SPOOL-07) — warn-only print-start gate decision table (D-01).
 *
 * Wave-0 compile-safety: pure `fail()` bodies, no reference to the unbuilt `PrintStartGate` symbols
 * (built in Wave 2 under `ui/spool/PrintStartGate.kt`, analog `ui/files/DeleteGate.kt`).
 *
 * Target contract (D-01 — Wave 2 turns these green). The gate NEVER blocks; each condition yields an
 * ordered amber warning the confirm flow can tap past:
 *  - no active spool                                  → warn "no spool selected"
 *  - material family mismatch (D-05)                  → warn "material mismatch"
 *  - remaining < needed + margin                      → warn "low filament"
 *  - active spool archived                            → warn "archived spool"
 *  - pending_reports stale                            → warn "pending report"
 *  - spool fetch failed                               → warn "could not verify spool"
 *  - clean pass (all green)                           → empty warning list (no block)
 *  - `filamentWeightTotal == null`                    → SKIP the low-filament check (no false warn)
 */
class PrintStartGateTest {

    @Test
    fun warnsWhenNoActiveSpool() {
        fail("RED: gate warn — no active spool — not yet implemented")
    }

    @Test
    fun warnsOnMaterialFamilyMismatch() {
        fail("RED: gate warn — material family mismatch (D-05) — not yet implemented")
    }

    @Test
    fun warnsOnLowRemainingFilament() {
        fail("RED: gate warn — remaining < needed + margin — not yet implemented")
    }

    @Test
    fun warnsOnArchivedSpool() {
        fail("RED: gate warn — archived active spool — not yet implemented")
    }

    @Test
    fun warnsOnStalePendingReports() {
        fail("RED: gate warn — stale pending_reports — not yet implemented")
    }

    @Test
    fun warnsWhenSpoolFetchFailed() {
        fail("RED: gate warn — spool fetch failed — not yet implemented")
    }

    @Test
    fun passesCleanWithNoWarningsAndNeverBlocks() {
        fail("RED: gate clean pass (empty warnings, never blocks) not yet implemented")
    }

    @Test
    fun skipsLowFilamentCheckWhenWeightTotalNull() {
        fail("RED: gate skips low-filament check when filamentWeightTotal == null — not yet implemented")
    }
}
