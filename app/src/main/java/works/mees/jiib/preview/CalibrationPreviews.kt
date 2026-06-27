package works.mees.jiib.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.components.ConsoleTail
import works.mees.jiib.calibration.CalibrationRoutine
import works.mees.jiib.calibration.ProbePageState
import works.mees.jiib.calibration.ProbeTool
import works.mees.jiib.calibration.TiltState
import works.mees.jiib.ui.calibration.ApplyBabystepContent
import works.mees.jiib.ui.calibration.BedMeshContent
import works.mees.jiib.ui.calibration.CalibrationHubContent
import works.mees.jiib.ui.calibration.MeshFieldMode
import works.mees.jiib.ui.calibration.ProbeCalibrateContent
import works.mees.jiib.ui.calibration.ProbeHubContent
import works.mees.jiib.ui.calibration.ProbeTestContent
import works.mees.jiib.ui.calibration.ScrewsTiltContent
import works.mees.jiib.ui.calibration.TiltContent
import works.mees.jiib.ui.calibration.TiltVariant

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

// ─────────────────────────────────────────────────────────────────────────────
// BedMeshContent previews (27-05)
//
// Targets the STATELESS BedMeshContent seam (WARNING-5). No live Moonraker, no VM.
// The BedMeshHeatmapHost Focus renders via the LocalInspectionMode placeholder branch
// inside BedMeshHeatmapHost itself (P22 D-05/D-04) — no Views host instantiated in preview.
//
// BedMesh axes:
//  - State matrix: ProfileList-noSelection / ProfileList-selected / SaveName-takeover / empty-profiles
//  - 6 theme combos on ProfileList-selected (most interactive state — Apply+Remove+Back foot)
//  - fs = L overflow: SaveName takeover — verify field + validation text don't clip
//  - Pseudolocale en-XA
// ─────────────────────────────────────────────────────────────────────────────

// Helper to reduce boilerplate in BedMeshContent preview calls.
@Composable
private fun bedMeshPreview(
    vm: works.mees.jiib.calibration.BedMeshVm = SampleFixtures.bedMeshVm(),
    selectedProfile: String? = null,
    fieldMode: MeshFieldMode = MeshFieldMode.ProfileList,
    editing: Boolean = false,
    showRemoveGuard: Boolean = false,
    showSaveConfigGuard: Boolean = false,
    toastError: String? = null,
    isPrinting: Boolean = false,
    dispatcherPresent: Boolean = true,
) {
    BedMeshContent(
        vm = vm,
        selectedProfile = selectedProfile,
        fieldMode = fieldMode,
        editing = editing,
        showRemoveGuard = showRemoveGuard,
        showSaveConfigGuard = showSaveConfigGuard,
        toastError = toastError,
        isPrinting = isPrinting,
        dispatcherPresent = dispatcherPresent,
        onEmergencyStop = {},
        onCycleScaleMode = {},
        onSelectProfile = {},
        onClearMesh = {},
        onOpenMeshConfig = {},
        onOpenConfigEditor = {},
        onBackToConfigList = {},
        onBackToProfileList = {},
        onSetViewType = {},
        onSetHighColorSel = {},
        onSetLowColorSel = {},
        onShowSaveName = {},
        onSaveNameConfirm = {},
        onSaveNameCancel = {},
        onApplyProfile = {},
        onShowRemoveGuard = {},
        onRemoveConfirm = {},
        onRemoveCancel = {},
        onSaveConfigConfirm = {},
        onSaveConfigCancel = {},
        onEditOpen = {},
        onEditApply = {},
        onEditSave = {},
        onEditDelete = {},
        onEditCancel = {},
        onHomeAll = {},
        onCalibrate = {},
        onDismissError = {},
        onBack = {},
    )
}

// ── State matrix (4 states: ProfileList-no-selection / selected / SaveName / empty) ──────────

@Preview(
    name = "BedMesh: ProfileList no selection (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun BedMeshProfileListNoSelection() = PreviewBox(colorfulDark) {
    bedMeshPreview(
        vm = SampleFixtures.bedMeshVm(),
        selectedProfile = null,
    )
}

