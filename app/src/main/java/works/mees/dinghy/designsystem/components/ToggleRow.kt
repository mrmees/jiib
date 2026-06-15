package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.R
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

// ─────────────────────────────────────────────────────────────────────────────
// Pure helpers (host-testable — no Compose runtime)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The string-resource id for the [ToggleRow] pill TEXT: checked → [R.string.common_on],
 * unchecked → [R.string.common_off]. Pure so the "text pill, never a glyph" contract
 * (icon-registry law — ToggleRow is text-only) stays host-testable.
 */
internal fun toggleStateLabelRes(checked: Boolean): Int =
    if (checked) R.string.common_on else R.string.common_off

/**
 * Whether a [ToggleRow] in state [checked] requests the accent treatment — drives BOTH the row/pill
 * border (`accentLine` vs `outline`) AND the pill text color (`accent` vs `text2`). It is simply the
 * checked state; modeled as a helper so the decision is host-testable (the concrete `Color`s are
 * pulled from [LocalTokens] at render time).
 */
internal fun toggleWantsAccent(checked: Boolean): Boolean = checked

/**
 * The dim alpha applied to a truly-disabled ([enabled] = false) [ToggleRow] — matches the
 * StepperRow / AdjusterPanel / 25-03 WR-07 convention (alpha 0.38 + `semantics { disabled() }`).
 */
internal const val TOGGLE_DIM_ALPHA = 0.38f

// ─────────────────────────────────────────────────────────────────────────────
// ToggleRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * THE canonical full-width **labeled toggle row** (control baseline audit, Phase 5): a label
 * (+ optional sub-label) on the left and an **"On"/"Off" TEXT pill** on the right. Extracted
 * VERBATIM from the owner-shipped `DevEnableRow` look (About screen) — zero new look invented.
 *
 * Canonicalizes the `DevEnableRow` / `SecureToggleRow` / `DenseToggleRow` rogue family. The
 * Printers `SecureToggleRow` pill and the Settings `DenseToggleRow` `Switch` are OUT this pass
 * (owner ruling 2026-06-15) but should adopt this class when those areas are reworked.
 *
 * ## Anatomy (from DevEnableRow, generalized)
 *  - Outer `Row`: `fillMaxWidth().heightIn(min = uDp)` (the 1U floor — Phase-28 ruling; a sub-label
 *    is allowed to grow the row past 1U), `clip(RoundedCornerShape(t.rCard))`,
 *    `border(2.dp, accentLine if checked else outline)`, `padding(horizontal = 16.dp)`.
 *  - Left `Column(weight(1f))`: [label] (Geist SemiBold `fsSp(17)`) + the optional [subLabel]
 *    (Geist `fsSp(15)`, `t.text3`) — the sub-label `Text` is emitted ONLY when [subLabel] != null.
 *  - Right pill `Box`: `clip(RoundedCornerShape(t.rPill))`, `border(2.dp, accentLine if checked
 *    else outline)`, `padding(h16, v8)`; the `"On"/"Off"` TEXT (Geist Bold `fsSp(17)`), color
 *    accent when checked else `t.text2`. TEXT pill — NEVER a glyph (icon law).
 *
 * ## a11y — `toggleable`, NOT `clickable` (the audit's a11y goal)
 *  - The row uses `Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch,
 *    onValueChange = onToggle)` so TalkBack announces the correct Switch role + checked state (the
 *    raw `DevEnableRow` `clickable` did not). `onValueChange` hands back the NEW value, so call
 *    sites pass [onToggle] directly. The `toggleable` modifier is applied BEFORE the
 *    `semantics { contentDescription }` block so the role/state survive and the description merges.
 *
 * ## Enabled
 *  - [enabled] = false → the row dims to `alpha [TOGGLE_DIM_ALPHA]` + `semantics { disabled() }`
 *    (the StepperRow / WR-07 convention); `toggleable(enabled = false)` already installs no action.
 *    No in-scope call site passes `enabled = false` — the param exists for the OUT toggles' future
 *    adoption.
 *
 * @param label     left-hand row label (the toggle's name).
 * @param checked   current on/off state.
 * @param onToggle  called with the NEW value on a row tap (or via the TalkBack Switch action).
 * @param uDp       one unit U — floors the row at 1U.
 * @param modifier  caller-supplied modifier (applied to the row, e.g. `fillMaxWidth()` at a dialog).
 * @param subLabel  optional secondary line under the label; emitted only when non-null.
 * @param contentDescription a11y label for the row; defaults to [label] when null.
 * @param enabled   true-disablement gate (dim + `disabled()`; no toggle action). Default true.
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val outline = if (toggleWantsAccent(checked)) t.accentLine else t.outline
    // True disablement: dim + announce disabled (the StepperRow / WR-07 convention). toggleable
    // with enabled = false already installs no toggle action.
    val disabledModifier =
        if (!enabled) Modifier.alpha(TOGGLE_DIM_ALPHA).semantics { disabled() } else Modifier
    val rowCd = contentDescription ?: label

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = uDp)
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            // a11y goal: a real Switch toggleable, NOT clickable — TalkBack gets the role +
            // checked state. Applied BEFORE the semantics block so the description merges in.
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onToggle,
            )
            .semantics { this.contentDescription = rowCd }
            .then(disabledModifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(17f, t.fs).sp,
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = t.text3,
                    fontFamily = Geist,
                    fontSize = fsSp(15f, t.fs).sp,
                )
            }
        }
        val pillShape = RoundedCornerShape(t.rPill)
        Box(
            Modifier
                .clip(pillShape)
                .border(
                    BorderStroke(2.dp, if (toggleWantsAccent(checked)) t.accentLine else t.outline),
                    pillShape,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(toggleStateLabelRes(checked)),
                color = if (toggleWantsAccent(checked)) t.accent else t.text2,
                fontFamily = Geist,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }
    }
}
