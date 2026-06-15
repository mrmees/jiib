package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.LocalUnitDp
import works.mees.dinghy.designsystem.layout.controlHeight
import works.mees.dinghy.designsystem.layout.gapS

// ─────────────────────────────────────────────────────────────────────────────
// Pure helpers (host-testable — no Compose runtime)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The registered decrement glyph token for a [StepperRow]'s `[−]` tile (control baseline audit,
 * Phase 4): always [DinghyIcons.Decrease] — never a literal `"−"` text label. Pure so the
 * "icon path, not text" contract is host-testable.
 */
internal fun stepperDecreaseIcon(): DinghyIcon = DinghyIcons.Decrease

/**
 * The registered increment glyph token for a [StepperRow]'s `[+]` tile: always
 * [DinghyIcons.Increase] — never a literal `"+"` text label.
 */
internal fun stepperIncreaseIcon(): DinghyIcon = DinghyIcons.Increase

/**
 * The dim alpha applied to a [StepperRow] tile that is either truly disabled (`enabled = false`)
 * or busy (`busy = true`). Matches the AdjusterPanel / 25-03 WR-07 convention (alpha 0.38 +
 * `semantics { disabled() }` for true-disablement; alpha-only for busy so taps still accumulate).
 */
internal const val STEPPER_DIM_ALPHA = 0.38f

// ─────────────────────────────────────────────────────────────────────────────
// StepperRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * THE canonical `[−] [center?] [+]` stepper row (control baseline audit, Phase 4) — the shape shared
 * by every discrete ± adjust in the app. Extracted from the hand-rolled ± `Row`s in [AdjusterPanel]
 * (Zone-2 value-adjust), [Scrubber] (the discrete ± row), and the Move Microstep step-size cycler +
 * jog pair. Renders the ± via the registered [DinghyIcons.Decrease] / [DinghyIcons.Increase] icon
 * tokens (icon path — NEVER a literal `"−"`/`"+"` text label; icon-registry law).
 *
 * ## Anatomy
 *  - A `Row` capped at exactly 1U (`height(uDp)`) that PROVIDES [LocalUnitDp] so each
 *    [OutlinedControl] tile floors at 1U and FILLS the row (R26 mechanism) instead of sitting at the
 *    bare 64dp floor, top-aligned. This also drives the 0.6U glyph sizing for the ± icons.
 *  - The `[−]` tile · an optional [center] slot · the `[+]` tile, each `weight(1f)`, spaced by
 *    [spacing] (default [gapS] = U×0.125 = 8dp at U=64; pass [gapM] for the 12dp variant).
 *  - [center] = null → a bare ±-pair (jog). [center] non-null → a `[−][value][+]` cycler/value cell;
 *    the slot is centered inside its `weight(1f)` cell.
 *
 * ## Enabled vs busy (the AdjusterPanel convention, preserved)
 *  - **TRUE disablement** ([enabled] = false): the ± tiles get NO clickable (R10 — via
 *    `OutlinedControl(enabled = false)`), `alpha [STEPPER_DIM_ALPHA]`, and `semantics { disabled() }`
 *    so TalkBack stops announcing them. The [center] slot is NOT disabled (it's a display/value).
 *  - **BUSY dim** ([busy] = true, while enabled): the ± tiles dim to `alpha [STEPPER_DIM_ALPHA]` but
 *    STAY clickable (no `disabled()` semantics) — taps during an in-flight commit accumulate.
 *
 * @param onDecrement called on the `[−]` tap; the caller pre-clamps (this row has no value knowledge).
 * @param onIncrement called on the `[+]` tap.
 * @param uDp         one unit U — caps the row at 1U and drives 1U fill + 0.6U glyph sizing.
 * @param modifier    caller-supplied modifier (applied to the row).
 * @param center      optional center slot (the value display for a cycler/value cell); null = bare ±-pair.
 * @param intent      the ± tiles' [Intent] (Accent for value-adjust; Go for Move jog — R19).
 * @param enabled     TRUE-disablement gate for the ± tiles (R10). Default true.
 * @param busy        in-flight commit dim: tiles dim but stay tappable. Default false.
 * @param spacing     inter-element gap. Default [gapS] (8dp at U=64); Scrubber passes [gapM] (12dp).
 * @param decreaseContentDescription TalkBack label for the `[−]` glyph (a11y law).
 * @param increaseContentDescription TalkBack label for the `[+]` glyph.
 */
@Composable
fun StepperRow(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    center: (@Composable () -> Unit)? = null,
    intent: Intent = Intent.Accent,
    enabled: Boolean = true,
    busy: Boolean = false,
    spacing: Dp = gapS(uDp),
    decreaseContentDescription: String? = null,
    increaseContentDescription: String? = null,
) {
    // R10: true disablement dims + announces disabled; BUSY dims but stays clickable so taps
    // landing during an in-flight commit accumulate (the AdjusterPanel convention, verbatim).
    val disabledModifier =
        if (!enabled) Modifier.alpha(STEPPER_DIM_ALPHA).semantics { disabled() } else Modifier
    val busyDimModifier =
        if (enabled && busy) Modifier.alpha(STEPPER_DIM_ALPHA) else Modifier

    // Default a11y labels match the app-wide ± content descriptions when the caller omits them.
    val decreaseCd = decreaseContentDescription ?: stringResource(R.string.cd_decrement)
    val increaseCd = increaseContentDescription ?: stringResource(R.string.cd_increment)

    CompositionLocalProvider(LocalUnitDp provides uDp) {
        Row(
            modifier = modifier.fillMaxWidth().controlHeight(uDp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedControl(
                label = "",
                onClick = onDecrement,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .then(disabledModifier)
                    .then(busyDimModifier),
                intent = intent,
                icon = DinghyIcons.Decrease,
                contentDescription = decreaseCd,
            )
            if (center != null) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    center()
                }
            }
            OutlinedControl(
                label = "",
                onClick = onIncrement,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .then(disabledModifier)
                    .then(busyDimModifier),
                intent = intent,
                icon = DinghyIcons.Increase,
                contentDescription = increaseCd,
            )
        }
    }
}
