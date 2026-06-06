package works.mees.dinghy.designsystem.icons

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-18 Wave-0 **compile scaffold** for the semantic icon-token registry (SC-3b).
 *
 * ⚠ COMPILE SCAFFOLD, NOT a failing "RED" test — it MUST compile day-one and PASS today. The icon
 * registry (`works.mees.dinghy.designsystem.icons.DinghyIcons` + the `IconRef` sealed interface +
 * the `DinghyIcon(primary, alternate)` data class, RESEARCH Q6 / D-07 / D-08) does NOT exist yet;
 * it is built in plan 18-03. Per [[dinghy-wave0-red-scaffold-compile.md]] the entire test sourceset
 * compiles before the `--tests` filter, so this scaffold MUST NOT reference `DinghyIcons` / `IconRef`
 * / `DinghyIcon` as live Kotlin symbols — they appear ONLY in the `// TODO(18-03):` comment below.
 * The body asserts a CURRENT fact so the file compiles and passes immediately.
 */
class DinghyIconsTest {

    /**
     * Current fact placeholder: the registry contract this scaffold will enforce is "every token has
     * a non-blank `alternate` remap handle (D-07) and a resolvable `IconRef` source (D-08-separable)".
     * Until 18-03 builds the registry, assert a trivially-true invariant so the scaffold compiles and
     * the per-wave `--tests` filter is never bricked.
     */
    @Test
    fun iconRegistryContract_placeholderUntil_18_03() {
        // The contract the live assertion will check (kept as plain data here, no registry symbols):
        val expectedNonBlankAlternateRule = true
        assertTrue("icon-token alternate handle must be non-blank (enforced live in 18-03)",
            expectedNonBlankAlternateRule)

        // TODO(18-03): convert to a live assertion when works.mees.dinghy.designsystem.icons.DinghyIcons
        //   exists — iterate every DinghyIcons entry and assert (a) icon.alternate is non-blank, and
        //   (b) icon.primary resolves to exactly one IconRef source (Ligature(name) XOR Drawable(resId)).
        //   Do NOT import DinghyIcons / IconRef / DinghyIcon until built (compile-day-one rule).
    }
}
