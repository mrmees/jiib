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
import works.mees.jiib.calibration.ApplyBabystepVm
import works.mees.jiib.calibration.ProbeTestVm
import works.mees.jiib.calibration.ProbeCalibrateVm
import works.mees.jiib.ui.calibration.ApplyBabystepBody
import works.mees.jiib.ui.calibration.EddyCalibrateBody
import works.mees.jiib.ui.calibration.EddyRunBody
import works.mees.jiib.ui.calibration.ProbeTestBody
import works.mees.jiib.ui.calibration.ZOffsetBody
import works.mees.jiib.ui.calibration.BedMeshContent
import works.mees.jiib.ui.calibration.CalibrationHubContent
import works.mees.jiib.ui.calibration.MeshFieldMode
import works.mees.jiib.ui.calibration.ProbeContent
import works.mees.jiib.ui.calibration.ScrewsTiltContent
import works.mees.jiib.ui.calibration.TiltContent
import works.mees.jiib.ui.calibration.TiltVariant
import works.mees.jiib.command.GatingState
import works.mees.jiib.designsystem.icons.JiibIcons

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
// ZOffsetBody previews (Task R4)
//
// Targets the stateless ZOffsetBody seam (WARNING-5 preview-first convention).
//
// Axes:
//  - State matrix: Idle-unhomed / Idle-homed / Idle-starting / Active / Accepted
//  - 6 theme combos on Active (most complex state — jog enabled, Accept+Abort buttons)
//  - fs = L overflow check on Active portrait
// ─────────────────────────────────────────────────────────────────────────────

private val DEFAULT_TESTZ_STEPS = listOf(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 5.0, 10.0)

@Preview(name = "ZOffset: Idle-unhomed (portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ZOffsetIdleUnhomed() = PreviewBox(colorfulDark) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = false),
        step = 0.1, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Preview(name = "ZOffset: Idle-homed ready (portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ZOffsetIdleHomed() = PreviewBox(colorfulDark) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = true),
        step = 0.1, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Preview(name = "ZOffset: Idle-starting (portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ZOffsetIdleStarting() = PreviewBox(colorfulDark) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = true),
        step = 0.1, starting = true,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Preview(name = "ZOffset: Active live-Z (portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ZOffsetActive() = PreviewBox(colorfulDark) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Preview(name = "ZOffset: Accepted (portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ZOffsetAccepted() = PreviewBox(colorfulDark) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Accepted),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

// ── 6-theme matrix on Active (most complex) ────────────────────────────────────

@Nexus7Previews
@Composable
private fun ZOffsetThemeColorfulDark() = PreviewBox(colorfulDark) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Nexus7Previews
@Composable
private fun ZOffsetThemeColorfulLight() = PreviewBox(colorfulLight) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Nexus7Previews
@Composable
private fun ZOffsetThemeSimpleDark() = PreviewBox(simpleDark) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Nexus7Previews
@Composable
private fun ZOffsetThemeSimpleLight() = PreviewBox(simpleLight) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Nexus7Previews
@Composable
private fun ZOffsetThemeHighContrastDark() = PreviewBox(highContrastDark) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

@Nexus7Previews
@Composable
private fun ZOffsetThemeHighContrastLight() = PreviewBox(highContrastLight) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

// ── fs=L overflow ────────────────────────────────────────────────────────────

@Preview(name = "ZOffset fs=L Active portrait overflow", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ZOffsetFsLargePortrait() = PreviewBox(fsLargeSeed) {
    ZOffsetBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, starting = false,
        onStart = {}, onAccept = {}, onSaveConfig = {}, onHomeAll = {},
    )
}

// ── ProbeContent with Z_OFFSET selected ──────────────────────────────────────

@Preview(name = "ProbeContent Z_OFFSET Idle (portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ProbeContentZOffsetIdle() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        probeCalibrateVm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = true),
        step = 0.1, steps = DEFAULT_TESTZ_STEPS, starting = false,
        onSelect = {},
        onBack = {},
    )
}

@Preview(name = "ProbeContent Z_OFFSET Active (portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ProbeContentZOffsetActive() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        probeCalibrateVm = SampleFixtures.probeVm(ProbePageState.Active),
        activeSessionTool = ProbeTool.Z_OFFSET,
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false,
        onSelect = {},
        onBack = {},
    )
}

@Preview(name = "ProbeContent Z_OFFSET Accepted (portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun ProbeContentZOffsetAccepted() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        probeCalibrateVm = SampleFixtures.probeVm(ProbePageState.Accepted),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false,
        onSelect = {},
        onBack = {},
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
// ProbeContent previews (R1: single Focus-centric Probe screen)
//
// Targets the stateless ProbeContent seam (WARNING-5). No live Moonraker, no holder.
// Reuses SampleFixtures.probeToolList (6 tools, 3 supported / 3 greyed).
//
// Axes:
//  - Tool matrix: Z_OFFSET-selected / PROBE_TEST-selected / null-selected (empty tools)
//    — exercises D-05 pre-select + null-guard fallback
//  - 2 theme variants on Z_OFFSET-selected (dark + light)
//  - fs=L overflow check (portrait): verifies Focus placeholder + list don't clip
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ProbeScreen: Z_OFFSET selected (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeScreenZOffsetSelected() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onBack = {},
    )
}

@Preview(
    name = "ProbeScreen: PROBE_TEST selected (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeScreenProbeTestSelected() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.PROBE_TEST,
        onSelect = {},
        onBack = {},
    )
}

@Preview(
    name = "ProbeScreen: null selected / empty tools (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeScreenNullSelected() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = emptyList(),
        selected = null,
        onSelect = {},
        onBack = {},
    )
}

