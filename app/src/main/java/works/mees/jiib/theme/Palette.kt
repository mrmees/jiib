package works.mees.jiib.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure, host-testable palette generator — a faithful 1:1 port of
 * `../theme_theory/app/color.js` (the owner-reviewed oracle, D-01).
 *
 * ZERO Android / Compose imports (mirrors `state/PrinterStateReducer.kt`'s host-pure
 * discipline). All generation happens in OKLCH and the output is plain sRGB **String**
 * hexes — never a Compose `Color`. On Android API < 26 a non-sRGB (Oklab) Compose Color
 * SILENTLY renders as sRGB (see `BakedTokens.kt:6-30` header law), which is WRONG on the
 * Nexus 7 floor; so we emit baked sRGB hex strings only and let `TokenBridge` (15-03)
 * bake to `Color`.
 *
 * The math is ported verbatim — same 3×3 OKLab matrices, same iteration counts
 * (oklchToHex 20, maxChromaAt 18), same `cuspL` `L += 0.02` float accumulation — so the
 * output is bit-for-bit identical to the `color.js` oracle. `PaletteGoldenTest` /
 * `PaletteMathTest` assert that parity against the committed `color-golden.json` fixture.
 *
 * INTEGER-DIVISION LANDMINE: JS `hexToRgb` does `parseInt(byte,16) / 255` (float division).
 * Channels are normalized with `/ 255.0` (Double) EVERYWHERE — a naive Int `/ 255` would
 * collapse every channel below 0xFF to 0 (totally wrong colors).
 *
 * 15-03 contract: `hexToOklch` / `oklchToHex` (and `OklchValue`) are exposed `internal`
 * (NOT private) so the same-module `TokenBridge` can derive the in-between surface tiers
 * via those helpers + an lShift — mirroring `color.js`'s `Palette.util` export. We chose
 * the `internal`-helper exposure (not moving the tier derivation here) so `Palette` stays
 * a clean 1:1 of `color.js`.
 */
object Palette {

    // --- Status anchors, per scheme. Hues/L/C in OKLCH. ---
    // 'default' is the universal red/amber/green language.
    // 'cvd' rebuilds it on the blue-yellow axis with a lightness staircase so it
    // survives red-green color blindness; note "go" becomes blue.
    private data class StatusAnchor(val H: Double, val L: Double, val C: Double)

    private val SCHEMES: Map<String, Map<String, StatusAnchor>> = mapOf(
        "default" to mapOf(
            "stop" to StatusAnchor(H = 29.0, L = 0.60, C = 0.20),
            "caution" to StatusAnchor(H = 75.0, L = 0.80, C = 0.15),
            "go" to StatusAnchor(H = 148.0, L = 0.66, C = 0.16),
        ),
        "cvd" to mapOf(
            "stop" to StatusAnchor(H = 29.0, L = 0.55, C = 0.19),
            "caution" to StatusAnchor(H = 102.0, L = 0.85, C = 0.16),
            "go" to StatusAnchor(H = 255.0, L = 0.64, C = 0.16),
        ),
    )

    /** {L,C,H} OKLCH triple. `internal` so 15-03's TokenBridge can derive tiers. */
    internal data class OklchValue(val L: Double, val C: Double, val H: Double)

    // ---------- sRGB <-> OKLCH ----------
    private fun clamp01(x: Double): Double = x.coerceIn(0.0, 1.0)

