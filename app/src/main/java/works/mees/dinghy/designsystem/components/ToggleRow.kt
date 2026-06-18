package works.mees.dinghy.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

// ─────────────────────────────────────────────────────────────────────────────
// Pure helpers (host-testable — no Compose runtime)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Whether a [ToggleRow] in state [checked] requests the accent treatment — drives BOTH the row
 * border (`accentLine` vs `outline`) AND the switch colors (`accent` knob/`accentSoft` track vs
 * `text3` knob/`outline` track). It is simply the checked state; modeled as a helper so the
 * decision is host-testable (the concrete `Color`s are pulled from [LocalTokens] at render time).
 */
internal fun toggleWantsAccent(checked: Boolean): Boolean = checked

/**
 * The dim alpha applied to a truly-disabled ([enabled] = false) [ToggleRow] — matches the
 * StepperRow / AdjusterPanel / 25-03 WR-07 convention (alpha 0.38 + `semantics { disabled() }`).
 */
internal const val TOGGLE_DIM_ALPHA = 0.38f

/** The on/off switch track capsule shape — hoisted to file scope so it isn't reallocated per recompose. */
private val ToggleSwitchTrackShape = RoundedCornerShape(percent = 50)

/** Inset between the switch track edge and the knob, at both ends. */
private val ToggleSwitchPad = 3.dp

// ─────────────────────────────────────────────────────────────────────────────
// ToggleRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * THE canonical full-width **labeled toggle row** (control baseline audit, Phase 5): a label
 * (+ optional sub-label) on the left and a **[ToggleSwitch]** on the right (switch restyle
 * 2026-06-17). Extracted VERBATIM from the owner-shipped `DevEnableRow` look (About screen) —
 * zero new look invented.
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
 *  - Right: [ToggleSwitch] — a tokenized rounded-capsule track + sliding knob (switch restyle
 *    2026-06-17). ON = accentSoft track / accentLine border / solid accent knob (trailing); OFF =
 *    transparent track / outline border / faint text3 knob (leading). State is position + color,
 *    no text. The row border still flips accentLine↔outline with [toggleWantsAccent] (kept — extra
 *    glanceability on a printer screen).
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
                style = DinghyType.listLabel.toTextStyle(t),
                // A long label (e.g. a verbose Klipper macro name) must not wrap past the 1U row floor.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = t.text3,
                    style = DinghyType.caption.toTextStyle(t),
                )
            }
        }
        ToggleSwitch(checked = checked, uDp = uDp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ToggleSwitch — the trailing on/off affordance (switch restyle 2026-06-17)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The canonical on/off SWITCH: a tokenized rounded-capsule track + a sliding knob, replacing the
 * former "On/Off" text pill. State is carried by knob position AND color (no text):
 *  - ON  → `accentSoft` track fill, `accentLine` 2dp border, solid `accent` knob at the trailing edge.
 *  - OFF → transparent track, `outline` 2dp border, faint `text3` knob at the leading edge.
 * The knob slides via a single one-shot `animateDpAsState` (cheap — respects the no-looping-animation
 * rule + the Adreno-320 floor). Sized U-relative so it fits inside a 1U row. All colors are role
 * tokens (THEME-01 — never raw). a11y is owned by the parent row's `toggleable(role = Role.Switch)`.
 */
@Composable
private fun ToggleSwitch(checked: Boolean, uDp: Dp) {
    val t = LocalTokens.current
    val trackH = uDp * 0.5f
    val trackW = uDp * 0.95f
    val knob = trackH - ToggleSwitchPad * 2
    val knobX by animateDpAsState(
        targetValue = if (checked) trackW - knob - ToggleSwitchPad else ToggleSwitchPad,
        label = "toggleKnobX",
    )
    Box(
        Modifier
            .width(trackW)
            .height(trackH)
            .clip(ToggleSwitchTrackShape)
            .background(if (checked) t.accentSoft else Color.Transparent)
            .border(BorderStroke(2.dp, if (toggleWantsAccent(checked)) t.accentLine else t.outline), ToggleSwitchTrackShape),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobX)
                .size(knob)
                .clip(CircleShape)
                .background(if (toggleWantsAccent(checked)) t.accent else t.text3),
        )
    }
}