// ── Theme variants on Z_OFFSET-selected ──────────────────────────────────────

@Nexus7Previews
@Composable
private fun ProbeScreenThemeColorfulDark() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onBack = {},
    )
}

@Nexus7Previews
@Composable
private fun ProbeScreenThemeColorfulLight() = PreviewBox(colorfulLight) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onBack = {},
    )
}

// ── fs=L overflow check ───────────────────────────────────────────────────────

@Preview(
    name = "ProbeScreen fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeScreenFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.Z_OFFSET,
        onSelect = {},
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ProbeContent + ApplyBabystepBody previews (Task R2)
//
// Targets both the ProbeContent seam (APPLY_BABYSTEP selected) and the standalone
// ApplyBabystepBody composable (WARNING-5 preview-first convention). No live Moonraker.
//
// Axes:
//  - ProbeContent: APPLY_BABYSTEP selected, canApply=true + canApply=false
//  - ApplyBabystepBody: canApply=true (values populated) × 2 themes; canApply=false
//  - fs=L overflow check (portrait): verifies hero value + buttons don't clip
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ProbeScreen: APPLY_BABYSTEP selected, canApply=true (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeScreenApplyBabystepCanApply() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.APPLY_BABYSTEP,
        applyBabystepVm = SampleFixtures.applyBabystepCanApply,
        onSelect = {},
        onBack = {},
    )
}

@Preview(
    name = "ProbeScreen: APPLY_BABYSTEP selected, canApply=false / no data (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeScreenApplyBabystepNoData() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.APPLY_BABYSTEP,
        applyBabystepVm = SampleFixtures.applyBabystepNoData,
        onSelect = {},
        onBack = {},
    )
}

// ── ApplyBabystepBody standalone previews ────────────────────────────────────

