package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.finetune.DASH
import works.mees.dinghy.ui.finetune.fmtValue
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The 3-zone adjuster Focus surface — the shared sketch-003 adjustment archetype.
 *
 * Every numeric-adjustment screen (Fine-Tune, Temperature, Outputs) composes this as its
 * Focus content. The three zones are distributed via `Arrangement.SpaceBetween`:
 *
 * **Zone 1 — Header (top):** [DinghyIconView] tinted [works.mees.dinghy.theme.ThemeTokens.accent2]
 * + bold [name] in Geist SemiBold + trailing spacer + optional Reset button ([Intent.Warn] / amber)
 * when [onReset] is non-null.
 *
 * **Zone 2 — Value (centered, absorbs slack):** Big [GeistMono] value text at 48sp (scaled by [fsSp]),
 * followed inline (same baseline [Row]) by the "was {baseline}{unit}" span in [works.mees.dinghy.theme.ThemeTokens.text3]
 * when [shouldShowBaseline] is true. **Never stacked** — a second line under the value overflows
 * the 5U phone-landscape focus budget (adjustment-controls.md anti-pattern).
 *
 * **Zone 3 — Controls (bottom):** A "−"/"+"`[OutlinedControl]` stepper Row (`[Intent.Accent]`,
 * `enabled = [enabled] && [value] != null`) above the caller-provided `[incrementPicker]` slot.
 *
 * ## Reset button (D-21)
 * Shown only when [onReset] is non-null. Uses [Intent.Warn] (amber / `t.heat`) per the
 * adjustment archetype — "proceed at peril" for a reset. **NEVER** `color-mix(in oklch, heat, outline)`
 * — that produces a red bleed (adjustment-controls.md § "What to Avoid").
 *
 * ## Enabled state
 * [enabled] gates both the "−" and "+" stepper tiles. When [value] is null (unreported / DASH)
 * the tiles are disabled regardless of [enabled].
 *
 * @param icon           the param's [DinghyIcon] registry token; tinted [works.mees.dinghy.theme.ThemeTokens.accent2] in the header.
 * @param name           the human-readable param name (e.g. "Print Speed").
 * @param value          the live numeric value; null renders [DASH] and disables the stepper.
 * @param unit           the unit suffix appended to the displayed value (e.g. "%", "mm/s").
 * @param baseline       the value captured on entry (for the "was X" span); null hides Reset.
 * @param decimals       decimal precision used both for display ([fmtValue]) and for the
 *                       [shouldShowBaseline] rounding comparison.
 * @param onDecrement    called on "−" tap; caller must pre-clamp + [works.mees.dinghy.ui.finetune.FineTuneHolder.markPending]
 *                       (D-22 clamp authority invariant — this component has no knowledge of limits).
 * @param onIncrement    called on "+" tap; same clamp requirement.
 * @param onReset        called on Reset tap; null when no baseline is available (hides the button).
 * @param enabled        busy-lock gate from the holder; when false both stepper tiles are inert.
 * @param incrementPicker caller-provided [IncrementPicker] slot (the step-set selector row).
 * @param modifier       caller-supplied modifier.
 */
@Composable
fun AdjusterPanel(
    icon: DinghyIcon,
    name: String,
    value: Double?,
    unit: String,
    baseline: Double?,
    decimals: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onReset: (() -> Unit)?,
    enabled: Boolean,
    incrementPicker: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // WR-07 (26-rev): the busy-lock must DIM as well as inert (the P17 "group dims + inert" UX).
    // OutlinedControl has no `enabled` param — apply the 25-03 convention (alpha 0.38 + semantics
    // disabled, BookmarkedMacrosScreen precedent) to the stepper tiles and Reset so the lock is
    // visible and TalkBack stops announcing them as actionable.
    val controlsEnabled = enabled && value != null
    val disabledModifier =
        if (!controlsEnabled) Modifier.alpha(0.38f).semantics { disabled() } else Modifier
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Zone 1 — Header: icon + name + spacer + optional Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(
                icon = icon,
                tint = t.accent2,
                sizeDp = 24.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
                color = t.text,
            )
            Spacer(modifier = Modifier.weight(1f))
            onReset?.let { reset ->
                OutlinedControl(
                    label = stringResource(R.string.adjuster_reset),
                    onClick = { if (controlsEnabled) reset() },
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .then(disabledModifier),
                    // D-21: caution/amber — NOT color-mix (red-bleed bug); use heat directly
                    intent = Intent.Warn,
                )
            }
        }

        // Zone 2 — Value + inline "was X" baseline (centered, absorbs slack)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (value == null) DASH else fmtValue(value, decimals) + unit,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(48f, t.fs).sp,
                    color = t.text,
                )
                // Inline "was X" — SAME Row as the value (NEVER a stacked second row below).
                // adjustment-controls.md anti-pattern: a second line overflows 5U phone-landscape Focus.
                if (shouldShowBaseline(value, baseline, decimals)) {
                    Text(
                        // Leading two spaces = the inline gap from the hero value (same Row, by design).
                        text = "  " + stringResource(
                            R.string.adjuster_was,
                            fmtValue(baseline!!, decimals) + unit,
                        ),
                        color = t.text3,
                        fontFamily = GeistMono,
                        fontSize = fsSp(18f, t.fs).sp,
                    )
                }
            }
        }

        // Zone 3 — Stepper + IncrementPicker (bottom)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // WR-11 exemption (documented): "−"/"+" are locale-independent math glyphs on the
                // stepper tiles — not translatable copy — so they stay literal by decision.
                OutlinedControl(
                    label = "−",
                    onClick = { if (controlsEnabled) onDecrement() },
                    modifier = Modifier
                        .weight(1f)
                        .then(disabledModifier),
                    intent = Intent.Accent,
                )
                OutlinedControl(
                    label = "+",
                    onClick = { if (controlsEnabled) onIncrement() },
                    modifier = Modifier
                        .weight(1f)
                        .then(disabledModifier),
                    intent = Intent.Accent,
                )
            }
            incrementPicker()
        }
    }
}

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
