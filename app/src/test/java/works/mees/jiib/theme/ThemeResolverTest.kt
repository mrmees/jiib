package works.mees.jiib.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-04/D-05: the resolver is the single StateFlow source of truth, GENERATE-AND-CACHE. The no-arg ctor
 * generates the default-seed palette; setOverride edits a data-pool slot; apply(full tuple) re-emits
 * exactly ONCE; setMode wires the palette modes. (The retired per-role TokenDelta shim tests were
 * deleted in 15-06 — chrome is seed-derived now.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeResolverTest {

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
