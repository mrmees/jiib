package works.mees.dinghy.designsystem.icons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live registry contract (SC-3b / D-07 / D-08), converted from the 18-01 compile scaffold once the
 * `works.mees.dinghy.designsystem.icons` registry exists (built in 18-03). Iterates [DinghyIcons.all]
 * (Amendment 3 — the hand-rolled Phase-22-readiness list, NOT reflection) to enforce:
 *   (a) every entry has a non-blank [DinghyIcon.alternate] remap handle (D-07),
 *   (b) every entry resolves to exactly one [IconRef] source (Ligature XOR Drawable — D-08 separable), and
 *   (c) [DinghyIcon.alternate] is UNIQUE across the whole registry (a duplicate makes a fork's remap
 *       ambiguous — Codex LOW-8).
 */
class DinghyIconsTest {

    @Test
    fun everyEntry_hasNonBlankAlternate_andResolvableIconRef() {
        assertFalse("DinghyIcons.all must not be empty", DinghyIcons.all.isEmpty())
        DinghyIcons.all.forEach { icon ->
            assertTrue(
                "alternate remap handle must be non-blank (D-07): $icon",
                icon.alternate.isNotBlank(),
            )
            // (b) the sealed shape already guarantees exactly-one-source at compile time; assert each
            // arm carries a usable primitive so a malformed entry (blank ligature / 0 resId) is caught.
            when (val ref = icon.primary) {
                is IconRef.Ligature -> assertTrue(
                    "Ligature name must be non-blank: $icon",
                    ref.name.isNotBlank(),
                )
                is IconRef.Drawable -> assertTrue(
                    "Drawable resId must be a real resource id (non-zero): $icon",
                    ref.resId != 0,
                )
            }
        }
    }

    @Test
    fun alternate_isUnique_acrossAllEntries() {
        val alternates = DinghyIcons.all.map { it.alternate }
        val distinct = alternates.toSet()
        assertEquals(
            "every DinghyIcon.alternate must be unique (the one-place D-07 remap handle); " +
                "duplicates: ${alternates.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}",
            alternates.size,
            distinct.size,
        )
    }

    /**
     * (WR-02) The RENDERED [IconRef] must be unique across the registry, not just the [alternate] string.
     * Two distinct semantic tokens that resolve to the SAME glyph (e.g. the `donut_large` `Progress` +
     * `LauncherSpool` collision) draw an identical icon for two different affordances — and because both can
     * appear on the SAME PrintStatus screen, that violates the registry's "an icon is never used twice on
     * one screen" rule. The old `alternate`-only uniqueness check was blind to it (false-confidence). This
     * asserts on `it.primary` so an undocumented glyph collision fails LOUDLY.
     *
     * NOTE: if a future phase intentionally shares ONE source across distinct semantic tokens (e.g. WR-03's
     * proposed `RetractSpeed`/`UnretractSpeed` both wrapping `R.drawable.sprint`), allow-list those specific
     * `alternate`s here explicitly rather than weakening the check — an accidental dup must still fail.
     */
    @Test
    fun iconRef_isUnique_acrossAllEntries() {
        // Allow-list: IconRefs intentionally shared by tokens that NEVER co-occur on one screen, so the
        // "icon-never-twice on one screen" rule (WR-02) is not actually violated. Each entry MUST be
        // justified by render-site separation (per the NOTE above — allow-list, don't weaken the check):
        //   - "output_circle": OutputCircle (FineTune Extrusion-factor control) + LauncherExtrude
        //     (PrintStatus launcher Extrude tile) — the same official extrude/output glyph for the same
        //     concept, on different screens that never render together (260607-fts).
        val allowedSharedLigatures = setOf("output_circle")
        val unexpectedDuplicates = DinghyIcons.all
            .groupBy { it.primary }
            .filter { (ref, dups) ->
                dups.size > 1 && !(ref is IconRef.Ligature && ref.name in allowedSharedLigatures)
            }
            .mapValues { (_, dups) -> dups.map { it.alternate } }
        assertEquals(
            "every DinghyIcon.primary (rendered IconRef) must be unique across the registry unless " +
                "explicitly allow-listed as a never-co-occurring shared glyph (WR-02). Unexpected " +
                "duplicate sources → entries: $unexpectedDuplicates",
            emptyMap<IconRef, List<String>>(),
            unexpectedDuplicates,
        )
    }

    /**
     * (D-15 / SC-3b) Drift-guard for the Phase-18.1 drawable→ligature flip. After the six registry
     * entries flip (StatusStop, BabystepCompress, BabystepExpand, PressureAdvance, Increase, Decrease),
     * the ONLY remaining [IconRef.Drawable] entries are the D-10 hand-authored printer-domain customs
     * `Nozzle`/`HeatBed`. Any other [IconRef.Drawable] is an un-flipped 18.1 regression.
     *
     * NOTE: this method is intentionally RED until Plan 18.1-02 performs the registry flips — it is the
     * RED half of a guard that goes GREEN in Wave 1. The other tests in this class stay GREEN throughout.
     */
    @Test
    fun registryDrawableEntries_areOnlyTheCustomKeepers() {
        val drawableBacked = DinghyIcons.all
            .filter { it.primary is IconRef.Drawable }
            .map { it.alternate }
            .toSet()
        assertEquals(
            "after 18.1 the only drawable-backed icons are the D-10 customs (Nozzle, HeatBed); " +
                "any other IconRef.Drawable is an un-flipped 18.1 regression",
            setOf("nozzle", "heat_bed"),
            drawableBacked,
        )
    }
}
