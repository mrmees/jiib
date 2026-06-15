package works.mees.dinghy.designsystem.layout

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
