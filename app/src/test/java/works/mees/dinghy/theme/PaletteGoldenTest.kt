package works.mees.dinghy.theme

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED golden-conformance scaffold (plan 15-01, Wave 0).
 *
 * GREEN in plan 15-02 when the Kotlin `Palette` port lands. Each method will then
 * load the committed `app/src/test/resources/color-golden.json` vector for its key
 * and assert the Kotlin `Palette.generate(...)` output matches the `color.js` oracle
 * bit-for-bit (accent/surfaces/pool/poolRoles/directional/status/minHueGap). The
 * fixture IS the conformance contract — a transcription typo in any of the 25+ OKLab
 * matrix constants produces silent color drift only these golden vectors catch.
 *
 * `[[dinghy-wave0-red-scaffold-compile]]`: this scaffold MUST compile against the
 * CURRENT sourceset. `Palette` does NOT exist yet, so every body is `fail(...)` and
 * NO unbuilt symbol is referenced. Gradle compiles the whole test sourceset before
 * applying the `--tests` filter — a non-compiling scaffold bricks ALL per-wave runs.
 */
class PaletteGoldenTest {

    /**
     * Loads the `color-golden.json` fixture from the test classpath.
     *
     * Stub for the RED phase: documents the classpath-resource access the GREEN
     * implementation will use, but does NOT yet parse into the (unbuilt) `Palette`
     * output types. The building plan (15-02) replaces this with a real loader +
     * per-key vector lookup.
     */
    @Suppress("unused")
    private fun loadFixture() {
        // GREEN (15-02): javaClass.getResourceAsStream("/color-golden.json")
        // → parse JSON → expose per-label vectors for the assertions below.
    }

    @Test
    fun defaultSeedDark_matchesOracle() {
        fail("RED — Palette not yet ported, GREEN in plan 15-02")
    }

    @Test
    fun defaultSeedLight_matchesOracle() {
        fail("RED — Palette not yet ported, GREEN in plan 15-02")
    }

    @Test
    fun simpleMode_matchesOracle() {
        fail("RED — Palette not yet ported, GREEN in plan 15-02")
    }

    @Test
    fun highContrastMode_matchesOracle() {
        fail("RED — Palette not yet ported, GREEN in plan 15-02")
    }

    @Test
    fun edgeHueRed_matchesOracle() {
        fail("RED — Palette not yet ported, GREEN in plan 15-02")
    }

    @Test
    fun edgeHueYellow_matchesOracle() {
        fail("RED — Palette not yet ported, GREEN in plan 15-02")
    }

    @Test
    fun poolShift120_matchesOracle() {
        fail("RED — Palette not yet ported, GREEN in plan 15-02")
    }
}
