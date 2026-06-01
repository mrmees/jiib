package works.mees.dinghy.ui.move

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Toolkit-agnostic holder for the Move panel (MOVE-04). It transforms the store's ALREADY-throttled
 * [PrinterStateStore.printerState] into one consumable [MoveVm] for the Compose `MoveScreen`:
 *
 *  - live `gcode_position` X/Y/Z — the offsets-stripped, USER-FACING coordinates the jog-pad corner
 *    cells render value-on-glyph (MOVE-04). Read from [PrinterState.gcodePosition], NOT
 *    [PrinterState.toolheadPosition] (raw kinematic incl. offsets — Pitfall 1). Null until the first
 *    position snapshot arrives; the holder NEVER fabricates a 0 for an unreported axis.
 *  - per-axis homed gating — derived from the lowercase `homed_axes` string (RESEARCH §3): `'x'/'y'/'z'
 *    in state.homedAxes`. The screen colors each axis label green (homed) / amber (unhomed) and gates
 *    normal jog on the per-axis flag (the amber Override is the deliberate unhomed escape).
 *
 * ## No second throttle (mirrors PrintStatusHolder)
 * The holder consumes the store's conflated flow directly (the store samples the high-rate plane at
 * `DEFAULT_SAMPLE_MS = 250`). It adds NO `sample`/`debounce`/`delay` of its own.
 *
 * Plain Kotlin (no Compose annotations) so it is host-unit-testable; mirrors the [PrinterState] /
 * [PrinterStateStore] StateFlow discipline.
 *
 * @param scope the lifecycle scope the `printerState` collect runs on (the UI host supplies it).
 * @param store the already-assembled Phase-2 spine; the holder CONSUMES it, never opens a session.
 */
class MoveHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    private val _vm = MutableStateFlow(MoveVm())
    /** The resolved Move panel view-model (live X/Y/Z + per-axis homed gating). */
    val vm: StateFlow<MoveVm> = _vm.asStateFlow()

    init {
        // Consume the store's ALREADY-throttled flow — NO second sample/debounce/delay here.
        scope.launch {
            store.printerState.collect { state ->
                _vm.value = buildVm(state)
            }
        }
    }

    private fun buildVm(state: PrinterState): MoveVm {
        // gcode_position is [X, Y, Z, E]; null per-axis when no position yet (never fabricate 0).
        val pos = state.gcodePosition
        val x = pos?.getOrNull(0)
        val y = pos?.getOrNull(1)
        val z = pos?.getOrNull(2)

        // homed_axes is lowercase (RESEARCH §3) — derive each axis flag by membership.
        val homed = state.homedAxes
        val xHomed = 'x' in homed
        val yHomed = 'y' in homed
        val zHomed = 'z' in homed

        return MoveVm(
            x = x,
            y = y,
            z = z,
            xHomed = xHomed,
            yHomed = yHomed,
            zHomed = zHomed,
            allHomed = xHomed && yHomed && zHomed,
        )
    }
}

/**
 * The Move panel view-model: live gcode X/Y/Z (null when unreported — never fabricated) and per-axis
 * homed gating ([allHomed] = all three present in `homed_axes`).
 */
data class MoveVm(
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val xHomed: Boolean = false,
    val yHomed: Boolean = false,
    val zHomed: Boolean = false,
    val allHomed: Boolean = false,
)
