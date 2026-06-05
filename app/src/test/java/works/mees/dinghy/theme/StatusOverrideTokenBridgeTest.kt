package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import works.mees.dinghy.config.Profile

/**
 * GREEN status-slot override path through [TokenBridge] (was the plan 15.1-01 Wave-0 RED scaffold;
 * realized in plan 15.1-04). Status colors are FULLY user-editable IN COLORFUL because shape + icon +
 * position carry the meaning (Area A), but the override is MODE-GATED (D-03/D-04 — the safety gap from
 * review):
 *   - Colorful:      override APPLIES over the generated status color; clearing reverts to generated.
 *   - Simple:        override IGNORED — status collapses to the text color (near-monochrome).
 *   - High-Contrast: override IGNORED — status FORCED to the fixed RYG safety palette (CVD escape hatch).
 *
 * The persisted override map is ONE String→Long wire map carrying the reserved status keys
 * "stop"/"caution"/"go" alongside the integer pool indices; the typed [StatusSlot] enum is the canonical
 * key. A junk/malformed status value FAILS SAFE to the generated color via [ThemePrefs.sanitizeTuple]'s
 * per-entry drop. Malformed keys (unknown string, bad int-looking) are dropped and never reach key.toInt().
 *
 * Both the ACTIVE-PROFILE resolution path ([Profile.toThemeTuple]) and the IDLE-GLOBAL path
 * ([ThemePrefs.sanitizeTuple]) funnel through [ThemeResolver.apply] — this exercises BOTH, not only the
 * bare [TokenBridge.build] call.
 */
class StatusOverrideTokenBridgeTest {

    private val seed = "#3f78ff"

    private fun bake(hex: String): Color {
        val s = hex.removePrefix("#")
        return Color(
            red = s.substring(0, 2).toInt(16),
            green = s.substring(2, 4).toInt(16),
            blue = s.substring(4, 6).toInt(16),
            alpha = 0xFF,
        )
    }

    /** The generated (un-overridden) status colors for the given mode, straight from the generator. */
    private fun generatedStatus(mode: String): Triple<Color, Color, Color> {
        val g = Palette.generate(
            seedHex = seed,
            dark = true,
            maxItems = 4,
            poolShift = 0,
            statusFromPool = true,
            simple = mode == ThemeResolver.MODE_SIMPLE,
            highContrast = mode == ThemeResolver.MODE_HIGH_CONTRAST,
        )
        return Triple(bake(g.status.stop), bake(g.status.caution), bake(g.status.go))
    }

    /**
     * Resolve via the SAME pipeline AppContainer.seedTheme uses: sanitize the wire map (idle path) OR a
     * Profile (active path) into a tuple, convert the Long maps via the shared helper, and apply.
     */
    private suspend fun resolveViaIdle(mode: String, wire: Map<String, Long>): ThemeTokens {
        val tuple = ThemePrefs.sanitizeTuple(
            rawSeed = seed, rawDark = true, rawMode = mode, rawShift = 0,
            rawMaxItems = 4, rawFs = "M", rawOverrides = wire,
        )
        return applyTuple(tuple)
    }

    private suspend fun resolveViaActiveProfile(mode: String, wire: Map<String, Long>): ThemeTokens {
        val tuple = Profile(
            id = "p1", host = "h", seedHex = seed, dark = true, paletteMode = mode,
            poolShift = 0, maxItems = 4, poolOverrides = wire, fsChoice = "M",
        ).toThemeTuple()
        return applyTuple(tuple)
    }

    private suspend fun applyTuple(tuple: ThemePrefs.ThemeTuple): ThemeTokens {
        val resolver = ThemeResolver()
        resolver.apply(
            seedHex = tuple.seedHex,
            dark = tuple.dark,
            paletteMode = tuple.paletteMode,
            poolShift = tuple.poolShift,
            maxItems = tuple.maxItems,
            overrides = tuple.poolOverrides.mapValues { it.value.toComposeColor() },
            statusOverrides = tuple.statusOverrides.mapValues { it.value.toComposeColor() },
            fs = tuple.fs,
        )
        return resolver.tokens.first()
    }

    // ---- (a) Colorful: override APPLIES; clear reverts ---------------------------------------------

    @Test
    fun statusKeyOverride_appliedOverGeneratedColor() = runTest {
        val customStop = 0xFFAB12CDL
        val tok = resolveViaIdle(ThemeResolver.MODE_COLORFUL, mapOf(StatusSlot.Stop.key to customStop))
        val (genStop, genCaution, genGo) = generatedStatus(ThemeResolver.MODE_COLORFUL)
        assertEquals("stop = the override", customStop.toComposeColor(), tok.stop)
        assertNotEquals("stop differs from generated", genStop, tok.stop)
        // caution + go untouched (no override) → generated.
        assertEquals("caution generated", genCaution, tok.heat)
        assertEquals("go generated", genGo, tok.go)
    }

    @Test
    fun clearedStatusOverride_fallsBackToGenerated() = runTest {
        // No "caution" override present → heat (caution) equals the generated status color.
        val tok = resolveViaIdle(ThemeResolver.MODE_COLORFUL, emptyMap())
        val (_, genCaution, _) = generatedStatus(ThemeResolver.MODE_COLORFUL)
        assertEquals("caution clear-to-generated", genCaution, tok.heat)
    }

    @Test
    fun overrideDrivesSoftAndGlowHalo_inColorful() = runTest {
        val customGo = 0xFF00AA55L
        val tok = resolveViaIdle(ThemeResolver.MODE_COLORFUL, mapOf(StatusSlot.Go.key to customGo))
        // The soft/glow variants derive from the RESOLVED go color (the override drives its halo too).
        assertEquals("go base = override", customGo.toComposeColor(), tok.go)
        assertEquals("goSoft alpha-variant of override", customGo.toComposeColor().copy(alpha = tok.goSoft.alpha), tok.goSoft)
        assertEquals("goGlow alpha-variant of override", customGo.toComposeColor().copy(alpha = tok.goGlow.alpha), tok.goGlow)
    }

