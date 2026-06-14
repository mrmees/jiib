package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.LocalUnitDp
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * A row of increment-step selector tiles — the shared sketch-003 increment picker.
 *
 * Each [step] in [steps] renders as an [OutlinedControl] tile with label [formatStep].
 * The [activeStep] tile uses [Intent.Accent] to signal the current selection; all others
 * use [Intent.Neutral]. Tapping a tile calls [onSelect] with that step value.
 *
 * ## Height cap (UAT-5)
 * The row is exactly [uDp] tall — the unit-grid U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid].
 * Never hardcode a Dp height (use [uDp] instead).
 *
 * ## Intent law (D-20) + selection fill (2026-06-13 UAT)
 * Active increment = [Intent.Accent] outline + [works.mees.dinghy.theme.ThemeTokens.accentSoft]
 * FILL (the app-wide selected-state convention, matching [ListRow]); inactive = [Intent.Neutral]
 * outline + the default surface fill. The fill is what makes the active step unmistakable.
 *
 * ## 1U fill (R26 mechanism)
 * Provides [LocalUnitDp] so each tile's [OutlinedControl] floors at 1U and FILLS the `height(uDp)`
 * row, instead of sitting at the bare 64dp floor top-aligned in a taller row.
 *
 * ## Stability
 * [steps] is typed [ImmutableList] so Compose can skip recomposition when the list hasn't changed
 * (kotlinx-collections-immutable is already in the dependency graph).
 *
 * @param steps      the ordered set of step values to show as tiles.
 * @param activeStep the currently-selected step; must be a member of [steps].
 * @param onSelect   called when the user taps a tile, with the new step value.
 * @param uDp        one unit U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid];
 *                   used as the row's exact [height] (UAT-5: 1U cap).
 * @param modifier   caller-supplied modifier.
 */
@Composable
fun IncrementPicker(
    steps: ImmutableList<Double>,
    activeStep: Double,
    onSelect: (Double) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    CompositionLocalProvider(LocalUnitDp provides uDp) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(uDp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            steps.forEach { step ->
                val selected = step == activeStep
                OutlinedControl(
                    label = formatStep(step),
                    onClick = { onSelect(step) },
                    modifier = Modifier.weight(1f),
                    intent = if (selected) Intent.Accent else Intent.Neutral,
                    // Selected tile = accentSoft fill (the ListRow selected convention); unselected
                    // keeps the default surface fill.
                    fill = if (selected) t.accentSoft else null,
                )
            }
        }
    }
}

/**
 * Format an increment step for display in the [IncrementPicker].
 *
 * Rules:
 *  - Whole numbers (e.g. 5.0, 10.0) → "±N" with no decimal (e.g. "±5", "±10").
 *  - Decimal values → "±" + the plain decimal with trailing zeros stripped (no locale grouping).
 *    e.g. 0.001 → "±0.001", 0.05 → "±0.05", 0.050 → "±0.05".
 *
 * The ± prefix is the sketch-003 convention: each tile reads as a signed magnitude.
 *
 * This function is [internal] so it is testable from the host test source set without exposing
 * it to unrelated consumers.
 */
internal fun formatStep(step: Double): String =
    if (step == step.toLong().toDouble()) {
        "±${step.toLong()}"
    } else {
        "±${step.toBigDecimal().stripTrailingZeros().toPlainString()}"
    }
