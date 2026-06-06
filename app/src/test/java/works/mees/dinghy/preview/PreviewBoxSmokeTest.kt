package works.mees.dinghy.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.ThemeResolver

/**
 * The Wave-2 bake/theme SEAM guard (the FIRST acceptance criterion of plan 18-02, Codex HIGH-2).
 *
 * This is a pure JVM unit test — it renders NO `@Preview` and touches no Compose runtime. It proves
 * the DATA seam [PreviewBox] depends on: `ThemeResolver().bake(seed)` (a no-arg resolver + the pure
 * per-tuple bake) compiles AND produces a distinct, non-null [works.mees.dinghy.theme.ThemeTokens]
 * for each of the six theme combos + the fs=L seed. If a future change breaks the overload or the
 * no-arg constructor assumption, it fails LOUDLY HERE in Wave 2 rather than silently across every
 * downstream exemplar. (The `@Composable` PreviewBox render path itself is Studio-eyeballed at the
 * phase gate; this guards the data seam the wrapper is built on.)
 */
class PreviewBoxSmokeTest {

    private val resolver = ThemeResolver()

    @Test
    fun bake_eachCombo_returnsNonNullTokens() {
        for (seed in themeCombos + fsLargeSeed) {
            val tokens = resolver.bake(seed)
            assertNotNull("bake($seed) returned null tokens — the seam is broken", tokens)
        }
    }

    @Test
    fun bake_sixCombos_areNotAllIdentical() {
        val baked = themeCombos.map { resolver.bake(it) }
        assertEquals("themeCombos must contain the 6 named combos", 6, baked.size)
        // Distinct token sets prove the seam actually threads mode/polarity through bake (a stubbed
        // or wrong overload would collapse them to one identical default theme).
        assertTrue(
            "the six theme combos baked to a single identical theme — bake is not seeing the seeds",
            baked.toSet().size > 1,
        )
    }

    @Test
    fun bake_lightAndDark_differ() {
        // The clearest polarity proof: Colorful dark vs Colorful light must not bake identically.
        assertTrue(
            "colorfulDark and colorfulLight baked identically — dark/light polarity not applied",
            resolver.bake(colorfulDark) != resolver.bake(colorfulLight),
        )
    }

    @Test
    fun bake_fsLargeSeed_carriesLargeFontScale() {
        // The fs=L injection (the @Preview(fontScale=) NO-OP workaround) must reach the baked tokens.
        assertEquals(
            "fsLargeSeed must bake fs = FontScale.L; --fs is the SOLE text-size authority (THEME-02)",
            FontScale.L.multiplier,
            resolver.bake(fsLargeSeed).fs,
            0.0001f,
        )
    }
}
