package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    /** Opaque Compose Color → "#RRGGBB" (Locale.US — never non-Latin digits, mirrors hueToHex). */
    private fun hexOf(c: Color): String =
        String.format(java.util.Locale.US, "#%06X", c.toArgb() and 0xFFFFFF)

    /**
     * Map a generated palette onto a complete [ThemeTokens], deriving the in-between surface/outline
     * tiers and the alpha variants, applying sparse pool [overrides] + the mode-gated status
     * [statusOverrides], and threading the text-size [fs] through. `gen.dark` selects the dark/light
     * lShift/alpha constants (mirrors `dinghy.js`'s `d = P.dark`).
     *
     * STATUS OVERRIDES ARE MODE-GATED (D-03/D-04 — the safety-correctness gap from review):
     *  - Colorful: a user `stop`/`caution`/`go` override is APPLIED over the generated status color
     *    (absent key → generated; clearing the key reverts to generated). Safe because shape + icon +
     *    position carry the status meaning, not hue.
     *  - Simple: status was already collapsed to the text color by [Palette.generate]; overrides are
     *    IGNORED so Simple stays near-monochrome.
     *  - High-Contrast: status was already forced to the fixed RYG safety palette by [Palette.generate];
     *    overrides are IGNORED so the accessibility/CVD escape hatch CANNOT be defeated by a stored value.
     * The status keys are split out BEFORE the integer-pool `mapIndexed` (they are String-keyed via
     * [StatusSlot.key] and never reach `key.toInt()`).
     */
    fun build(
        gen: Palette.Generated,
        overrides: Map<Int, Color>,
        fs: Float,
        statusOverrides: Map<String, Color> = emptyMap(),
        accentOverride: Color? = null,
    ): ThemeTokens {
        val d = gen.dark
        val s = gen.surfaces
        val t = gen.theme
        val st = gen.status

        // --- status (D-03/D-04): apply user overrides ONLY in Colorful. In Simple/High-Contrast the
        // generator already collapsed status to text / forced the RYG palette, so an override must NOT
        // re-colorize it. Keyed by StatusSlot.key — split out here, never hitting key.toInt(). ---
        val colorful = !gen.simple && !gen.highContrast
        fun statusColor(slot: StatusSlot, generated: String): Color =
            (if (colorful) statusOverrides[slot.key] else null) ?: bake(generated)
        val stopColor = statusColor(StatusSlot.Stop, st.stop)
        val cautionColor = statusColor(StatusSlot.Caution, st.caution)
        val goColor = statusColor(StatusSlot.Go, st.go)

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
        val accentHex: String = accentOverride?.let { hexOf(it) } ?: t.primary
        val accent = bake(accentHex)
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
            // --- theme accent = the seed primary, or the user's accentOverride across ALL modes. ---
            accent = accent,
            accent2 = lShift(accentHex, if (d) 0.08 else -0.05),
            accentSoft = rgbaOf(accentHex, if (d) 0.16 else 0.12),
            accentLine = rgbaOf(accentHex, if (d) 0.55 else 0.50),
            accentGlow = rgbaOf(accentHex, if (d) 0.35 else 0.20),
            // --- status = 3 dedicated pool slots, MODE-GATED user overrides applied above (D-03/D-04).
            // dinghy's `heat` IS the caution color (D-13). The soft/glow alpha variants derive from the
            // RESOLVED status color (so an override drives its halo too), not the raw generated hex. ---
            heat = cautionColor,
            heatSoft = cautionColor.copy(alpha = if (d) 0.16f else 0.14f),
            heatGlow = cautionColor.copy(alpha = if (d) 0.38f else 0.22f),
            pool = pool,
            directional = Directional(
                temperature = accent, // D-07: accent leads (supersedes gen.directional.temperature)
                xy = dirXy,           // D-07: pool[0] (empty-pool → accent)
                z = dirZ,             // D-07: pool[1] (empty-pool → accent)
            ),
            go = goColor,
            goSoft = goColor.copy(alpha = if (d) 0.16f else 0.14f),
            goGlow = goColor.copy(alpha = if (d) 0.40f else 0.22f),
            stop = stopColor,
            stopSoft = stopColor.copy(alpha = if (d) 0.15f else 0.12f),
            stopGlow = stopColor.copy(alpha = if (d) 0.42f else 0.22f),
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