@Preview(
    name = "BedMesh: ProfileList profile selected (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun BedMeshProfileListSelected() = PreviewBox(colorfulDark) {
    bedMeshPreview(
        vm = SampleFixtures.bedMeshVm(),
        selectedProfile = "adaptive",
    )
}

@Preview(
    name = "BedMesh: SaveName takeover (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun BedMeshSaveNameTakeover() = PreviewBox(colorfulDark) {
    bedMeshPreview(
        vm = SampleFixtures.bedMeshVm(),
        fieldMode = MeshFieldMode.SaveName("26.06.11_14.30"),
    )
}

@Preview(
    name = "BedMesh: Empty profiles + unhomed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun BedMeshEmptyProfiles() = PreviewBox(colorfulDark) {
    bedMeshPreview(vm = SampleFixtures.bedMeshEmpty)
}

// ── 6-theme matrix on ProfileList-selected (most interactive state) ────────────────────────

@Nexus7Previews
@Composable
private fun BedMeshThemeColorfulDark() = PreviewBox(colorfulDark) {
    bedMeshPreview(vm = SampleFixtures.bedMeshVm(), selectedProfile = "adaptive")
}

@Nexus7Previews
@Composable
private fun BedMeshThemeColorfulLight() = PreviewBox(colorfulLight) {
    bedMeshPreview(vm = SampleFixtures.bedMeshVm(), selectedProfile = "adaptive")
}

@Nexus7Previews
@Composable
private fun BedMeshThemeSimpleDark() = PreviewBox(simpleDark) {
    bedMeshPreview(vm = SampleFixtures.bedMeshVm(), selectedProfile = "adaptive")
}

@Nexus7Previews
@Composable
private fun BedMeshThemeSimpleLight() = PreviewBox(simpleLight) {
    bedMeshPreview(vm = SampleFixtures.bedMeshVm(), selectedProfile = "adaptive")
}

@Nexus7Previews
@Composable
private fun BedMeshThemeHighContrastDark() = PreviewBox(highContrastDark) {
    bedMeshPreview(vm = SampleFixtures.bedMeshVm(), selectedProfile = "adaptive")
}

@Nexus7Previews
@Composable
private fun BedMeshThemeHighContrastLight() = PreviewBox(highContrastLight) {
    bedMeshPreview(vm = SampleFixtures.bedMeshVm(), selectedProfile = "adaptive")
}

// ── fs = L overflow check (SaveName takeover — keyboard field + validation) ───────────────

@Preview(
    name = "BedMesh SaveName fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun BedMeshFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    bedMeshPreview(
        vm = SampleFixtures.bedMeshVm(),
        fieldMode = MeshFieldMode.SaveName("26.06.11_14.30"),
    )
}

@Preview(
    name = "BedMesh SaveName fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun BedMeshFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    bedMeshPreview(
        vm = SampleFixtures.bedMeshVm(),
        fieldMode = MeshFieldMode.SaveName("26.06.11_14.30"),
    )
}

// ── Pseudolocale en-XA ──────────────────────────────────────────────────────────────────

