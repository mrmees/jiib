package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

// ─────────────────────────────────────────────────────────────────────────────
// Pure host-testable helper — FillMeterTest asserts this directly
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Clamps [fraction] to the valid `fillMaxWidth` range `[0f, 1f]`.
 *
 * This is the pure extraction the composable uses internally; [FillMeterTest] asserts it:
 *   - `clampFraction(-0.5f) == 0f`  (negative data error → 0f, avoids `fillMaxWidth` crash)
 *   - `clampFraction(1.1f) == 1f`   (rounding-over-100% → 1f, avoids `fillMaxWidth` crash)
 *   - `clampFraction(0.74f) == 0.74f` (in-range → unchanged)
 *
 * A Compose `fillMaxWidth(fraction)` panics when `fraction < 0f` or `fraction > 1f`; this
 * guard prevents that crash in the [FillMeter] composable.
 */
internal fun clampFraction(fraction: Float): Float = fraction.coerceIn(0f, 1f)

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A read-only, left-anchored fill meter — shows a fraction of remaining capacity
 * (e.g. spool remaining weight as a proportion of the original weight).
 *
 * ## Structure
 *  - Pill-shaped track (height 6dp, `RoundedCornerShape(999.dp)`) in [ThemeTokens.surface3].
 *  - Left-anchored fill layer in [fillColor] (`fillMaxWidth(clamped)` over the track).
 *  - Optional [GeistMono] label below the track (e.g. "735 / 1000 g · 74%").
 *
 * ## THEME-01 data carve-out — [fillColor]
 * [fillColor] carries the **filament's actual color hex** (caller-supplied, e.g. from Spoolman)
 * OR a chrome token such as [ThemeTokens.accent]. It is **item data** in the spool context
 * and must **NOT** be brandTint-clamped. The caller decides whether to pass a raw filament hex
 * or a token; [FillMeter] renders it faithfully either way.
 *
 * ## No glow
 * No `blurMaskFilter` / drop-shadow is applied to the fill — on Adreno 320, per-pixel GPU
 * operations cost fill rate we cannot spare (23-RESEARCH §FillMeter "No glow on fill bar").
 * The flat colored fill is the correct visual treatment for the perf floor.
 *
 * ## Read-only
 * [FillMeter] is a **read-only** display primitive — it carries NO `pointerInput` or gesture
 * handling. For an interactive scrubber use [works.mees.dinghy.designsystem.ScrubberPage].
 *
 * @param fraction  fill fraction from the caller; clamped internally to `[0f, 1f]` via
 *                  [clampFraction] so an out-of-range value never crashes `fillMaxWidth`.
 * @param fillColor the fill bar color — data carve-out (filament hex or `t.accent`); never
 *                  a theme role resolved internally (caller supplies it).
 * @param modifier  caller-supplied modifier (e.g. `Modifier.fillMaxWidth()`).
 * @param label     optional Geist Mono label rendered below the track (e.g. "735 / 1000 g · 74%").
 *                  Omitted when blank.
 */
@Composable
fun FillMeter(
    fraction: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    label: String = "",
) {
    val t = LocalTokens.current
    val clamped = clampFraction(fraction)  // safe for fillMaxWidth; also the assertion seam

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Pill track + left-anchored fill layer
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
        ) {
            // Track layer — surface3 background
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(t.surface3),
            )
            // Fill layer — left-anchored, data fillColor, NO blurMaskFilter (Adreno-320 budget)
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .background(fillColor),
            )
        }

        // Optional label — GeistMono for tabular numerals (avoids digit-advance jitter)
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = t.text2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(15f, t.fs).sp,  // metadata floor — never bare .sp
            )
        }
    }
}
