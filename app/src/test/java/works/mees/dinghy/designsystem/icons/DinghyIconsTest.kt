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
}