@Preview(
    name = "BedMesh pseudolocale en-XA ProfileList",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun BedMeshPseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    bedMeshPreview(
        vm = SampleFixtures.bedMeshVm(),
        selectedProfile = "default",
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ScrewsTiltContent previews (27-06)
//
// Targets the STATELESS ScrewsTiltContent seam (WARNING-5). No live Moonraker, no VM.
//
// ScrewsTilt axes:
//  - State matrix: Idle (homed, no turns) / Result (4 screws with turn data in ListRows)
//  - 6 theme combos on Idle (Focus spatial visualization + empty list)
//  - fs = L overflow: Result state — verify screw name + turn instruction don't clip
//  - Pseudolocale en-XA
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ScrewsTilt: Idle homed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ScrewsTiltIdlePortrait() = PreviewBox(colorfulDark) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

@Preview(
    name = "ScrewsTilt: Idle homed (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ScrewsTiltIdleLandscape() = PreviewBox(colorfulDark) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

@Preview(
    name = "ScrewsTilt: Result with turn data (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ScrewsTiltResultPortrait() = PreviewBox(colorfulDark) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltResult)
}

@Preview(
    name = "ScrewsTilt: Result with turn data (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ScrewsTiltResultLandscape() = PreviewBox(colorfulDark) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltResult)
}

// ── 6-theme matrix on Idle state (Focus visualization + list baseline) ────────────────────

@Nexus7Previews
@Composable
private fun ScrewsTiltThemeColorfulDark() = PreviewBox(colorfulDark) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

@Nexus7Previews
@Composable
private fun ScrewsTiltThemeColorfulLight() = PreviewBox(colorfulLight) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

@Nexus7Previews
@Composable
private fun ScrewsTiltThemeSimpleDark() = PreviewBox(simpleDark) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

@Nexus7Previews
@Composable
private fun ScrewsTiltThemeSimpleLight() = PreviewBox(simpleLight) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

@Nexus7Previews
@Composable
private fun ScrewsTiltThemeHighContrastDark() = PreviewBox(highContrastDark) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

@Nexus7Previews
@Composable
private fun ScrewsTiltThemeHighContrastLight() = PreviewBox(highContrastLight) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

// ── fs = L overflow check (Result state — turn instruction trailing text) ─────────────────

@Preview(
    name = "ScrewsTilt fs=L Result portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ScrewsTiltFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltResult)
}

@Preview(
    name = "ScrewsTilt fs=L Result landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ScrewsTiltFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltResult)
}

// ── Pseudolocale en-XA ────────────────────────────────────────────────────────

@Preview(
    name = "ScrewsTilt pseudolocale en-XA Idle",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun ScrewsTiltPseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    ScrewsTiltContent(vm = SampleFixtures.screwsTiltIdle)
}

// ─────────────────────────────────────────────────────────────────────────────
// TiltContent previews (27-06)
//
// Targets the STATELESS TiltContent seam (WARNING-5). One parameterized composable
// covers both TiltVariant.ZTilt and TiltVariant.Qgl.
//
// Tilt axes:
//  - State matrix: Idle-unhomed / Idle-homed / Running / Done / Failed (ZTilt + Qgl variants)
//  - 6 theme combos on Idle-homed ZTilt (most common landing state)
//  - fs = L overflow: Done state — verify adjustment list + Z delta text don't clip
//  - Pseudolocale en-XA
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Tilt: ZTilt Idle-unhomed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun TiltZTiltIdleUnhomed() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = false),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

@Preview(
    name = "Tilt: ZTilt Idle-homed ready (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun TiltZTiltIdleHomed() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = true),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

@Preview(
    name = "Tilt: ZTilt Running (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun TiltZTiltRunning() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Running),
        variant = TiltVariant.ZTilt,
        state = TiltState.Running,
        running = true,
    )
}

@Preview(
    name = "Tilt: ZTilt Done (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun TiltZTiltDone() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Done),
        variant = TiltVariant.ZTilt,
        state = TiltState.Done,
    )
}

@Preview(
    name = "Tilt: ZTilt Failed (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun TiltZTiltFailed() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Failed),
        variant = TiltVariant.ZTilt,
        state = TiltState.Failed,
    )
}

@Preview(
    name = "Tilt: QGL Idle-homed ready (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun TiltQglIdleHomed() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.Qgl, TiltState.Idle, homedGate = true),
        variant = TiltVariant.Qgl,
        state = TiltState.Idle,
    )
}

@Preview(
    name = "Tilt: QGL Running (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun TiltQglRunning() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.Qgl, TiltState.Running),
        variant = TiltVariant.Qgl,
        state = TiltState.Running,
        running = true,
    )
}

@Preview(
    name = "Tilt: QGL Done (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun TiltQglDone() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.Qgl, TiltState.Done),
        variant = TiltVariant.Qgl,
        state = TiltState.Done,
    )
}

// ── 6-theme matrix on ZTilt Idle-homed (most common landing state) ────────────────────────

@Nexus7Previews
@Composable
private fun TiltThemeColorfulDark() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = true),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

@Nexus7Previews
@Composable
private fun TiltThemeColorfulLight() = PreviewBox(colorfulLight) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = true),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

