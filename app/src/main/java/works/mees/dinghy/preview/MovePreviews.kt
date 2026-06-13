package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.ui.move.MoveVm
import works.mees.dinghy.ui.move.OldMoveScreen

/**
 * @Preview matrix for the rebuilt Move screen (27-03 / D-15 preview-first convention).
 *
 * MoveScreen uses a SPECIALIZED command-centric layout (D-15 exemption — the sacred-square 3×3
 * jog pad requires its spatial shape; portrait caps the pad at 60% HEIGHT via BoxWithConstraints).
 *
 * The STATELESS overload `MoveScreen(vm, inFlight, onBack)` drives ALL previews — no live
 * Moonraker, no [works.mees.dinghy.ui.move.MoveHolder].
 *
 * Interesting axes:
 *  - Portrait 60%-height cap: the JogPad must be visibly NOT full-height in the portrait preview;
 *    the Z column + distance stepper must be visible beneath it.
 *  - Landscape: JogPad fills full height, Z + distance columns beside it, FootButtonBar at bottom.
 *  - Per-axis homed state: unhomed axes show the amber caution indicator.
 *  - Theme: 6 combos × dark/light × palette mode
 *  - fs = L overflow check — verifies Z readout / distance readout center-cell content doesn't clip
 *  - Pseudolocale en-XA — i18n completeness sweep
 */

// ─────────────────────────────────────────────────────────────────────────────
// Sample fixtures for Move
// ─────────────────────────────────────────────────────────────────────────────

/** Fully homed Move state with a representative Z position. */
private val moveHomed = MoveVm(
    x = 125.0,
    y = 125.0,
    z = 5.2,
    xHomed = true,
    yHomed = true,
    zHomed = true,
    allHomed = true,
)

/** Fully UNhomed Move state — all axes show amber caution indicators. */
private val moveUnhomed = MoveVm(
    x = null,
    y = null,
    z = null,
    xHomed = false,
    yHomed = false,
    zHomed = false,
    allHomed = false,
)

/** Partially homed — X/Y homed, Z not homed (common mid-run state). */
private val movePartialHomed = MoveVm(
    x = 75.4,
    y = 50.1,
    z = null,
    xHomed = true,
    yHomed = true,
    zHomed = false,
    allHomed = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// Portrait vs Landscape matrix
// (the 60%-HEIGHT portrait cap MUST be visible — pad not full-bleed; D-03 / D-15)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Move portrait (Nexus7 — 60% cap)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun MovePortraitMatrix() =
    PreviewBox(colorfulDark) { OldMoveScreen(vm = moveHomed) }

@Preview(name = "Move landscape (Nexus7 — full height pad)", device = NEXUS7, showBackground = true)
@Composable
private fun MoveLandscapeMatrix() =
    PreviewBox(colorfulDark) { OldMoveScreen(vm = moveHomed) }

@Preview(name = "Move portrait unhomed (Nexus7)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun MovePortraitUnhomed() =
    PreviewBox(colorfulDark) { OldMoveScreen(vm = moveUnhomed) }

@Preview(name = "Move portrait partial homed (Nexus7)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun MovePortraitPartialHomed() =
    PreviewBox(colorfulDark) { OldMoveScreen(vm = movePartialHomed) }

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix on the homed portrait state
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun MoveThemeColorfulDark() =
    PreviewBox(colorfulDark) { OldMoveScreen(vm = moveHomed) }

@Nexus7Previews
@Composable
private fun MoveThemeColorfulLight() =
    PreviewBox(colorfulLight) { OldMoveScreen(vm = moveHomed) }

@Nexus7Previews
@Composable
private fun MoveThemeSimpleDark() =
    PreviewBox(simpleDark) { OldMoveScreen(vm = moveHomed) }

@Nexus7Previews
@Composable
private fun MoveThemeSimpleLight() =
    PreviewBox(simpleLight) { OldMoveScreen(vm = moveHomed) }

@Nexus7Previews
@Composable
private fun MoveThemeHighContrastDark() =
    PreviewBox(highContrastDark) { OldMoveScreen(vm = moveHomed) }

@Nexus7Previews
@Composable
private fun MoveThemeHighContrastLight() =
    PreviewBox(highContrastLight) { OldMoveScreen(vm = moveHomed) }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — verifies Z column readout / distance display center-cell
// text doesn't clip at the largest in-app text size.
// NOTE: @Preview(fontScale=) is a NO-OP — must seed via fsLargeSeed tuple.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Move fs=L portrait overflow check", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun MoveFsLargeOverflowPortrait() =
    PreviewBox(fsLargeSeed) { OldMoveScreen(vm = moveHomed) }

@Preview(name = "Move fs=L landscape overflow check", device = NEXUS7, showBackground = true)
@Composable
private fun MoveFsLargeOverflowLandscape() =
    PreviewBox(fsLargeSeed) { OldMoveScreen(vm = moveHomed) }

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale en-XA — i18n completeness sweep (SC-3c)
// Verifies all visible strings are in strings.xml (pseudo-expands them with accents + brackets).
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Move pseudolocale en-XA", device = NEXUS7_PORTRAIT, locale = "en-XA", showBackground = true)
@Composable
private fun MovePseudolocaleSpotCheck() =
    PreviewBox(colorfulDark) { OldMoveScreen(vm = moveHomed) }
