package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.ui.outputs.OutputLedContent
import works.mees.dinghy.ui.outputs.OutputScrubberContent
import works.mees.dinghy.ui.outputs.OutputScrubberType
import works.mees.dinghy.ui.outputs.OutputToggleControl

/**
 * Day-one @Preview matrix for the Phase-19 output DETAIL pages (19-06 / PREVIEW_AND_TOKENS). Drives the
 * STATELESS content seams ([OutputScrubberContent], [OutputLedContent], [OutputToggleControl]) from pure
 * sample data — NO live Moonraker, NO holder, NO dispatcher. Covers the LED page (a couple of hue/brightness
 * states), the shared scrubber detail (a fan + a heater sample), and the digital toggle (On + Off states).
 *
 * A `@Preview` annotation cannot select the Colorful/Simple/High-Contrast palette MODE; the themes MUST be
 * explicit [PreviewBox] seed wrappers, and `fs = L` is injected via [fsLargeSeed] (NOT `@Preview(fontScale)`,
 * a verified NO-OP — the #1 copy-paste trap).
 */

// --- Stateless cells --------------------------------------------------------------------------------

@Composable
private fun LedCell() = OutputLedContent(
    prettyName = "Case Light",
    ledHasRgb = true,
    ledHasWhite = false,
    initialHue = 210f,
    initialBrightness = 80f,
    enabled = true,
    failureText = null,
    onColorSettle = { _, _ -> },
    onWhiteSettle = {},
    onOff = {},
    onBack = {},
)

/** GAP-B: a white/brightness-only LED — brightness control ONLY, no hue wheel, grey/white swatch. */
@Composable
private fun WhiteOnlyLedCell() = OutputLedContent(
    prettyName = "Chamber Light",
    ledHasRgb = false,
    ledHasWhite = true,
    initialHue = 0f,
    initialBrightness = 80f,
    enabled = true,
    failureText = null,
    onColorSettle = { _, _ -> },
    onWhiteSettle = {},
    onOff = {},
    onBack = {},
)

@Composable
private fun FanScrubberCell() = OutputScrubberContent(
    prettyName = "Filter Fan",
    type = OutputScrubberType.FAN,
    currentValue = 45f,
    servoAngleMax = 180f,
    readOnly = false,
    enabled = true,
    failureText = null,
    heaterTemp = null,
    onSettle = {},
    onOff = {},
    onBack = {},
)

@Composable
private fun HeaterScrubberCell() = OutputScrubberContent(
    prettyName = "Chamber",
    type = OutputScrubberType.HEATER,
    currentValue = 60f,
    servoAngleMax = 180f,
    readOnly = false,
    enabled = true,
    failureText = null,
    heaterTemp = 42f,
    onSettle = {},
    onOff = {},
    onBack = {},
)

@Composable
private fun ToggleOnCell() = OutputToggleControl(
    prettyName = "Spot LED",
    isOn = true,
    readOnly = false,
    enabled = true,
    failureText = null,
    onOn = {},
    onOff = {},
    onBack = {},
)

@Composable
private fun ToggleOffCell() = OutputToggleControl(
    prettyName = "Spot LED",
    isOn = false,
    readOnly = false,
    enabled = true,
    failureText = null,
    onOn = {},
    onOff = {},
    onBack = {},
)

// --- LED: the full 6-theme matrix (dark/light × Colorful/Simple/HighContrast) -----------------------

@Nexus7Previews
@Composable
private fun LedColorfulDark() = PreviewBox(colorfulDark) { LedCell() }

@Nexus7Previews
@Composable
private fun LedColorfulLight() = PreviewBox(colorfulLight) { LedCell() }

@Nexus7Previews
@Composable
private fun LedSimpleDark() = PreviewBox(simpleDark) { LedCell() }

@Nexus7Previews
@Composable
private fun LedSimpleLight() = PreviewBox(simpleLight) { LedCell() }

@Nexus7Previews
@Composable
private fun LedHighContrastDark() = PreviewBox(highContrastDark) { LedCell() }

@Nexus7Previews
@Composable
private fun LedHighContrastLight() = PreviewBox(highContrastLight) { LedCell() }

/** LED fs = L overflow shot — catches wheel/brightness/swatch clipping at the largest text size. */
@Nexus7Previews
@Composable
private fun LedFsLargeOverflow() = PreviewBox(fsLargeSeed) { LedCell() }

/** GAP-B: white-only LED — brightness-only (NO hue wheel), grey/white swatch. */
@Nexus7Previews
@Composable
private fun WhiteOnlyLedColorfulDark() = PreviewBox(colorfulDark) { WhiteOnlyLedCell() }

/** White-only LED fs = L overflow shot. */
@Nexus7Previews
@Composable
private fun WhiteOnlyLedFsLargeOverflow() = PreviewBox(fsLargeSeed) { WhiteOnlyLedCell() }

// --- Scrubber detail (fan + heater) -----------------------------------------------------------------

@Nexus7Previews
@Composable
private fun FanScrubberColorfulDark() = PreviewBox(colorfulDark) { FanScrubberCell() }

@Nexus7Previews
@Composable
private fun HeaterScrubberColorfulDark() = PreviewBox(colorfulDark) { HeaterScrubberCell() }

@Nexus7Previews
@Composable
private fun FanScrubberFsLargeOverflow() = PreviewBox(fsLargeSeed) { FanScrubberCell() }

// --- Digital toggle (On + Off) ----------------------------------------------------------------------

@Nexus7Previews
@Composable
private fun ToggleOnColorfulDark() = PreviewBox(colorfulDark) { ToggleOnCell() }

@Nexus7Previews
@Composable
private fun ToggleOffColorfulDark() = PreviewBox(colorfulDark) { ToggleOffCell() }

@Nexus7Previews
@Composable
private fun ToggleHighContrastLight() = PreviewBox(highContrastLight) { ToggleOnCell() }

@Nexus7Previews
@Composable
private fun ToggleFsLargeOverflow() = PreviewBox(fsLargeSeed) { ToggleOffCell() }

/** Pseudolocale (`en-XA`) i18n spot-check on the LED page — any English showing through is unrouted. */
@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun OutputDetailPseudolocaleSpotCheck() {
    PreviewBox(colorfulDark) { LedCell() }
}