@Nexus7Previews
@Composable
private fun TiltThemeSimpleDark() = PreviewBox(simpleDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = true),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

@Nexus7Previews
@Composable
private fun TiltThemeSimpleLight() = PreviewBox(simpleLight) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = true),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

@Nexus7Previews
@Composable
private fun TiltThemeHighContrastDark() = PreviewBox(highContrastDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = true),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

@Nexus7Previews
@Composable
private fun TiltThemeHighContrastLight() = PreviewBox(highContrastLight) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = true),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

// ── fs = L overflow check (Done state — adjustment list text) ─────────────────────────────

@Preview(
    name = "Tilt fs=L Done portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun TiltFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Done),
        variant = TiltVariant.ZTilt,
        state = TiltState.Done,
    )
}

@Preview(
    name = "Tilt fs=L Done landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun TiltFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Done),
        variant = TiltVariant.ZTilt,
        state = TiltState.Done,
    )
}

// ── Pseudolocale en-XA ────────────────────────────────────────────────────────

@Preview(
    name = "Tilt pseudolocale en-XA ZTilt Idle",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun TiltPseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    TiltContent(
        vm = SampleFixtures.tiltContent(TiltVariant.ZTilt, TiltState.Idle, homedGate = true),
        variant = TiltVariant.ZTilt,
        state = TiltState.Idle,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ProbeHubContent previews (Task 14)
//
// Targets the STATELESS ProbeHubContent seam (WARNING-5). No live Moonraker, no VM, no holder.
// Fixture data from [SampleFixtures.probeToolList] — six tools, three supported / three greyed.
//
// ProbeHub axes:
//  - Tool matrix: Z_OFFSET-selected / PROBE_TEST-selected / EDDY_CALIBRATE-selected (unsupported)
//    — exercises D-05 pre-select + D-06 greyed-but-listed
//  - 6 theme combos on Z_OFFSET-selected (the most common hub entry for E3/E5 printers)
//  - fs = L overflow: verifies title + description text don't clip the Focus card
//  - Pseudolocale en-XA: i18n completeness sweep
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ProbeHub: Z_OFFSET selected (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeHubZOffsetSelected() = PreviewBox(colorfulDark) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Preview(
    name = "ProbeHub: PROBE_TEST selected (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeHubProbeTestSelected() = PreviewBox(colorfulDark) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.PROBE_TEST,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Preview(
    name = "ProbeHub: EDDY_CALIBRATE selected (unsupported-greyed visible, Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeHubUnsupportedGreyed() = PreviewBox(colorfulDark) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.EDDY_CALIBRATE, // unsupported — greyed in list
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

// ── 6-theme matrix on Z_OFFSET-selected ─────────────────────────────────────

@Nexus7Previews
@Composable
private fun ProbeHubThemeColorfulDark() = PreviewBox(colorfulDark) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeHubThemeColorfulLight() = PreviewBox(colorfulLight) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeHubThemeSimpleDark() = PreviewBox(simpleDark) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeHubThemeSimpleLight() = PreviewBox(simpleLight) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeHubThemeHighContrastDark() = PreviewBox(highContrastDark) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeHubThemeHighContrastLight() = PreviewBox(highContrastLight) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

// ── fs = L overflow check ─────────────────────────────────────────────────────

@Preview(
    name = "ProbeHub fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeHubFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

@Preview(
    name = "ProbeHub fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeHubFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

// ── Pseudolocale en-XA ────────────────────────────────────────────────────────

@Preview(
    name = "ProbeHub pseudolocale en-XA",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun ProbeHubPseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    ProbeHubContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onOpen = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ProbeTestContent previews (Task 17)
//
// Targets the STATELESS ProbeTestContent seam (WARNING-5). No live Moonraker, no VM, no holder.
// Fixture data from [SampleFixtures.probeTestTriggered], [SampleFixtures.probeTestWithAccuracy],
// and [SampleFixtures.probeTestNoData].
//
// ProbeTest axes:
//  - State matrix: no-data / triggered (OPEN dot + last-Z, no accuracy) / with accuracy result
//  - 6 theme combos on triggered state (most common on-device landing state)
//  - fs = L overflow: accuracy state — verify 6-stat table + stepper don't clip
//  - Pseudolocale en-XA: i18n completeness sweep
// ─────────────────────────────────────────────────────────────────────────────

// Helper to reduce boilerplate in ProbeTestContent preview calls.
@Composable
private fun probeTestPreview(
    samples: Int = 10,
    samplesIdx: Int = 4, // index of 10 in SAMPLES_STEPS
    enabled: Boolean = true,
    vm: works.mees.jiib.calibration.ProbeTestVm = SampleFixtures.probeTestTriggered,
) {
    ProbeTestContent(
        vm = vm,
        samples = samples,
        samplesIdx = samplesIdx,
        enabled = enabled,
        onEmergencyStop = {},
        onAcknowledgeUnknown = {},
        onQuery = {},
        onProbeOnce = {},
        onRunAccuracy = {},
        onSamplesUp = {},
        onSamplesDown = {},
        onBack = {},
    )
}

// ── State matrix ─────────────────────────────────────────────────────────────

@Preview(
    name = "ProbeTest: No data (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeTestNoData() = PreviewBox(colorfulDark) {
    probeTestPreview(vm = SampleFixtures.probeTestNoData)
}

@Preview(
    name = "ProbeTest: Triggered OPEN+lastZ (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeTestTriggered() = PreviewBox(colorfulDark) {
    probeTestPreview(vm = SampleFixtures.probeTestTriggered)
}

@Preview(
    name = "ProbeTest: With accuracy result (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeTestWithAccuracy() = PreviewBox(colorfulDark) {
    probeTestPreview(vm = SampleFixtures.probeTestWithAccuracy)
}

// ── 6-theme matrix on triggered state ────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ProbeTestThemeColorfulDark() = PreviewBox(colorfulDark) {
    probeTestPreview(vm = SampleFixtures.probeTestTriggered)
}

@Nexus7Previews
@Composable
private fun ProbeTestThemeColorfulLight() = PreviewBox(colorfulLight) {
    probeTestPreview(vm = SampleFixtures.probeTestTriggered)
}

@Nexus7Previews
@Composable
private fun ProbeTestThemeSimpleDark() = PreviewBox(simpleDark) {
    probeTestPreview(vm = SampleFixtures.probeTestTriggered)
}

@Nexus7Previews
@Composable
private fun ProbeTestThemeSimpleLight() = PreviewBox(simpleLight) {
    probeTestPreview(vm = SampleFixtures.probeTestTriggered)
}

@Nexus7Previews
@Composable
private fun ProbeTestThemeHighContrastDark() = PreviewBox(highContrastDark) {
    probeTestPreview(vm = SampleFixtures.probeTestTriggered)
}

@Nexus7Previews
@Composable
private fun ProbeTestThemeHighContrastLight() = PreviewBox(highContrastLight) {
    probeTestPreview(vm = SampleFixtures.probeTestTriggered)
}

// ── fs = L overflow check (accuracy state — 6-stat table + stepper) ──────────

@Preview(
    name = "ProbeTest fs=L accuracy portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeTestFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    probeTestPreview(vm = SampleFixtures.probeTestWithAccuracy)
}

@Preview(
    name = "ProbeTest fs=L accuracy landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeTestFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    probeTestPreview(vm = SampleFixtures.probeTestWithAccuracy)
}

// ── Pseudolocale en-XA ────────────────────────────────────────────────────────

@Preview(
    name = "ProbeTest pseudolocale en-XA accuracy",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun ProbeTestPseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    probeTestPreview(vm = SampleFixtures.probeTestWithAccuracy)
}

// ─────────────────────────────────────────────────────────────────────────────
// ApplyBabystepContent previews (Task 18)
//
// Targets the STATELESS ApplyBabystepContent seam (WARNING-5). No live Moonraker, no VM.
// Fixture data from [SampleFixtures.applyBabystepCanApply] and [SampleFixtures.applyBabystepNoData].
//
// ApplyBabystep axes:
//  - State matrix: canApply=true with values / canApply=false with null values
//  - 6 theme combos on canApply=true (most complex state — both values + new offset shown)
//  - fs = L overflow: verifies three-row readout doesn't clip Focus
//  - Pseudolocale en-XA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun applyBabystepPreview(
    vm: works.mees.jiib.calibration.ApplyBabystepVm = SampleFixtures.applyBabystepCanApply,
    applyGuard: Boolean = false,
    saveGuard: Boolean = false,
) {
    ApplyBabystepContent(
        vm = vm,
        applyGuard = applyGuard,
        saveGuard = saveGuard,
        onSaveGuardShow = {},
        onApplyConfirm = {},
        onApplyCancel = {},
        onSaveConfirm = {},
        onSaveCancel = {},
        onBack = {},
    )
}

// ── State matrix ─────────────────────────────────────────────────────────────

@Preview(
    name = "ApplyBabystep: canApply=true with values (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepCanApply() = PreviewBox(colorfulDark) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

@Preview(
    name = "ApplyBabystep: canApply=false / null values (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepNoData() = PreviewBox(colorfulDark) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepNoData)
}

@Preview(
    name = "ApplyBabystep: apply guard open (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepApplyGuard() = PreviewBox(colorfulDark) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply, applyGuard = true)
}

@Preview(
    name = "ApplyBabystep: save guard open (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepSaveGuard() = PreviewBox(colorfulDark) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply, saveGuard = true)
}

// ── 6-theme matrix on canApply=true ──────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ApplyBabystepThemeColorfulDark() = PreviewBox(colorfulDark) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

@Nexus7Previews
@Composable
private fun ApplyBabystepThemeColorfulLight() = PreviewBox(colorfulLight) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

@Nexus7Previews
@Composable
private fun ApplyBabystepThemeSimpleDark() = PreviewBox(simpleDark) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

@Nexus7Previews
@Composable
private fun ApplyBabystepThemeSimpleLight() = PreviewBox(simpleLight) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

@Nexus7Previews
@Composable
private fun ApplyBabystepThemeHighContrastDark() = PreviewBox(highContrastDark) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

@Nexus7Previews
@Composable
private fun ApplyBabystepThemeHighContrastLight() = PreviewBox(highContrastLight) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

// ── fs = L overflow check ─────────────────────────────────────────────────────

@Preview(
    name = "ApplyBabystep fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

@Preview(
    name = "ApplyBabystep fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ApplyBabystepFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

// ── Pseudolocale en-XA ────────────────────────────────────────────────────────

@Preview(
    name = "ApplyBabystep pseudolocale en-XA canApply",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun ApplyBabystepPseudolocaleSpotCheck() = PreviewBox(colorfulDark) {
    applyBabystepPreview(vm = SampleFixtures.applyBabystepCanApply)
}

// ─────────────────────────────────────────────────────────────────────────────
// ConsoleTail component previews (Task 16)
// ─────────────────────────────────────────────────────────────────────────────

private val sampleConsoleTailLines: List<String> = listOf(
    "Starting eddy probe calibration…",
    "SET_KINEMATIC_POSITION",
    "G28 Z",
    "Homing Z…",
    "ok",
    "PROBE_EDDY_CURRENT_CALIBRATE CHIP=btt_eddy",
    "Starting manual Z probe calibration at z=3.500",
    "Move to next position: z=3.500",
    "Move to next position: z=2.000",
    "Move to next position: z=1.000",
    "Move to next position: z=0.500",
    "Move to next position: z=0.250",
    "ACCEPT",
    "Calibration complete. Saving…",
    "ok",
)

@Preview(
    name = "ConsoleTail dark portrait (Nexus7)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ConsoleTailDarkPortrait() = PreviewBox(colorfulDark) {
    ConsoleTail(
        lines = sampleConsoleTailLines,
        uDp = 64.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ConsoleTail light landscape (Nexus7)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ConsoleTailLightLandscape() = PreviewBox(colorfulLight) {
    ConsoleTail(
        lines = sampleConsoleTailLines,
        uDp = 64.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ConsoleTail empty (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ConsoleTailEmpty() = PreviewBox(colorfulDark) {
    ConsoleTail(
        lines = emptyList(),
        uDp = 64.dp,
        modifier = Modifier.fillMaxSize(),
    )
}
