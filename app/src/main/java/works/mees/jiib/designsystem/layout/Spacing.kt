package works.mees.jiib.designsystem.layout

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * U-relative inner-spacing tokens for control classes (control baseline audit, 2026-06-14).
 *
 * Supersedes R13's "spacing is a named FIXED-dp set, never U-derived" clause: the NAMES survive but
 * now resolve to fractions of the unit grid `U` ([UnitGrid]/[uDp]) so spacing scales with the grid
 * and stays single-sourced. Calibrated to the prior fixed values at U=64dp (phone-landscape floor):
 * gapS→8dp, gapM→12dp. See docs/ui_design/THEMING.md §7b and COMPONENTS.md §7b/§4.
 */

/** Tight gap — grid/tile gaps, intra-row element spacing. ≈ U×0.125 (= 8dp at U=64). */
fun gapS(uDp: Dp): Dp = uDp * 0.125f

/** Rhythm gap — inter-row spacing. ≈ U×0.1875 (= 12dp at U=64). */
fun gapM(uDp: Dp): Dp = uDp * 0.1875f

/**
 * Floating-overlay corner padding (the FloatingEStop shell fallback ONLY). NOT U-relative — a
 * floating overlay has no row context to scale against, so this stays a fixed dp edge case.
 */
val padFloat: Dp = 14.dp

/**
 * THE single control-height rule (owner UAT 2026-06-15). Every interactive control ROW is EXACTLY
 * 1U tall — `height(uDp)`, floored at the 64dp touch target. Use this instead of hand-rolling
 * `.height(uDp)` / `.heightIn(min = uDp)` per component so control heights can't drift apart
 * (the foot-bar-vs-focus-tile mismatch). EXACT height (not a `min` floor) is deliberate: a `min`
 * lets a control stretch when its container has spare vertical space, and different regions hand out
 * that space differently — which is exactly what made "1U" controls render a few px apart.
 *
 * The ONE sanctioned exception is a control that intentionally GROWS past 1U (the Output On/Off
 * toggle that fills the focus). Those opt OUT — they do not call this and size themselves.
 */
fun Modifier.controlHeight(uDp: Dp): Modifier = this.height(maxOf(uDp, 64.dp))
