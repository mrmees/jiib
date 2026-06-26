package works.mees.jiib.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.jiib.outputs.OutputDescriptor
import works.mees.jiib.outputs.OutputRowVm
import works.mees.jiib.ui.outputs.OutputsScreen

/**
 * @Preview matrix for the Outputs screen (26-05 / PREVIEW_AND_TOKENS). Drives the STATELESS
 * [OutputsScreen] seam from pure [OutputRowVm] fixtures — NO live Moonraker, NO [works.mees.jiib.outputs.OutputsHolder],
 * NO socket. The sample list covers every render branch:
 *  - a fan with a live `%` value,
 *  - an LED with a non-null `swatchColor` (the THEME-01 data-color chip),
 *  - a servo with NO `displayValue` (SC-3 — row stays tappable),
 *  - a read-only pin (`isSettable = false` → dimmed row per SC-3),
 *  - a pwm_tool with absent value (SC-3 — value omitted, row still rendered).
 *
 * Two Focus states are exercised:
 *  - [OutputsNoSelection]: `selectedKey = null` → empty-Focus prompt ("Select an output").
 *  - [OutputsWithSelection]: `selectedKey = sampleRows[0].descriptor.objectKey` → Focus shows the
 *    fan row's label placeholder (live [OutputFocusControl] requires a holder — omitted in previews).
 *
 * A `@Preview` annotation cannot select the Colorful/Simple/High-Contrast palette MODE; the themes
 * MUST be explicit [PreviewBox] seed wrappers, and `fs = L` is injected via [fsLargeSeed].
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
        displayValue = null, swatchColor = null, isSettable = true, busy = false, // SC-3: no angle
    ),
    OutputRowVm(
        descriptor = desc("output_pin static_led", "output_pin", "static_led", readOnly = true),
        displayValue = "On", swatchColor = null, isSettable = false, busy = false,
    ),
    OutputRowVm(
        descriptor = desc("pwm_tool laser", "pwm_tool", "laser"),
        displayValue = null, swatchColor = null, isSettable = true, busy = false, // SC-3: absent value
    ),
)

private val selectedKey = sampleRows[0].descriptor.objectKey  // fan_generic FILTER_fan

private fun desc(key: String, family: String, name: String, readOnly: Boolean = false) = OutputDescriptor(
    objectKey = key, family = family, commandName = name, prettyName = name,
    pwm = true, servoAngleMax = 180f, readOnly = readOnly,
)

// --- No-selection variants (Focus = empty prompt) --------------------------------------------

@Composable
private fun OutputsNoSelection() = OutputsScreen(
    rows = sampleRows,
    selectedKey = null,
    onSelect = {},
    onBack = {},
)

// --- With-selection variants (Focus = label placeholder — holder absent in preview) ----------

@Composable
private fun OutputsWithSelection() = OutputsScreen(
    rows = sampleRows,
    selectedKey = selectedKey,
    onSelect = {},
    onBack = {},
)

// ---------------------------------------------------------------------------------------------
// Full 6-theme matrix — no-selection (base layout proof)
// ---------------------------------------------------------------------------------------------

@Nexus7Previews
@Composable
private fun OutputsColorfulDark() = PreviewBox(colorfulDark) { OutputsNoSelection() }

@Nexus7Previews
@Composable
private fun OutputsColorfulLight() = PreviewBox(colorfulLight) { OutputsNoSelection() }

@Nexus7Previews
@Composable
private fun OutputsSimpleDark() = PreviewBox(simpleDark) { OutputsNoSelection() }

@Nexus7Previews
@Composable
private fun OutputsSimpleLight() = PreviewBox(simpleLight) { OutputsNoSelection() }

@Nexus7Previews
@Composable
private fun OutputsHighContrastDark() = PreviewBox(highContrastDark) { OutputsNoSelection() }

@Nexus7Previews
@Composable
private fun OutputsHighContrastLight() = PreviewBox(highContrastLight) { OutputsNoSelection() }

/**
 * fs = L overflow shot ([fsLargeSeed]) — catches row/value clipping at the LARGEST in-app text
 * size. `fs` is injected through the seed; `@Preview(fontScale = …)` is a verified NO-OP here.
 */
@Nexus7Previews
@Composable
private fun OutputsFsLargeOverflow() = PreviewBox(fsLargeSeed) { OutputsNoSelection() }

/** With-selection state — exercises the Focus region FocusFrame path. */
@Nexus7Previews
@Composable
private fun OutputsSelectedColorfulDark() = PreviewBox(colorfulDark) { OutputsWithSelection() }

/** Landscape spot-check: portrait-stack → landscape Focus|Field 50/50 via [ScreenScaffold]. */
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Composable
private fun OutputsLandscapeSpotCheck() = PreviewBox(colorfulDark) { OutputsNoSelection() }

/** RTL spot-check — rows use start/end-relative arrangement so they mirror in RTL. */
@Nexus7Previews
@Composable
private fun OutputsRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            OutputsNoSelection()
        }
    }
}

/** Pseudolocale (`en-XA`) i18n-completeness spot-check. */
@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun OutputsPseudolocaleSpotCheck() = PreviewBox(colorfulDark) { OutputsNoSelection() }