    // ---- (b) Simple: override IGNORED, status = text -----------------------------------------------

    @Test
    fun simpleMode_ignoresStatusOverride_statusIsText() = runTest {
        val custom = 0xFFAB12CDL
        val withOverride = resolveViaIdle(
            ThemeResolver.MODE_SIMPLE,
            mapOf(StatusSlot.Stop.key to custom, StatusSlot.Caution.key to custom, StatusSlot.Go.key to custom),
        )
        val noOverride = resolveViaIdle(ThemeResolver.MODE_SIMPLE, emptyMap())
        // A stored override does NOT change the rendered status color in Simple.
        assertEquals("stop unchanged by override", noOverride.stop, withOverride.stop)
        assertEquals("caution unchanged by override", noOverride.heat, withOverride.heat)
        assertEquals("go unchanged by override", noOverride.go, withOverride.go)
        // Simple collapses status to text.
        assertEquals("stop = text", withOverride.text, withOverride.stop)
        assertEquals("go = text", withOverride.text, withOverride.go)
        assertNotEquals("override color NOT applied", custom.toComposeColor(), withOverride.stop)
    }

    // ---- (c) High-Contrast: override IGNORED, status = fixed RYG ------------------------------------

    @Test
    fun highContrastMode_ignoresStatusOverride_statusIsFixedRyg() = runTest {
        val custom = 0xFFAB12CDL
        val withOverride = resolveViaIdle(
            ThemeResolver.MODE_HIGH_CONTRAST,
            mapOf(StatusSlot.Stop.key to custom, StatusSlot.Caution.key to custom, StatusSlot.Go.key to custom),
        )
        val noOverride = resolveViaIdle(ThemeResolver.MODE_HIGH_CONTRAST, emptyMap())
        // The fixed RYG safety palette is preserved regardless of any stored override (D-04 escape hatch).
        assertEquals("stop = forced RYG", noOverride.stop, withOverride.stop)
        assertEquals("caution = forced RYG", noOverride.heat, withOverride.heat)
        assertEquals("go = forced RYG", noOverride.go, withOverride.go)
        assertNotEquals("override color NOT applied", custom.toComposeColor(), withOverride.stop)
    }

    // ---- malformed keys (never crash; never reach key.toInt()) -------------------------------------

    @Test
    fun malformedKeys_areDropped_validStatusKeyApplies_inColorful() = runTest {
        val customStop = 0xFFAB12CDL
        val tok = resolveViaIdle(
            ThemeResolver.MODE_COLORFUL,
            mapOf(
                "totally-unknown" to 0xFF111111L,   // unknown string key → dropped
                "0x1F" to 0xFF222222L,              // bad int-looking key → dropped (not parseable, not status)
                StatusSlot.Stop.key to customStop,  // valid status key → applies
            ),
        )
        val (genStop, _, _) = generatedStatus(ThemeResolver.MODE_COLORFUL)
        assertEquals("valid status key applies", customStop.toComposeColor(), tok.stop)
        assertNotEquals("stop is the override, not generated", genStop, tok.stop)
        // No crash reaching here is the malformed-key assertion (the bad keys never hit key.toInt()).
    }

    @Test
    fun sanitize_splitsKeysCorrectly_statusVsPool_dropsBoth() {
        val tuple = ThemePrefs.sanitizeTuple(
            rawSeed = seed, rawDark = true, rawMode = "Colorful", rawShift = 0,
            rawMaxItems = 4, rawFs = "M",
            rawOverrides = mapOf(
                "stop" to 0xFFAB12CDL,       // status
                "2" to 0xFF445566L,          // pool index
                "nope" to 0xFF000000L,       // malformed → dropped from BOTH maps
            ),
        )
        assertEquals("status map has stop only", 1, tuple.statusOverrides.size)
        assertEquals(0xFFAB12CDL, tuple.statusOverrides["stop"])
        assertEquals("pool map has index 2 only", 1, tuple.poolOverrides.size)
        assertEquals(0xFF445566L, tuple.poolOverrides[2])
    }

    // ---- both resolution paths exercised (active profile + idle global) -----------------------------

    @Test
    fun overrideApplies_onBothActiveProfileAndIdleGlobalPaths() = runTest {
        val customStop = 0xFFAB12CDL
        val wire = mapOf(StatusSlot.Stop.key to customStop)
        val idle = resolveViaIdle(ThemeResolver.MODE_COLORFUL, wire)
        val active = resolveViaActiveProfile(ThemeResolver.MODE_COLORFUL, wire)
        assertEquals("idle path applies", customStop.toComposeColor(), idle.stop)
        assertEquals("active-profile path applies", customStop.toComposeColor(), active.stop)
        assertEquals("both paths agree", idle.stop, active.stop)
    }

    @Test
    fun gatingHolds_onBothPaths_inHighContrast() = runTest {
        val custom = 0xFFAB12CDL
        val wire = mapOf(StatusSlot.Stop.key to custom)
        val idle = resolveViaIdle(ThemeResolver.MODE_HIGH_CONTRAST, wire)
        val active = resolveViaActiveProfile(ThemeResolver.MODE_HIGH_CONTRAST, wire)
        assertNotEquals("idle gating ignores override", custom.toComposeColor(), idle.stop)
        assertNotEquals("active gating ignores override", custom.toComposeColor(), active.stop)
        assertEquals("both paths force the same RYG", idle.stop, active.stop)
    }
}
