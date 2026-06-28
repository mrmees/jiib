package works.mees.jiib.calibration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * The "Apply Babystepping" page view-model: bakes the live G-Code Z offset (babystep) into the
 * saved probe z_offset, computing what `Z_OFFSET_APPLY_PROBE` / `Z_OFFSET_APPLY_ENDSTOP` will
 * write when the user confirms.
 *
 *  - [savedOffset] the current probe z_offset from config (null when probe-less / not yet loaded).
 *  - [liveBabystep] the live `gcode_move.homing_origin[2]` (null when not yet reported).
 *  - [newOffset] `savedOffset - liveBabystep` when both non-null; null otherwise.
 *    Mirrors Klipper's own `Z_OFFSET_APPLY_PROBE`: `new_calibrate = z_offset - homing_origin.z`.
 *    Example: saved = 2.04, babystep = -0.06 → newOffset = 2.04 - (-0.06) = 2.10.
 *  - [applyCommand] the gcode command to dispatch: "Z_OFFSET_APPLY_PROBE" when a `probe` object
 *    is present; "Z_OFFSET_APPLY_ENDSTOP" for probe-less printers (same gate as [probeCalibrateGate]).
 *  - [canApply] true only when [liveBabystep] is non-null AND non-zero AND [savedOffset] is non-null.
 *    A zero babystep means nothing to bake — the Apply button should stay disabled.
 */
data class ApplyBabystepVm(
    val savedOffset: Double? = null,
    val liveBabystep: Double? = null,
    val newOffset: Double? = null,
    val applyCommand: String = "Z_OFFSET_APPLY_PROBE",
    val canApply: Boolean = false,
)

/**
 * Pure gate for the "Apply Babystepping" apply-command: a printer with a `probe` object uses
 * `Z_OFFSET_APPLY_PROBE`; a probe-less printer falls back to `Z_OFFSET_APPLY_ENDSTOP`.
 * Parallel to [probeCalibrateGate] (CALIB-05 / A3).
 */
fun zOffsetApplyGate(caps: Capabilities): String =
    if (caps.hasObject("probe")) "Z_OFFSET_APPLY_PROBE" else "Z_OFFSET_APPLY_ENDSTOP"

/**
 * Toolkit-agnostic holder for the Apply Babystepping page. Combines the saved probe z_offset
 * ([PrinterStateStore.probeZOffset]), the live babystep offset ([PrinterState.gcodeZOffset]), and
 * capabilities ([PrinterStateStore.capabilities]) into [ApplyBabystepVm].
 *
 * Pure math, no I/O, no Compose (ADR-0001). Host-unit-tested by [ApplyBabystepHolderTest].
 *
 * @param scope the lifecycle scope the collect runs on (the UI host supplies it).
 * @param store the already-assembled Phase-2/9 spine; the holder CONSUMES it, never opens a session.
 */
class ApplyBabystepHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    private val _vm = MutableStateFlow(ApplyBabystepVm())

    /** The resolved Apply Babystepping view-model. */
    val vm: StateFlow<ApplyBabystepVm> = _vm.asStateFlow()

    init {
        scope.launch {
            combine(store.probeZOffset, store.endstopZOffset, store.printerState, store.capabilities) {
                probeZOffset, endstopZOffset, printerState, caps ->
                buildVm(probeZOffset, endstopZOffset, printerState, caps)
            }.collect { _vm.value = it }
        }
    }

    private fun buildVm(
        probeZOffset: Float?,
        endstopZOffset: Float?,
        printerState: PrinterState,
        caps: Capabilities,
    ): ApplyBabystepVm {
        // Resolve the apply command first so savedOffset selection agrees with it.
        val applyCommand = zOffsetApplyGate(caps)
        // Probe-present: saved offset is probe.z_offset; probe-less: stepper_z.position_endstop.
        // Z_OFFSET_APPLY_ENDSTOP subtracts the gcode offset from position_endstop — same arithmetic.
        val savedOffset = if (applyCommand == "Z_OFFSET_APPLY_PROBE") {
            probeZOffset?.toDouble()
        } else {
            endstopZOffset?.toDouble()
        }
        val liveBabystep = printerState.gcodeZOffset

        // Klipper: new_calibrate = z_offset - homing_origin.z  (babystep shifts the frame; subtract it)
        val newOffset = if (savedOffset != null && liveBabystep != null) savedOffset - liveBabystep else null

        val canApply = liveBabystep != null && liveBabystep != 0.0 && savedOffset != null

        return ApplyBabystepVm(
            savedOffset = savedOffset,
            liveBabystep = liveBabystep,
            newOffset = newOffset,
            applyCommand = applyCommand,
            canApply = canApply,
        )
    }
}
