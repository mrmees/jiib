package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The dinghy-specific glue between the pure generator and the app's full token vocabulary — a port
 * of `../theme_theory/app/dinghy.js`'s `tokensFromPalette()`. [Palette.generate] emits a slim,
 * pure-neutral palette (surfaces / theme / status / pool / directional, as String hexes); dinghy
 * needs more surface/outline tiers (bg2, surface2/3, text3, hair, outline2) plus the accent/heat/go/
 * stop/directional alpha variants. We DERIVE those in-between tiers here, in Kotlin, by calling
 * 15-02's `internal` [Palette.hexToOklch] / [Palette.oklchToHex] helpers (an `lShift`) — so
 * [Palette.generate] stays a faithful 1:1 of `color.js` (the OKLCH math is NOT re-ported here, which
 * would risk golden drift), and then bake every value to an sRGB Compose [Color].
 *
 * This is the SECOND sanctioned producer of a complete [ThemeTokens] (besides the substrate's
 * baked [TokensDark]/[TokensLight]) and the ONLY place — alongside [BakedTokens] — that constructs a
 * literal/derived [Color] from generator output. Every emitted value is a baked sRGB `Color`, never
 * an Oklab-space Color: an Oklab Color renders WRONG on API < 26 (BakedTokens header law).
 *
 * Surfaces are PURE-NEUTRAL (D-16): `bg` is the generator's neutral bg verbatim — no faint cool
 * tint is reintroduced here.
 *
 * Pool overrides (D-09): a sparse `Map<Int, Color>` replaces individual `pool[i]` slots (the
 * data-pool is the user's editable surface). Applied defensively — an out-of-range index is dropped
 * (ignored), never crashes (threat T-15-03-01).
 */
object TokenBridge {

    /** clamp to [0,1] (JS `Math.min(1, Math.max(0, x))`). */
    private fun clamp01(x: Double): Double = x.coerceIn(0.0, 1.0)

    /**
     * Bake a `#rrggbb` String hex from the generator to a fully-opaque sRGB Compose [Color].
     * sRGB-only — never constructs an Oklab-space Color.
     */
    private fun bake(hex: String): Color {
        val s = hex.removePrefix("#")
        val r = s.substring(0, 2).toInt(16)
        val g = s.substring(2, 4).toInt(16)
        val b = s.substring(4, 6).toInt(16)
        return Color(red = r, green = g, blue = b, alpha = 0xFF)
    }

    /**
     * Port of `dinghy.js`'s `lShift(hex, dL)`: hex → OKLCH (via 15-02's `internal`
     * [Palette.hexToOklch]) → bump L by `dL` clamped to [0,1] → back to a baked sRGB [Color] (via
     * [Palette.oklchToHex]). The OKLCH math is NOT re-implemented here — it delegates to Palette so
     * derived tiers track the same color science as the generator (no golden drift).
     */
    private fun lShift(hex: String, dL: Double): Color {
        val o = Palette.hexToOklch(hex)
        return bake(Palette.oklchToHex(clamp01(o.L + dL), o.C, o.H))
    }

    /**
     * Port of `dinghy.js`'s `rgbaOf(hex, a)`: the RGB of `hex` with a float alpha `a` (0..1) packed
     * into the Compose Color's alpha channel — an alpha-bearing sRGB [Color].
     */
    private fun rgbaOf(hex: String, a: Double): Color = bake(hex).copy(alpha = a.toFloat())

    /**
     * Map a generated palette onto a complete [ThemeTokens], deriving the in-between surface/outline
     * tiers and the alpha variants, applying sparse pool [overrides], and threading the text-size
     * [fs] through. `gen.dark` selects the dark/light lShift/alpha constants (mirrors `dinghy.js`'s
     * `d = P.dark`).
     */
    fun build(gen: Palette.Generated, overrides: Map<Int, Color>, fs: Float): ThemeTokens {
        val d = gen.dark
        val s = gen.surfaces
        val t = gen.theme
        val st = gen.status

        // --- pool: bake the generated hexes, then apply sparse overrides at-index (D-09). ---
        val basePool = gen.pool.map { bake(it) }
        val pool: List<Color> =
            if (overrides.isEmpty()) {
                basePool
            } else {
                basePool.mapIndexed { i, c -> overrides[i] ?: c }  // out-of-range keys never match
            }

        // --- directional (D-07): accent LEADS — temperature = accent, xy = pool[0], z = pool[1].
        // This SUPERSEDES Phase-15's gen.directional (nozzle=pool[0] / directional.temperature) — the
        // ONE re-derivation point so Move (directional.xy/.z, D-08) and PrintStatus (directional.temperature)
        // consumers shift values WITHOUT shape change. accent survives all palette modes (D-05) so
        // temperature never collapses to text in Simple/HighContrast. Bed shares xy's pool[0], chamber
        // shares z's pool[1] (dual-tag preserved, shifted down one — never co-occur on screen).
        // Empty-pool guard (CR-02): pool[0]/pool[1] fall back to accent rather than index-crash. ---
        val accent = bake(t.primary)
        val dirXy = pool.getOrElse(0) { accent }
        val dirZ = pool.getOrElse(1) { accent }

        return ThemeTokens(
            // --- surfaces: neutral, polarity-flipped (D-16). bg verbatim (no tint); the rest derived. ---
            bg = bake(s.bg),
            bg2 = lShift(s.bg, if (d) 0.03 else -0.022),
            surface = bake(s.surface),
            surface2 = lShift(s.surface, if (d) 0.04 else -0.045),
            surface3 = lShift(s.surface, if (d) 0.085 else -0.09),
            text = bake(s.text),
            text2 = bake(s.muted),
            text3 = lShift(s.muted, if (d) -0.12 else 0.13),
            hair = rgbaOf(s.text, if (d) 0.08 else 0.11),
            outline = bake(s.divider),
            outline2 = lShift(s.divider, if (d) 0.11 else -0.12),
            // --- theme accent = the seed primary (one identity hue). ---
            accent = accent,
            accent2 = lShift(t.primary, if (d) 0.08 else -0.05),
            accentSoft = rgbaOf(t.primary, if (d) 0.16 else 0.12),
            accentLine = rgbaOf(t.primary, if (d) 0.55 else 0.50),
            accentGlow = rgbaOf(t.primary, if (d) 0.35 else 0.20),
            // --- status = 3 dedicated pool slots. dinghy's `heat` IS the caution color (D-13). ---
            heat = bake(st.caution),
            heatSoft = rgbaOf(st.caution, if (d) 0.16 else 0.14),
            heatGlow = rgbaOf(st.caution, if (d) 0.38 else 0.22),
            pool = pool,
            directional = Directional(
                temperature = accent, // D-07: accent leads (supersedes gen.directional.temperature)
                xy = dirXy,           // D-07: pool[0] (empty-pool → accent)
                z = dirZ,             // D-07: pool[1] (empty-pool → accent)
            ),
            go = bake(st.go),
            goSoft = rgbaOf(st.go, if (d) 0.16 else 0.14),
            goGlow = rgbaOf(st.go, if (d) 0.40 else 0.22),
            stop = bake(st.stop),
            stopSoft = rgbaOf(st.stop, if (d) 0.15 else 0.12),
            stopGlow = rgbaOf(st.stop, if (d) 0.42 else 0.22),
            edgeGlow = rgbaOf(s.muted, if (d) 0.24 else 0.12),
            rScreen = 30.dp,
            rCard = 22.dp,
            rCtrl = 16.dp,
            rPill = 999.dp,
            fs = fs,
            // The active palette mode, resolved from the generator's flags in ONE place (D-05) so
            // ThemeTokens.seriesColor is a pure enum read. The resolver maps its MODE_* string →
            // gen.simple/highContrast → this enum.
            mode = PaletteMode.fromFlags(gen.simple, gen.highContrast),
        )
    }
}
