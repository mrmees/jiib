package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl

/**
 * A row of increment-step selector tiles — the shared sketch-003 increment picker.
 *
 * Each [step] in [steps] renders as an [OutlinedControl] tile with label [formatStep].
 * The [activeStep] tile uses [Intent.Accent] to signal the current selection; all others
 * use [Intent.Neutral]. Tapping a tile calls [onSelect] with that step value.
 *
 * ## Touch floor
 * The row's minimum height is [uDp] — the unit-grid U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid].
 * Never hardcode a Dp touch floor (use [uDp] instead).
 *
 * ## Intent law (D-20)
 * Active increment = [Intent.Accent]; inactive = [Intent.Neutral]. This is the sketch-003 spec.
 *
 * ## Stability
 * [steps] is typed [ImmutableList] so Compose can skip recomposition when the list hasn't changed
 * (kotlinx-collections-immutable is already in the dependency graph).
 *
 * @param steps      the ordered set of step values to show as tiles.
 * @param activeStep the currently-selected step; must be a member of [steps].
 * @param onSelect   called when the user taps a tile, with the new step value.
 * @param uDp        one unit U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid];
 *                   used as the row's [heightIn] minimum.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = uDp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        steps.forEach { step ->
            OutlinedControl(
                label = formatStep(step),
                onClick = { onSelect(step) },
                modifier = Modifier.weight(1f),
                intent = if (step == activeStep) Intent.Accent else Intent.Neutral,
            )
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
