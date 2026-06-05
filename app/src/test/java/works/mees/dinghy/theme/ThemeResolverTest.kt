package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-05 + Pitfall 7: the resolver is the single StateFlow source of truth, and the delta model never
 * drifts from its base — an empty delta set resolves IDENTICALLY to the base; a single override
 * changes EXACTLY one token and leaves the rest equal to the base.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeResolverTest {

    @Test
    fun emptyDelta_resolvesIdenticalToBase_dark() {
        val resolved = resolve(ThemeBase.Dark, TokenDelta.EMPTY, FontScale.M.multiplier)
        // empty delta === base (Pitfall 7). M is the baked default fs, so equality is total.
        assertEquals(TokensDark, resolved)
    }

    @Test
    fun emptyDelta_resolvesIdenticalToBase_light() {
        val resolved = resolve(ThemeBase.Light, TokenDelta.EMPTY, FontScale.M.multiplier)
        assertEquals(TokensLight, resolved)
    }

    @Test
    fun singleAccentDelta_overridesExactlyOneToken() {
        val custom = Color(0xFF112233)
        val delta = TokenDelta.of(TokenDelta.Role.Accent to custom.toArgb())
        val resolved = resolve(ThemeBase.Dark, delta, FontScale.M.multiplier)

        // The one overridden token changed...
        assertEquals(custom, resolved.accent)
        assertNotEquals(TokensDark.accent, resolved.accent)
        // ...and EVERY other token still equals the base (no drift).
        assertEquals(TokensDark.copy(accent = custom), resolved)
    }

    @Test
    fun resolver_exposesStateFlow_andReEmitsOnEachMutator() = runTest {
        val resolver = ThemeResolver(base = ThemeBase.Dark)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver.tokens.toList(emissions)
        }
        runCurrent()
        assertEquals(TokensDark, resolver.tokens.value) // seeded

        resolver.setBase(ThemeBase.Light)
        runCurrent()
        assertEquals(TokensLight, resolver.tokens.value)

        val custom = Color(0xFFABCDEF)
        resolver.setDeltas(TokenDelta.of(TokenDelta.Role.Stop to custom.toArgb()))
        runCurrent()
        assertEquals(custom, resolver.tokens.value.stop)

        resolver.setFs(FontScale.L.multiplier)
        runCurrent()
        assertEquals(1.32f, resolver.tokens.value.fs)

        // seed + 3 mutators = 4 distinct emissions.
        assertEquals(4, emissions.size)
        job.cancel()
    }

    @Test
    fun apply_setsAllThreeInputsInOneEmission() = runTest {
        val resolver = ThemeResolver()
        val custom = Color(0xFF00FF00)
        resolver.apply(
            base = ThemeBase.Light,
            deltas = TokenDelta.of(TokenDelta.Role.Go to custom.toArgb()),
            fs = FontScale.S.multiplier,
        )
        val t = resolver.tokens.value
        assertEquals(TokensLight.bg, t.bg)       // base applied
        assertEquals(custom, t.go)               // delta applied
        assertEquals(1.0f, t.fs)                 // fs applied
    }

    @Test
    fun emptyDelta_isSingleton() {
        assertSame(TokenDelta.EMPTY, TokenDelta.EMPTY)
        assertEquals(true, TokenDelta.EMPTY.isEmpty)
    }

    // -----------------------------------------------------------------------------------------------
    // 15-04: generate-and-cache (the NEW live API). The no-arg ctor generates the default-seed palette;
    // setOverride edits a data-pool slot; apply(full tuple) re-emits exactly ONCE; setMode wires modes.
    // -----------------------------------------------------------------------------------------------

    /** The default-seed oracle: what the resolver SHOULD produce out-of-box (generate-and-cache). */
    private fun defaultOracle(fs: Float = FontScale.M.multiplier): ThemeTokens {
        val gen = Palette.generate(
            seedHex = DEFAULT_SEED_HEX,
            dark = true,
            maxItems = DEFAULT_POOL_MAX_ITEMS,
            poolShift = 0,
            statusFromPool = true,
            simple = false,
            highContrast = false,
        )
        return TokenBridge.build(gen, emptyMap(), fs)
    }

    @Test
    fun defaultConstructed_generatesDefaultSeedTokens() {
        val resolver = ThemeResolver()
        val oracle = defaultOracle()
        val t = resolver.tokens.value
        // Generate-and-cache produces the expected default-seed tokens (NOT the legacy baked table).
        assertEquals(oracle.accent, t.accent)
        assertEquals(oracle.bg, t.bg)
        assertEquals(oracle.pool, t.pool)
    }

    @Test
    fun setOverride_replacesPoolSlot_andClearRestoresSeedDerived() {
        val resolver = ThemeResolver()
        val seedDerived = resolver.tokens.value.pool[1]
        val custom = Color(0xFF00FFAA)

        resolver.setOverride(1, custom)
        assertEquals(custom, resolver.tokens.value.pool[1])

        // Clearing the slot restores the seed-derived color at that index.
        resolver.setOverride(1, null)
        assertEquals(seedDerived, resolver.tokens.value.pool[1])
    }

    @Test
    fun applyTuple_emitsExactlyOnce_noFlicker() = runTest {
        val resolver = ThemeResolver()
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver.tokens.toList(emissions)
        }
        runCurrent()
        assertEquals(1, emissions.size) // the seeded value

        resolver.apply(
            seedHex = "#c8b400",
            dark = false,
            paletteMode = ThemeResolver.MODE_COLORFUL,
            poolShift = 0,
            maxItems = DEFAULT_POOL_MAX_ITEMS,
            overrides = emptyMap(),
            fs = FontScale.S.multiplier,
        )
        runCurrent()
        // The full tuple is ONE re-emit (Pitfall 3) — count increments by exactly 1, not by 7.
        assertEquals(2, emissions.size)
        assertEquals(1.0f, resolver.tokens.value.fs) // tuple applied
        job.cancel()
    }

    @Test
    fun setMode_simple_collapsesPoolToMonoGray() {
        val resolver = ThemeResolver()
        resolver.setMode(ThemeResolver.MODE_SIMPLE)
        val pool = resolver.tokens.value.pool

        // Simple mode collapses the data pool to a single mono (gray) family: every slot shares the
        // same hue family — i.e. r≈g≈b on each entry (a gray), unlike the Colorful chromatic pool.
        pool.forEach { c ->
            val r = (c.red * 255f).toInt()
            val g = (c.green * 255f).toInt()
            val b = (c.blue * 255f).toInt()
            val maxChan = maxOf(r, g, b)
            val minChan = minOf(r, g, b)
            assertTrue(
                "Simple-mode pool entry should be near-gray (got r=$r g=$g b=$b)",
                (maxChan - minChan) <= 12,
            )
        }
    }
}
