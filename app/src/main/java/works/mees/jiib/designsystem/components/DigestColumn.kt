package works.mees.jiib.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.fsSp

/** One row of a Focus digest. Line rows scale (LAW 5); Custom rows (meters, chips) hold their U height. */
sealed interface DigestRow {
    data class Line(
        val label: String,
        val value: String,
        val icon: JiibIcon? = null,
        val iconTint: Color? = null,
        val emphasis: DigestEmphasis = DigestEmphasis.Standard,
        val valueColor: Color? = null,
        val labelColor: Color? = null,
    ) : DigestRow

    class Custom(val heightU: Float = 1f, val content: @Composable () -> Unit) : DigestRow
}

sealed interface DigestFit {
    data object Natural : DigestFit
    data class Shrunk(val scale: Float) : DigestFit
    data object Scroll : DigestFit
}

/**
 * LAW 5, the pure core: walk scale 1.0 → minScale in stepDown decrements; first scale whose
 * modeled total height fits wins. Natural if 1.0 fits, Scroll if nothing fits.
 * totalHeightAt models row heights at a given scale (clamping per-row floors is the caller's model).
 *
 * Each candidate is computed as (1.0 - i*stepDown) in Double then converted to Float, avoiding
 * the floating-point accumulation error of repeated Float subtraction (which can strand the
 * stepping slightly above exact multiples like 0.8f or 0.6f and miss them).
 *
 * The +0.5f fit tolerance forgives sub-pixel float rounding in the height model (e.g. `0.6f` is
 * not exactly 0.6 in IEEE 754, so `400f * 0.6f` can be 0.000015px over the expected value).
 */
fun digestFit(
    availablePx: Float,
    totalHeightAt: (Float) -> Float,
    minScale: Float,
    stepDown: Float = 0.05f,
): DigestFit {
    val fits = { s: Float -> totalHeightAt(s) <= availablePx + 0.5f }
    if (fits(1f)) return DigestFit.Natural
    var step = 1
    while (step < 10_000) {
        val scale = (1.0 - step * stepDown.toDouble()).toFloat().coerceAtLeast(minScale)
        if (fits(scale)) return DigestFit.Shrunk(scale)
        if (scale == minScale) break   // tested at the floor — nothing smaller to try
        step++
    }
    return DigestFit.Scroll
}

/** Line-height factor for modeling a text row's height from its font sp. */
internal const val DIGEST_LINE_HEIGHT_FACTOR = 1.5f

/**
 * A digest block that degrades deterministically: even rhythm when roomy → text scales toward
 * the 15sp floor when tight → scrolls as LAST resort. Never silently clipped.
 */
@Composable
fun DigestColumn(
    rows: List<DigestRow>,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val t = LocalTokens.current
    val uDp = LocalUnitDp.current ?: 64.dp
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val availablePx = constraints.maxHeight.toFloat()
        val gapPx = with(density) { 8.dp.toPx() }
        val uPx = with(density) { uDp.toPx() }

        // Model: line rows = tallest of (label, value, icon 0.6U) at a given scale; custom rows fixed.
        val totalHeightAt: (Float) -> Float = { scale ->
            val rowsPx = rows.sumOf { row ->
                when (row) {
                    is DigestRow.Line -> {
                        val (labelRole, valueRole) = digestLineRoles(row.emphasis)
                        val maxSp = maxOf(
                            digestScaledSp(labelRole.baseSp, scale),
                            digestScaledSp(valueRole.baseSp, scale),
                        )
                        val textPx = with(density) { (fsSp(maxSp, t.fs) * DIGEST_LINE_HEIGHT_FACTOR).sp.toPx() }
                        val iconPx = if (row.icon != null) uPx * 0.6f else 0f
                        maxOf(textPx, iconPx).toDouble()
                    }
                    is DigestRow.Custom -> (uPx * row.heightU).toDouble()
                }
            }.toFloat()
            rowsPx + gapPx * (rows.size - 1).coerceAtLeast(0)
        }

        // Global floor: the scale below which every Line row is already clamped at 15sp base.
        val largestBase = rows.filterIsInstance<DigestRow.Line>()
            .maxOfOrNull { row ->
                val (l, v) = digestLineRoles(row.emphasis)
                maxOf(l.baseSp, v.baseSp)
            } ?: 15f
        val minScale = (15f / largestBase).coerceAtMost(1f)

        val fit = if (constraints.hasBoundedHeight) digestFit(availablePx, totalHeightAt, minScale) else DigestFit.Natural
        val scale = when (fit) {
            DigestFit.Natural -> 1f
            is DigestFit.Shrunk -> fit.scale
            DigestFit.Scroll -> minScale
        }

        val columnMod = Modifier
            .fillMaxWidth()
            .then(if (fit == DigestFit.Scroll) Modifier.height(this.maxHeight).verticalScroll(rememberScrollState()) else Modifier)

        Column(
            modifier = columnMod,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = horizontalAlignment,
        ) {
            rows.forEach { row ->
                when (row) {
                    is DigestRow.Line -> DigestLine(
                        label = row.label,
                        value = row.value,
                        icon = row.icon,
                        iconTint = row.iconTint,
                        emphasis = row.emphasis,
                        valueColor = row.valueColor,
                        labelColor = row.labelColor,
                        scale = scale,
                    )
                    is DigestRow.Custom -> Box(Modifier.fillMaxWidth().height(uDp * row.heightU)) { row.content() }
                }
            }
        }
    }
}