    private fun sToL(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun lToS(c: Double): Double =
        if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

    private fun hexToRgb(h: String): DoubleArray {
        val s = h.replace("#", "")
        // `/ 255.0` (Double) — NOT Int division. A naive `/ 255` collapses every
        // channel < 0xFF to 0 → totally wrong colors (CONFIRMED Codex landmine).
        return doubleArrayOf(
            s.substring(0, 2).toInt(16) / 255.0,
            s.substring(2, 4).toInt(16) / 255.0,
            s.substring(4, 6).toInt(16) / 255.0,
        )
    }

    private fun rgbToHex(r: Double, g: Double, b: Double): String {
        // round(clamp01(x) * 255) → 2-pad lowercase hex to match the oracle (#3c75fb).
        fun f(x: Double): String =
            (clamp01(x) * 255.0).roundToInt().toString(16).padStart(2, '0')
        return "#" + f(r) + f(g) + f(b)
    }

    // linear-sRGB -> OKLab (3×3 matrices copied VERBATIM from color.js).
    private fun linToLab(r: Double, g: Double, b: Double): DoubleArray {
        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
        val lr = cbrt(l)
        val mr = cbrt(m)
        val sr = cbrt(s)
        return doubleArrayOf(
            0.2104542553 * lr + 0.7936177850 * mr - 0.0040720468 * sr,
            1.9779984951 * lr - 2.4285922050 * mr + 0.4505937099 * sr,
            0.0259040371 * lr + 0.7827717662 * mr - 0.8086757660 * sr,
        )
    }

    // OKLab -> linear-sRGB (3×3 matrices copied VERBATIM from color.js).
    private fun labToLin(L: Double, a: Double, b: Double): DoubleArray {
        val lr = L + 0.3963377774 * a + 0.2158037573 * b
        val mr = L - 0.1055613458 * a - 0.0638541728 * b
        val sr = L - 0.0894841775 * a - 1.2914855480 * b
        val l = lr * lr * lr
        val m = mr * mr * mr
        val s = sr * sr * sr
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    /** hex → {L,C,H} (H wrapped to 0..360). `internal` for the 15-03 bridge. */
    internal fun hexToOklch(hex: String): OklchValue {
        val rgb = hexToRgb(hex).map { sToL(it) }
        val lab = linToLab(rgb[0], rgb[1], rgb[2])
        val C = hypot(lab[1], lab[2])
        var H = atan2(lab[2], lab[1]) * 180.0 / PI
        if (H < 0) H += 360.0
        return OklchValue(L = lab[0], C = C, H = H)
    }

    private fun inGamut(L: Double, C: Double, H: Double): Boolean {
        val a = C * cos(H * PI / 180.0)
        val b = C * sin(H * PI / 180.0)
        val lin = labToLin(L, a, b)
        return lin.all { it >= -0.0002 && it <= 1.0002 }
    }

    /** OKLCH -> sRGB hex, reducing chroma (20-iter binary search) until in gamut. `internal` for the bridge. */
    internal fun oklchToHex(L: Double, C: Double, H: Double): String {
        var c = C
        if (!inGamut(L, c, H)) {
            var lo = 0.0
            var hi = C
            for (i in 0 until 20) {
                val mid = (lo + hi) / 2.0
                if (inGamut(L, mid, H)) lo = mid else hi = mid
            }
            c = lo
        }
        val a = c * cos(H * PI / 180.0)
        val b = c * sin(H * PI / 180.0)
        val lin = labToLin(L, a, b).map { lToS(it) }
        return rgbToHex(lin[0], lin[1], lin[2])
    }

    private fun grayHex(L: Double): String = oklchToHex(L, 0.0, 0.0)

    // Largest in-gamut chroma for (L, H) — 18-iter binary search.
    private fun maxChromaAt(L: Double, H: Double): Double {
        var lo = 0.0
        var hi = 0.4
        for (i in 0 until 18) {
            val mid = (lo + hi) / 2.0
            if (inGamut(L, mid, H)) lo = mid else hi = mid
        }
        return lo
    }

    // The lightness where hue H peaks in chroma (its sRGB "cusp"). KEEP the literal
    // `L += 0.02` accumulation — float drift must match the IEEE-754 the oracle uses;
    // do NOT refactor to an integer-step + multiply.
    private fun cuspL(H: Double): Double {
        var bestL = 0.6
        var bestC = -1.0
        var L = 0.30
        while (L <= 0.92) {
            val c = maxChromaAt(L, H)
            if (c > bestC) {
                bestC = c
                bestL = L
            }
            L += 0.02
        }
        return bestL
    }

    // ---------- reserved zones + spread ----------
    private fun hueDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    private fun inSpan(h: Double, lo: Double, hi: Double): Boolean =
        if (lo <= hi) (h >= lo && h <= hi) else (h >= lo || h <= hi)

    // Whole warm arc (red through caution) plus the "go" band are reserved.
    // (Dead path when statusFromPool=true — the shipped config — but ported for parity.)
    private fun isReserved(h: Double, band: Double, scheme: String): Boolean {
        val S = SCHEMES.getValue(scheme)
        val lo = (S.getValue("stop").H - band + 360.0) % 360.0
        val hi = (S.getValue("caution").H + band) % 360.0
        if (inSpan(h, lo, hi)) return true
        if (hueDiff(h, S.getValue("go").H) < band) return true
        return false
    }

    private data class Run(val s: Double, val l: Double)

    // Spread n hues across the allowed arc (step 0.5°), dodging reserved zones.
    // noReserve=true skips reservation → covers the FULL wheel (statusFromPool path).
    private fun spreadHues(
        n: Int,
        band: Double,
        seedH: Double,
        scheme: String,
        noReserve: Boolean,
    ): List<Double> {
        val step = 0.5
        val runs = mutableListOf<Run>()
        var cs: Double? = null
        var cl = 0.0
        var d = 0.0
        while (d < 360.0) {
            val h = (seedH + d) % 360.0
            if (noReserve || !isReserved(h, band, scheme)) {
                if (cs == null) {
                    cs = d
                    cl = 0.0
                }
                cl += step
            } else if (cs != null) {
                runs.add(Run(cs, cl))
                cs = null
            }
            d += step
        }
        if (cs != null) runs.add(Run(cs, cl))
        val total = runs.fold(0.0) { a, r -> a + r.l }.let { if (it == 0.0) 1.0 else it }
        val out = mutableListOf<Double>()
        for (k in 0 until n) {
            val target = (k.toDouble() / n) * total
            var acc = 0.0
            var off = 0.0
            for (j in runs.indices) {
                if (target <= acc + runs[j].l) {
                    off = runs[j].s + (target - acc)
                    break
                }
                acc += runs[j].l
            }
            out.add((seedH + off) % 360.0)
        }
        return out
    }

    // Minimum hue gap between adjacent entries of a hue list (degrees).
    private fun minHueGap(hues: List<Double>): Double {
        if (hues.size < 2) return 360.0
        val s = hues.sorted()
        var m = 360.0
        for (i in 1 until s.size) m = minOf(m, s[i] - s[i - 1])
        return minOf(m, 360.0 - (s[s.size - 1] - s[0]))
    }

    // Reorder hues farthest-point: each next entry maximises its minimum distance to all
    // already-ranked ones, so any PREFIX is the most mutually-separated subset of that length.
    private fun rankByContrast(hues: List<Double>): List<Double> {
        if (hues.size <= 2) return hues.toList()
        val rem = hues.toMutableList()
        val ranked = mutableListOf(rem.removeAt(0))
        while (rem.isNotEmpty()) {
            var bi = 0
            var bd = -1.0
            for (i in rem.indices) {
                var md = 360.0
                for (j in ranked.indices) md = minOf(md, hueDiff(rem[i], ranked[j]))
                if (md > bd) {
                    bd = md
                    bi = i
                }
            }
            ranked.add(rem.removeAt(bi))
        }
        return ranked
    }

    // ---------- output value types (String hexes, oracle-comparable; NOT Compose Color) ----------

    /** Surface neutrals (pure-neutral, D-16). */
    data class Surfaces(
        val bg: String,
        val surface: String,
        val divider: String,
        val text: String,
        val muted: String,
        val disabled: String,
        val textOnFill: String,
    )

    /** Accent pair. */
    data class Theme(val primary: String, val secondary: String)

    /** Status trio (stop/caution/go). */
    data class Status(val stop: String, val caution: String, val go: String)

    /** Directional standards (heaters/xy/z). */
    data class Directional(val temperature: String, val xy: String, val z: String)

    /**
     * The full generator output — a plain value type of STRING hexes, a faithful 1:1 of
     * `color.js`'s return object. NOT a `ThemeTokens` (the bridge bakes to Color); keeping
     * it 1:1 keeps the golden tests host-pure.
     */
    data class Generated(
        val dark: Boolean,
        val scheme: String,
        val surfaces: Surfaces,
        val theme: Theme,
        val status: Status,
        val statusFromPool: Boolean,
        val statusColors: List<Pair<String, String>>?,
        val simple: Boolean,
        val highContrast: Boolean,
        val pool: List<String>,
        val poolHues: List<Double>,
        val poolRoles: List<String?>,
        val directional: Directional,
        val minHueGap: Int,
        val poolShift: Int,
    )

    // ---------- the one call you make ----------
    fun generate(
        seedHex: String,
        dark: Boolean,
        maxItems: Int,
        poolShift: Int = 0,
        statusFromPool: Boolean = true,
        simple: Boolean = false,
        highContrast: Boolean = false,
        scheme: String = "default",
        band: Double = 24.0,
    ): Generated {
        val resolvedScheme = if (SCHEMES.containsKey(scheme)) scheme else "default"
        val items = maxOf(1, maxItems)
        val mono = simple || highContrast

        val seed = hexToOklch(seedHex)
        // accent lightness: the hue's vivid (cusp) lightness, clamped into a contrast-safe band.
        val cL = cuspL(seed.H)
        val accentL =
            if (dark) cL.coerceAtLeast(0.60).coerceAtMost(0.80)
            else cL.coerceAtLeast(0.42).coerceAtMost(0.62)

        fun stat(k: String): String {
            val a = SCHEMES.getValue(resolvedScheme).getValue(k)
            return oklchToHex(a.L, a.C, a.H)
        }
        // a data hue rendered for the active background (drop lightness a touch on light bg).
        fun chan(H: Double, L: Double): String =
            oklchToHex(if (dark) L else (L - 0.07).coerceAtLeast(0.32), 0.15, H)

        val STATUSN = if (statusFromPool) 3 else 0
        val poolN = maxOf(items, 3) + STATUSN
        val ranked = rankByContrast(
            spreadHues(poolN, band, seed.H + poolShift, resolvedScheme, statusFromPool),
        )

        val levels = if (items <= 5) listOf(0.68, 0.52) else listOf(0.74, 0.62, 0.50)
        fun poolColorAt(k: Int): String = chan(ranked[k], levels[k % levels.size])
        val poolHues = ranked.subList(0, items).toList()
        var pool = poolHues.indices.map { poolColorAt(it) }

        // directional = the three highest-contrast hues (ranked[0..2]) re-tagged by warmth.
        fun warmth(h: Double): Double = cos((h - 345.0) * PI / 180.0)
        val top3i = listOf(0, 1, 2).sortedWith(compareByDescending { warmth(ranked[it]) })
        val roleNames = listOf("temperature", "xy", "z")
        fun roleSlot(role: String): String = poolColorAt(top3i[roleNames.indexOf(role)])
        val poolRoles: List<String?> = poolHues.indices.map { pi ->
            val r = top3i.indexOf(pi)
            if (r >= 0) roleNames[r] else null
        }

        // status: reserved-language (fixed stoplight) OR three DEDICATED pool slots.
        var status: Status
        var statusColors: List<Pair<String, String>>?
        if (statusFromPool) {
            val base = poolN - 3
            val trioH = listOf(0, 1, 2).map { ranked[base + it] }
            val trioHex = listOf(0, 1, 2).map { poolColorAt(base + it) }
            val ord = listOf(0, 1, 2).sortedWith(compareByDescending { warmth(trioH[it]) })
            status = Status(
                stop = trioHex[ord[0]],
                caution = trioHex[ord[1]],
                go = trioHex[ord[2]],
            )
            statusColors = listOf(
                "stop" to status.stop,
                "caution" to status.caution,
                "go" to status.go,
            )
        } else {
            status = Status(stop = stat("stop"), caution = stat("caution"), go = stat("go"))
            statusColors = null
        }

        // 'simple'/'highContrast': collapse pool + directional to the text colour, keep ONLY accent.
        val txt = grayHex(if (dark) 0.93 else 0.17)
        var directional =
            if (mono) Directional(temperature = txt, xy = txt, z = txt)
            else Directional(
                temperature = roleSlot("temperature"),
                xy = roleSlot("xy"),
                z = roleSlot("z"),
            )
        if (mono) {
            pool = pool.map { txt }
            if (highContrast) {
                fun ryg(k: String): String {
                    val a = SCHEMES.getValue("default").getValue(k)
                    return oklchToHex(a.L, a.C, a.H)
                }
                status = Status(stop = ryg("stop"), caution = ryg("caution"), go = ryg("go"))
                statusColors = listOf(
                    "stop" to status.stop,
                    "caution" to status.caution,
                    "go" to status.go,
                )
            } else {
                status = Status(stop = txt, caution = txt, go = txt)
                if (statusColors != null) {
                    statusColors = statusColors.map { it.first to txt }
                }
            }
        }

        return Generated(
            dark = dark,
            scheme = resolvedScheme,
            surfaces = Surfaces(
                bg = grayHex(if (dark) 0.15 else 0.975),
                surface = grayHex(if (dark) 0.21 else 0.93),
                divider = grayHex(if (dark) 0.32 else 0.82),
                text = grayHex(if (dark) 0.93 else 0.17),
                muted = grayHex(if (dark) 0.60 else 0.46),
                disabled = grayHex(if (dark) 0.40 else 0.72),
                textOnFill = grayHex(0.13),
            ),
            theme = Theme(
                primary = oklchToHex(accentL, maxOf(seed.C, 0.10), seed.H),
                secondary = oklchToHex(
                    if (dark) accentL - 0.12 else accentL + 0.06,
                    maxOf(seed.C * 0.55, 0.05),
                    seed.H,
                ),
            ),
            status = status,
            statusFromPool = statusFromPool,
            statusColors = statusColors,
            simple = simple,
            highContrast = highContrast,
            pool = pool,
            poolHues = poolHues,
            poolRoles = poolRoles,
            directional = directional,
            minHueGap = minHueGap(poolHues).roundToInt(),
            poolShift = poolShift,
        )
    }
}
