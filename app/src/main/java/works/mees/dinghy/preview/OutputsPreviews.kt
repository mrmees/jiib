package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.outputs.OutputDescriptor
import works.mees.dinghy.outputs.OutputRowVm
import works.mees.dinghy.ui.outputs.OutputsContent

/**
 * Day-one @Preview matrix for the Outputs list (19-05 / PREVIEW_AND_TOKENS). Drives the STATELESS
 * [OutputsContent] seam from a pure handcrafted [OutputRowVm] list — NO live Moonraker, NO [OutputsHolder],
 * NO socket. The sample list deliberately covers every render branch the screen must handle:
 *  - a fan with a live `%` value,
 *  - an LED with a non-null `swatchColor` (the THEME-01 data-color chip),
 *  - a servo with NO `displayValue` (PWM-not-angle hidden — SC-3, row stays tappable),
 *  - a read-only pin (`isSettable = false` → hairline/dimmed),
 *  - an absent-value row (`displayValue = null` → value omitted, row still rendered + tappable — SC-3).
 *
 * A `@Preview` annotation cannot select the Colorful/Simple/High-Contrast palette MODE; the themes MUST be
 * explicit [PreviewBox] seed wrappers, and `fs = L` is injected via [fsLargeSeed] (NOT
 * `@Preview(fontScale = …)`, a verified NO-OP — the #1 copy-paste trap).
 */
private val sampleRows: List<OutputRowVm> = listOf(
    OutputRowVm(
        descriptor = desc("fan_generic FILTER_fan", "fan_generic", "FILTER_fan"),
        displayValue = "45%", swatchColor = null, isSettable = true, busy = false,
    ),
    OutputRowVm(
        descriptor = desc("led caselight", "led", "caselight"),
        displayValue = "80%", swatchColor = 0xFF33B1FFL, isSettable = true, busy = false,
    ),
    OutputRowVm(
        descriptor = desc("servo my_servo", "servo", "my_servo"),
        displayValue = null, swatchColor = null, isSettable = true, busy = false, // servo: PWM not angle (SC-3).
    ),
    OutputRowVm(
        descriptor = desc("output_pin static_led", "output_pin", "static_led", readOnly = true),
        displayValue = "On", swatchColor = null, isSettable = false, busy = false, // read-only static pin.
    ),
    OutputRowVm(
        descriptor = desc("pwm_tool laser", "pwm_tool", "laser"),
        displayValue = null, swatchColor = null, isSettable = true, busy = false, // absent value (SC-3).
    ),
)

private fun desc(key: String, family: String, name: String, readOnly: Boolean = false) = OutputDescriptor(
    objectKey = key, family = family, commandName = name, prettyName = name,
    pwm = true, servoAngleMax = 180f, readOnly = readOnly,
)

@Composable
private fun OutputsCell() = OutputsContent(rows = sampleRows, onRowTap = {}, onBack = {})

// ---------------------------------------------------------------------------------------------
// The full 6-theme matrix (dark/light × Colorful/Simple/HighContrast) — six PreviewBox seed wrappers.
// ---------------------------------------------------------------------------------------------

@Nexus7Previews
@Composable
private fun OutputsColorfulDark() = PreviewBox(colorfulDark) { OutputsCell() }

@Nexus7Previews
@Composable
private fun OutputsColorfulLight() = PreviewBox(colorfulLight) { OutputsCell() }

@Nexus7Previews
@Composable
private fun OutputsSimpleDark() = PreviewBox(simpleDark) { OutputsCell() }

@Nexus7Previews
@Composable
private fun OutputsSimpleLight() = PreviewBox(simpleLight) { OutputsCell() }

@Nexus7Previews
@Composable
private fun OutputsHighContrastDark() = PreviewBox(highContrastDark) { OutputsCell() }

@Nexus7Previews
@Composable
private fun OutputsHighContrastLight() = PreviewBox(highContrastLight) { OutputsCell() }

/**
 * The fs = L overflow shot ([fsLargeSeed]) — catches row/value clipping at the LARGEST in-app text size.
 * fs is injected through the seed's `fs`; `@Preview(fontScale = …)` is a verified NO-OP here.
 */
@Nexus7Previews
@Composable
private fun OutputsFsLargeOverflow() = PreviewBox(fsLargeSeed) { OutputsCell() }

/** RTL spot-check — proves the row uses `start`/`end`-relative arrangement so it mirrors in RTL locales. */
@Nexus7Previews
@Composable
private fun OutputsRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            OutputsCell()
        }
    }
}

/** Pseudolocale (`en-XA`) i18n-completeness spot-check — any plain English showing through is unrouted. */
@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun OutputsPseudolocaleSpotCheck() {
    PreviewBox(colorfulDark) { OutputsCell() }
}
