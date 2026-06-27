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
 *  - [newOffset] `savedOffset + liveBabystep` when both non-null; null otherwise.
 *    Mirrors Klipper's own `Z_OFFSET_APPLY_PROBE` macro: `new = z_offset + homing_origin.z`.
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
            combine(store.probeZOffset, store.printerState, store.capabilities) {
                probeZOffset, printerState, caps ->
                buildVm(probeZOffset, printerState, caps)
            }.collect { _vm.value = it }
        }
    }

    private fun buildVm(
        probeZOffset: Float?,
        printerState: PrinterState,
        caps: Capabilities,
    ): ApplyBabystepVm {
        val savedOffset = probeZOffset?.toDouble()
        val liveBabystep = printerState.gcodeZOffset

        // Klipper: new_z_offset = saved_z_offset + gcode_move.homing_origin.z
        val newOffset = if (savedOffset != null && liveBabystep != null) savedOffset + liveBabystep else null

        val canApply = liveBabystep != null && liveBabystep != 0.0 && savedOffset != null

        return ApplyBabystepVm(
            savedOffset = savedOffset,
            liveBabystep = liveBabystep,
            newOffset = newOffset,
            applyCommand = zOffsetApplyGate(caps),
            canApply = canApply,
        )
    }
}
