package works.mees.jiib.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import works.mees.jiib.R
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.ui.finetune.DASH
import works.mees.jiib.ui.finetune.fmtValue
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The 2-zone adjuster Focus surface — the shared sketch-003 adjustment archetype.
 *
 * Every numeric-adjustment screen (Fine-Tune, Temperature, Outputs) composes this as its
 * Focus content. The two zones are distributed via `Arrangement.SpaceBetween`. The param's
 * identity (icon + name) and the revert affordance now live in the enclosing [FocusFrame]
 * header — this panel is purely value + controls:
 *
 * **Zone 1 — Value (centered, absorbs slack):** Big [GeistMono] value text at 48sp (scaled by [fsSp]),
 * with the "was {baseline}{unit}" line [works.mees.jiib.theme.ThemeTokens.text3] **stacked directly
 * below it** when [shouldShowBaseline] is true (owner UAT 2026-06-13 — some units, e.g. mm/s², make the
 * old inline form too long to share the value's line; the weight(1f) value zone absorbs the slack).
 *
 * **Zone 2 — Controls (bottom-docked):** A decrement/increment [OutlinedControl] stepper Row
 * (icon tokens [works.mees.jiib.designsystem.icons.DinghyIcons.Decrease] / [works.mees.jiib.designsystem.icons.DinghyIcons.Increase],
 * [Intent.Accent], capped at 1U via `height(uDp)`, `enabled = [enabled] && [value] != null`)
 * above the caller-provided `[incrementPicker]` slot.
 *
 * ## Enabled state vs busy dim (quick-rmr)
 * TRUE disablement: when [enabled] is false or [value] is null (unreported / DASH) the stepper
 * tiles get no clickable, no ripple, and `semantics { disabled() }` (R10 law) — unchanged.
 *
 * BUSY dim: [busy] DIMS the decrement/increment tiles (alpha 0.38) but keeps them CLICKABLE with
 * semantics enabled — taps landing during an in-flight trailing commit must accumulate into the
 * next working value, never be swallowed.
 *
 * @param value          the live numeric value; null renders [DASH] and disables the stepper.
 * @param unit           the unit suffix appended to the displayed value (e.g. "%", "mm/s").
 * @param baseline       the value captured on entry (for the inline "was X" span).
 * @param decimals       decimal precision used both for display ([fmtValue]) and for the
 *                       [shouldShowBaseline] rounding comparison.
 * @param onDecrement    called on decrement tap; caller must pre-clamp + [works.mees.jiib.ui.finetune.FineTuneHolder.markPending]
 *                       (D-22 clamp authority invariant — this component has no knowledge of limits).
 * @param onIncrement    called on increment tap; same clamp requirement.
 * @param enabled        TRUE-disablement gate; when false both stepper tiles are inert (no clickable).
 * @param busy           in-flight commit dim (quick-rmr): the stepper tiles dim to alpha 0.38 but STAY
 *                       tappable (taps accumulate during the commit).
 *                       Default false keeps previews and legacy call sites unchanged.
 * @param incrementPicker caller-provided [IncrementPicker] slot (the step-set selector row).
 * @param rejectTick     R10 (26.5-03) rejection-feedback tick: the caller increments this when the
 *                       dispatcher reports a busy/debounce rejection for THIS panel's dispatch key
 *                       ([works.mees.jiib.command.CommandDispatcher.rejectedKey]). Each change
 *                       (except the initial 0) triggers a brief ONE-SHOT flash of the hero value —
 *                       "heard you, still settling" — never a looping animation (Adreno-320 motion
 *                       law). Default 0 keeps previews and legacy call sites flash-free.
 * @param uDp            the unit U from [works.mees.jiib.designsystem.layout.rememberUnitGrid];
 *                       used to cap the stepper Row at 1U.
 * @param modifier       caller-supplied modifier.
 */
@Composable
fun AdjusterPanel(
    value: Double?,
    unit: String,
    baseline: Double?,
    decimals: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    enabled: Boolean,
    incrementPicker: @Composable () -> Unit,
    uDp: Dp = 64.dp,
    rejectTick: Long = 0L,
    busy: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // WR-07 (26-rev): the busy-lock must DIM as well as inert (the P17 "group dims + inert" UX).
    // R10 (26.5-03): true disablement now goes through OutlinedControl's `enabled` param (no click
    // modifier installed at all — no ripple, no swallowed tap; Part 5 cause #1). The 25-03 dim
    // convention (alpha 0.38 + semantics disabled, BookmarkedMacrosScreen precedent) is KEPT on the
    // stepper tiles and Reset so the lock stays visible and TalkBack stops announcing them.
    val controlsEnabled = enabled && value != null
    val disabledModifier =
        if (!controlsEnabled) Modifier.alpha(0.38f).semantics { disabled() } else Modifier
    // quick-rmr busy dim: dimmed but TAPPABLE — no semantics{disabled()}, clickable stays installed
    // so taps during the in-flight commit accumulate into the next working value.
    val busyDimModifier =
        if (controlsEnabled && busy) Modifier.alpha(0.38f) else Modifier

    // R10 (26.5-03): one-shot rejection flash — the hero value briefly tints to the warn token
    // (heat) and settles back to text over REJECT_FLASH_MS. lerp between two LocalTokens roles
    // keeps it token-only (THEME-01) and theme-reactive; Animatable makes it strictly ONE-SHOT
    // (no looping transition — Adreno-320 motion law, D-13).
    val rejectFlash = remember { Animatable(0f) }
    LaunchedEffect(rejectTick) {
        if (rejectTick != 0L) {
            rejectFlash.snapTo(1f)
            rejectFlash.animateTo(0f, animationSpec = tween(durationMillis = REJECT_FLASH_MS))
        }
    }
    val valueColor = lerp(t.text, t.heat, rejectFlash.value)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Zone 1 — Value + inline "was X" baseline (centered, absorbs slack)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Hero value + unit. The unit is rendered smaller + lighter than the number and with
                // NO separating space (owner UAT 2026-06-13) — a compact "120mm/s²" form that keeps
                // the value on one line without a shrink-to-fit rule. Numeric part carries the R10
                // rejection flash (valueColor); the unit stays the calmer text2.
                // alignByBaseline (NOT verticalAlignment=Bottom): align the text BASELINES so the
                // smaller unit sits on the same line as the value instead of dropping to a subscript
                // (box-bottom alignment looked like a subscript — owner UAT 2026-06-13).
                Row {
                    Text(
                        text = if (value == null) DASH else fmtValue(value, decimals),
                        style = DinghyType.focusHero.toTextStyle(t),
                        color = valueColor,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (value != null && unit.isNotBlank()) {
                        Text(
                            text = unit.trim(),
                            style = DinghyType.statValue.toTextStyle(t),
                            color = t.text2,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                // "was X" STACKED directly below the value (owner UAT 2026-06-13): some units make
                // the inline form too long to share the line. The weight(1f) value zone absorbs the
                // extra line within the Focus budget. Unit space stripped to match the hero value.
                if (shouldShowBaseline(value, baseline, decimals)) {
                    Text(
                        text = stringResource(
                            R.string.adjuster_was,
                            fmtValue(baseline!!, decimals) + unit.trim(),
                        ),
                        color = t.text3,
                        style = DinghyType.dataMeta.toTextStyle(t),
                    )
                }
            }
        }

        // Zone 2 — Stepper + IncrementPicker (bottom-docked)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The ± stepper is the canonical [StepperRow] (Phase 4): 1U row, LocalUnitDp-provided so
            // the tiles floor at 1U and FILL the row (R26), Decrease/Increase icon tokens, Accent
            // intent. `enabled`/`busy` pass straight through — TRUE disablement dims + announces
            // disabled, busy dims but stays tappable (taps accumulate). Spacing = gapS (8dp at U=64,
            // pixel-identical to the prior hand-rolled Row).
            StepperRow(
                onDecrement = onDecrement,
                onIncrement = onIncrement,
                uDp = uDp,
                intent = Intent.Accent,
                enabled = controlsEnabled,
                busy = busy,
            )
            incrementPicker()
        }
    }
}

/** R10: duration of the one-shot rejection flash (ms) — brief, never looping (Adreno-320 law). */
private const val REJECT_FLASH_MS = 200

/**
 * Pure predicate: whether to show the inline "was {baseline}" label next to the adjuster value.
 *
 * Returns `false` when either [value] or [baseline] is null (no baseline to compare against).
 * Returns `true` only when both are non-null AND their values differ when rounded to
 * [decimalPrecision] decimal places (prevents floating-point noise from toggling the label).
 *
 * Example (3 decimal places):
 *  - `shouldShowBaseline(0.2001, 0.200, 3)` → `false` (0.200 == 0.200 after rounding)
 *  - `shouldShowBaseline(0.205, 0.200, 3)`  → `true`  (0.205 != 0.200 after rounding)
 *
 * This function is [internal] so it is testable from the host test source set.
 */
internal fun shouldShowBaseline(value: Double?, baseline: Double?, decimalPrecision: Int): Boolean {
    if (value == null || baseline == null) return false
    val factor = 10.0.pow(decimalPrecision)
    return (value * factor).roundToInt() != (baseline * factor).roundToInt()
}
