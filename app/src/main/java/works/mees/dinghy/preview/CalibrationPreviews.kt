package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.calibration.ProbePageState
import works.mees.dinghy.ui.calibration.CalibrationHubContent
import works.mees.dinghy.ui.calibration.ProbeCalibrateContent

/**
 * @Preview matrix for CalibrationHubScreen + ProbeCalibrateScreen (27-04).
 *
 * Targets the STATELESS `CalibrationHubContent` and `ProbeCalibrateContent` seams
 * (WARNING-5 preview-first convention). No live Moonraker, no VM, no holder — pure
 * fixture data from [SampleFixtures].
 *
 * Structure is intentionally per-screen so 27-05 (BedMesh) and 27-06 (ScrewsTilt/Tilt)
 * append new sections to this file cleanly.
 *
 * Hub axes:
 *  - Routine matrix: ProbeCalibrate-selected / BedMesh-selected / an unsupported-greyed entry
 *    visible — exercises D-05 pre-select + D-06 greyed-but-listed
 *  - 6 theme combos (ProbeCalibrate selected — the most common hub entry)
 *  - fs = L overflow: verifies title + description text don't clip the Focus card
 *  - Pseudolocale en-XA: i18n completeness sweep
 *
 * Probe axes:
 *  - State matrix: Idle-unhomed / Idle-homed / Active / Accepted — exercises all four foot branches
 *  - 6 theme combos on Active (most complex state — two footer buttons, live-Z column active)
 *  - fs = L overflow: verifies ZReadoutDisplay + step cell don't overflow columns
 *  - Pseudolocale en-XA
 */

// ─────────────────────────────────────────────────────────────────────────────
// CalibrationHubContent previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Hub: ProbeCalibrate selected (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun HubProbeCalibrateSelected() = PreviewBox(colorfulDark) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Preview(
    name = "Hub: BedMesh selected (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun HubBedMeshSelected() = PreviewBox(colorfulDark) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.BED_MESH,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Preview(
    name = "Hub: QuadGantryLevel selected (unsupported-greyed visible, Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun HubUnsupportedGreyed() = PreviewBox(colorfulDark) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.QUAD_GANTRY_LEVEL, // unsupported — greyed in list
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

// ── 6-theme matrix on ProbeCalibrate-selected ────────────────────────────────

@Nexus7Previews
@Composable
private fun HubThemeColorfulDark() = PreviewBox(colorfulDark) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun HubThemeColorfulLight() = PreviewBox(colorfulLight) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun HubThemeSimpleDark() = PreviewBox(simpleDark) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun HubThemeSimpleLight() = PreviewBox(simpleLight) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun HubThemeHighContrastDark() = PreviewBox(highContrastDark) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun HubThemeHighContrastLight() = PreviewBox(highContrastLight) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

// ── fs = L overflow check ─────────────────────────────────────────────────────

@Preview(
    name = "Hub fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun HubFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Preview(
    name = "Hub fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun HubFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

// ── Pseudolocale en-XA ────────────────────────────────────────────────────────

@Preview(
    name = "Hub pseudolocale en-XA",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun HubPseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    CalibrationHubContent(
        routines = SampleFixtures.calibrationRoutineList,
        selected = CalibrationRoutine.PROBE_CALIBRATE,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ProbeCalibrateContent previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Probe: Idle-unhomed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeIdleUnhomed() = PreviewBox(colorfulDark) {
    val vm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = false)
    ProbeCalibrateContent(
        vm = vm,
        step = 0.1,
        starting = false,
        saveGuard = false,
        toastError = null,
        enabled = false,
        onTestZUp = {}, onTestZDown = {},
        onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {},
        onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {},
        onDismissError = {}, onBack = {},
    )
}

@Preview(
    name = "Probe: Idle-homed ready (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeIdleHomed() = PreviewBox(colorfulDark) {
    val vm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = true)
    ProbeCalibrateContent(
        vm = vm,
        step = 0.1,
        starting = false,
        saveGuard = false,
        toastError = null,
        enabled = false,
        onTestZUp = {}, onTestZDown = {},
        onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {},
        onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {},
        onDismissError = {}, onBack = {},
    )
}

@Preview(
    name = "Probe: Idle-starting disabled (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeIdleStarting() = PreviewBox(colorfulDark) {
    val vm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = true)
    ProbeCalibrateContent(
        vm = vm,
        step = 0.1,
        starting = true, // command in-flight, not yet Active
        saveGuard = false,
        toastError = null,
        enabled = false,
        onTestZUp = {}, onTestZDown = {},
        onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {},
        onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {},
        onDismissError = {}, onBack = {},
    )
}

@Preview(
    name = "Probe: Active live-Z (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeActive() = PreviewBox(colorfulDark) {
    val vm = SampleFixtures.probeVm(ProbePageState.Active)
    ProbeCalibrateContent(
        vm = vm,
        step = 0.05,
        starting = false,
        saveGuard = false,
        toastError = null,
        enabled = true, // TESTZ buttons enabled in Active state
        onTestZUp = {}, onTestZDown = {},
        onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {},
        onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {},
        onDismissError = {}, onBack = {},
    )
}

@Preview(
    name = "Probe: Accepted (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeAccepted() = PreviewBox(colorfulDark) {
    val vm = SampleFixtures.probeVm(ProbePageState.Accepted)
    ProbeCalibrateContent(
        vm = vm,
        step = 0.05,
        starting = false,
        saveGuard = false,
        toastError = null,
        enabled = false,
        onTestZUp = {}, onTestZDown = {},
        onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {},
        onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {},
        onDismissError = {}, onBack = {},
    )
}

// ── 6-theme matrix on Active state (most complex) ─────────────────────────────

@Nexus7Previews
@Composable
private fun ProbeThemeColorfulDark() = PreviewBox(colorfulDark) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeThemeColorfulLight() = PreviewBox(colorfulLight) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeThemeSimpleDark() = PreviewBox(simpleDark) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeThemeSimpleLight() = PreviewBox(simpleLight) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeThemeHighContrastDark() = PreviewBox(highContrastDark) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeThemeHighContrastLight() = PreviewBox(highContrastLight) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}

// ── fs = L overflow check ─────────────────────────────────────────────────────

@Preview(
    name = "Probe fs=L Active portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}

@Preview(
    name = "Probe fs=L Active landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}

// ── Pseudolocale en-XA ────────────────────────────────────────────────────────

@Preview(
    name = "Probe pseudolocale en-XA Active",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun ProbePseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    ProbeCalibrateContent(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false, saveGuard = false, toastError = null, enabled = true,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onHomeAll = {}, onStart = {}, onAccept = {}, onAbort = {},
        onSaveGuardShow = {}, onSaveConfirm = {}, onSaveCancel = {}, onDismissError = {}, onBack = {},
    )
}