@Preview(
    name = "ApplyBabystepBody: canApply=true dark (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepBodyCanApplyDark() = PreviewBox(colorfulDark) {
    ApplyBabystepBody(
        vm = SampleFixtures.applyBabystepCanApply,
        dispatcher = null,
        uDp = 56.dp,
        onRequestConfirm = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ApplyBabystepBody: canApply=true light (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepBodyCanApplyLight() = PreviewBox(colorfulLight) {
    ApplyBabystepBody(
        vm = SampleFixtures.applyBabystepCanApply,
        dispatcher = null,
        uDp = 56.dp,
        onRequestConfirm = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ApplyBabystepBody: canApply=false / no data (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepBodyNoData() = PreviewBox(colorfulDark) {
    ApplyBabystepBody(
        vm = SampleFixtures.applyBabystepNoData,
        dispatcher = null,
        uDp = 56.dp,
        onRequestConfirm = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ApplyBabystepBody: fs=L overflow check (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ApplyBabystepBodyFsLarge() = PreviewBox(fsLargeSeed) {
    ApplyBabystepBody(
        vm = SampleFixtures.applyBabystepCanApply,
        dispatcher = null,
        uDp = 56.dp,
        onRequestConfirm = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ProbeTestBody previews (Task R3)
//
// Two axes:
//  A) ProbeContent with PROBE_TEST selected — exercises the body wiring + live status params.
//  B) Standalone ProbeTestBody — exercises all four Focus rows in isolation.
//
// ProbeTestBody axes:
//  - State matrix: no-data / triggered (OPEN) / triggered+lastZ / with accuracy result
//  - 2 theme variants (dark + light) on triggered state
//  - fs=L overflow check (accuracy state — 6-stat table + stepper + buttons)
// ─────────────────────────────────────────────────────────────────────────────

// ── A) ProbeContent + PROBE_TEST selected ────────────────────────────────────

@Preview(
    name = "ProbeScreen R3: PROBE_TEST no-data (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeScreenProbeTestNoData() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.PROBE_TEST,
        probeTestVm = SampleFixtures.probeTestNoData,
        onSelect = {},
        onBack = {},
    )
}

@Preview(
    name = "ProbeScreen R3: PROBE_TEST triggered+lastZ (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeScreenProbeTestTriggered() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.PROBE_TEST,
        probeTestVm = SampleFixtures.probeTestTriggered,
        probeIsZEndstop = true,
        liveTriggered = true,
        onSelect = {},
        onBack = {},
    )
}

@Preview(
    name = "ProbeScreen R3: PROBE_TEST with accuracy (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeScreenProbeTestWithAccuracy() = PreviewBox(colorfulDark) {
    ProbeContent(
        tools = SampleFixtures.probeToolList,
        selected = ProbeTool.PROBE_TEST,
        probeTestVm = SampleFixtures.probeTestWithAccuracy,
        probeIsZEndstop = true,
        liveTriggered = false,
        onSelect = {},
        onBack = {},
    )
}

// ── B) Standalone ProbeTestBody ───────────────────────────────────────────────

@Preview(
    name = "ProbeTestBody: no-data (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeTestBodyNoData() = PreviewBox(colorfulDark) {
    ProbeTestBody(
        vm = SampleFixtures.probeTestNoData,
        samplesIdx = 4,
        dispatcher = null,
        uDp = 56.dp,
        onSamplesUp = {},
        onSamplesDown = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ProbeTestBody: triggered OPEN+lastZ dark (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeTestBodyTriggeredDark() = PreviewBox(colorfulDark) {
    ProbeTestBody(
        vm = SampleFixtures.probeTestTriggered,
        probeIsZEndstop = true,
        liveTriggered = true,
        samplesIdx = 4,
        dispatcher = null,
        uDp = 56.dp,
        onSamplesUp = {},
        onSamplesDown = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ProbeTestBody: triggered OPEN+lastZ light (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeTestBodyTriggeredLight() = PreviewBox(colorfulLight) {
    ProbeTestBody(
        vm = SampleFixtures.probeTestTriggered,
        probeIsZEndstop = true,
        liveTriggered = true,
        samplesIdx = 4,
        dispatcher = null,
        uDp = 56.dp,
        onSamplesUp = {},
        onSamplesDown = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ProbeTestBody: with accuracy result (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeTestBodyWithAccuracy() = PreviewBox(colorfulDark) {
    ProbeTestBody(
        vm = SampleFixtures.probeTestWithAccuracy,
        probeIsZEndstop = true,
        liveTriggered = false,
        samplesIdx = 4,
        dispatcher = null,
        uDp = 56.dp,
        onSamplesUp = {},
        onSamplesDown = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

// ── fs=L overflow check (accuracy state — 6-stat table + stepper + buttons) ──

@Preview(
    name = "ProbeTestBody: fs=L accuracy portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun ProbeTestBodyFsLargeAccuracyPortrait() = PreviewBox(fsLargeSeed) {
    ProbeTestBody(
        vm = SampleFixtures.probeTestWithAccuracy,
        probeIsZEndstop = true,
        liveTriggered = false,
        samplesIdx = 4,
        dispatcher = null,
        uDp = 56.dp,
        onSamplesUp = {},
        onSamplesDown = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "ProbeTestBody: fs=L accuracy landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun ProbeTestBodyFsLargeAccuracyLandscape() = PreviewBox(fsLargeSeed) {
    ProbeTestBody(
        vm = SampleFixtures.probeTestWithAccuracy,
        probeIsZEndstop = true,
        liveTriggered = false,
        samplesIdx = 4,
        dispatcher = null,
        uDp = 56.dp,
        onSamplesUp = {},
        onSamplesDown = {},
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EddyRunBody previews (Task R5)
//
// Targets the stateless EddyRunBody seam (WARNING-5 preview-first convention).
// No live Moonraker, no VM — pure fixture data and sample console lines.
// BUILD-BLIND: eddy hardware required; build-blind note always visible.
//
// Two variants:
//  - Drive Current: no stage selector (tapStages = null)
//  - Eddy Tap: with stage selector (Guess / Refine / Verify chips)
//
// Preview axes:
//  - Variant × portrait/landscape (drive current empty/filled; tap guess-selected)
//  - 6 theme combos on Tap-with-stages portrait (richest state — stage chips + buttons)
//  - fs = L overflow check on Drive Current portrait (description + build-blind note + tail)
// ─────────────────────────────────────────────────────────────────────────────

private val sampleEddyConsoleLines = listOf(
    "LDC_CALIBRATE_DRIVE_CURRENT CHIP=btt_eddy",
    "// Preparing calibration…",
    "// Testing drive current: 15",
    "// Testing drive current: 20",
    "// Optimal drive current: 20",
    "// Done.",
)

private val sampleTapStageLabels = listOf("Guess", "Refine", "Verify")

private const val SAMPLE_BUILD_BLIND_NOTE =
    "⚠ Requires eddy current hardware — not validated on device (build-blind)."

// ── Drive Current: no stage selector, empty console ─────────────────────────

@Preview(
    name = "EddyRunBody: Drive Current empty console (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyRunBodyDriveCurrentEmpty() = PreviewBox(colorfulDark) {
    EddyRunBody(
        description = "Adjust the Eddy current coil drive current for optimal signal amplitude.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = emptyList(),
        tapStages = null,
        selectedStage = null,
        onSelectStage = {},
        onRun = {},
        onSave = {},
        uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

// ── Drive Current: no stage selector, filled console ────────────────────────

@Preview(
    name = "EddyRunBody: Drive Current filled console (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyRunBodyDriveCurrentFilled() = PreviewBox(colorfulDark) {
    EddyRunBody(
        description = "Adjust the Eddy current coil drive current for optimal signal amplitude.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines,
        tapStages = null,
        selectedStage = null,
        onSelectStage = {},
        onRun = {},
        onSave = {},
        uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyRunBody: Drive Current filled console (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun EddyRunBodyDriveCurrentFilledLandscape() = PreviewBox(colorfulDark) {
    EddyRunBody(
        description = "Adjust the Eddy current coil drive current for optimal signal amplitude.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines,
        tapStages = null,
        selectedStage = null,
        onSelectStage = {},
        onRun = {},
        onSave = {},
        uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

// ── Eddy Tap: stage selector, Guess selected ──────────────────────────────

@Preview(
    name = "EddyRunBody: Tap Guess-selected empty console (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyRunBodyTapGuessEmpty() = PreviewBox(colorfulDark) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = emptyList(),
        tapStages = sampleTapStageLabels,
        selectedStage = "Guess",
        onSelectStage = {},
        onRun = {},
        onSave = {},
        uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyRunBody: Tap Refine-selected filled console (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyRunBodyTapRefineSelected() = PreviewBox(colorfulDark) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines,
        tapStages = sampleTapStageLabels,
        selectedStage = "Refine",
        onSelectStage = {},
        onRun = {},
        onSave = {},
        uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyRunBody: Tap Guess-selected filled console (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun EddyRunBodyTapGuessFilledLandscape() = PreviewBox(colorfulDark) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines,
        tapStages = sampleTapStageLabels,
        selectedStage = "Guess",
        onSelectStage = {},
        onRun = {},
        onSave = {},
        uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

// ── 6-theme matrix on Tap Guess-selected portrait (richest state) ─────────

@Nexus7Previews
@Composable
private fun EddyRunBodyTapThemeColorfulDark() = PreviewBox(colorfulDark) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines, tapStages = sampleTapStageLabels,
        selectedStage = "Guess", onSelectStage = {}, onRun = {}, onSave = {}, uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyRunBodyTapThemeColorfulLight() = PreviewBox(colorfulLight) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines, tapStages = sampleTapStageLabels,
        selectedStage = "Guess", onSelectStage = {}, onRun = {}, onSave = {}, uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyRunBodyTapThemeSimpleDark() = PreviewBox(simpleDark) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines, tapStages = sampleTapStageLabels,
        selectedStage = "Guess", onSelectStage = {}, onRun = {}, onSave = {}, uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyRunBodyTapThemeSimpleLight() = PreviewBox(simpleLight) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines, tapStages = sampleTapStageLabels,
        selectedStage = "Guess", onSelectStage = {}, onRun = {}, onSave = {}, uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyRunBodyTapThemeHighContrastDark() = PreviewBox(highContrastDark) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines, tapStages = sampleTapStageLabels,
        selectedStage = "Guess", onSelectStage = {}, onRun = {}, onSave = {}, uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyRunBodyTapThemeHighContrastLight() = PreviewBox(highContrastLight) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines, tapStages = sampleTapStageLabels,
        selectedStage = "Guess", onSelectStage = {}, onRun = {}, onSave = {}, uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

// ── fs = L overflow check ─────────────────────────────────────────────────────

@Preview(
    name = "EddyRunBody: Drive Current fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyRunBodyDriveCurrentFsLargePortrait() = PreviewBox(fsLargeSeed) {
    EddyRunBody(
        description = "Adjust the Eddy current coil drive current for optimal signal amplitude.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines,
        tapStages = null,
        selectedStage = null,
        onSelectStage = {},
        onRun = {},
        onSave = {},
        uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyRunBody: Tap fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyRunBodyTapFsLargePortrait() = PreviewBox(fsLargeSeed) {
    EddyRunBody(
        description = "Set the Eddy current tap threshold used to detect bed contact.",
        buildBlindNote = SAMPLE_BUILD_BLIND_NOTE,
        lines = sampleEddyConsoleLines,
        tapStages = sampleTapStageLabels,
        selectedStage = "Guess",
        onSelectStage = {},
        onRun = {},
        onSave = {},
        uDp = 56.dp,
        modifier = Modifier.fillMaxSize(),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EddyCalibrateBody previews (Task R6)
//
// Targets the stateless EddyCalibrateBody seam (WARNING-5 preview-first convention).
// No live Moonraker, no VM — pure fixture data and sample console lines.
// BUILD-BLIND: eddy hardware required; build-blind note visible in Idle phase.
//
// Three phases:
//  - Idle: description + build-blind caution + Start button
//  - Idle-starting: disabled "Starting…" button (eddy_calibrate in flight)
//  - Active (paper-test): Z readout + ManualProbeJog + Accept + Abort
//  - Accepted (sweep): ConsoleTail + Save button
//
// Preview axes:
//  - State matrix: Idle / Idle-starting / Active / Accepted (portrait)
//  - 6 theme combos on Active portrait (richest state — jog + buttons)
//  - fs = L overflow check on Active portrait
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "EddyCalibrate: Idle (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyCalibrateBodyIdle() = PreviewBox(colorfulDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = true),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS,
        starting = false, enabled = false,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyCalibrate: Idle-starting disabled (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyCalibrateBodyIdleStarting() = PreviewBox(colorfulDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Idle, homedGate = true),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS,
        starting = true, enabled = false,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyCalibrate: Active paper-test (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyCalibrateBodyActive() = PreviewBox(colorfulDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS,
        starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyCalibrate: Active paper-test (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun EddyCalibrateBodyActiveLandscape() = PreviewBox(colorfulDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS,
        starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyCalibrate: Accepted sweep console (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyCalibrateBodySweep() = PreviewBox(colorfulDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Accepted),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS,
        starting = false, enabled = false,
        lines = sampleEddyConsoleLines, uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(
    name = "EddyCalibrate: Accepted sweep console (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun EddyCalibrateBodySweepLandscape() = PreviewBox(colorfulDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Accepted),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS,
        starting = false, enabled = false,
        lines = sampleEddyConsoleLines, uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

// ── 6-theme matrix on Active (richest state: jog + Accept + Abort) ──────────

@Nexus7Previews
@Composable
private fun EddyCalibrateBodyThemeColorfulDark() = PreviewBox(colorfulDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyCalibrateBodyThemeColorfulLight() = PreviewBox(colorfulLight) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyCalibrateBodyThemeSimpleDark() = PreviewBox(simpleDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyCalibrateBodyThemeSimpleLight() = PreviewBox(simpleLight) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyCalibrateBodyThemeHighContrastDark() = PreviewBox(highContrastDark) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Nexus7Previews
@Composable
private fun EddyCalibrateBodyThemeHighContrastLight() = PreviewBox(highContrastLight) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}

// ── fs = L overflow check (Active state — Z readout + ManualProbeJog + buttons) ─

@Preview(
    name = "EddyCalibrate fs=L Active portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun EddyCalibrateBodyFsLargePortrait() = PreviewBox(fsLargeSeed) {
    EddyCalibrateBody(
        vm = SampleFixtures.probeVm(ProbePageState.Active),
        step = 0.05, steps = DEFAULT_TESTZ_STEPS, starting = false, enabled = true,
        lines = emptyList(), uDp = 56.dp,
        onTestZUp = {}, onTestZDown = {}, onStepUp = {}, onStepDown = {},
        onStart = {}, onAccept = {}, onAbort = {}, onSaveConfig = {},
        modifier = Modifier.fillMaxSize(),
    )
}
