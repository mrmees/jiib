package works.mees.dinghy.ui.printstatus

import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

/**
 * The Phase-16 four-state Print-Status classifier (the phase's central architectural intent —
 * ONE classifier, CONTEXT domain + RESEARCH §1). A pure, host-testable function of
 * [PrinterState.printState] ONLY — it deliberately reads NOTHING else (not `printFilename`, not
 * `lastJob`/`restartFilename`, not `klippyState`).
 *
 * **CRITICAL behavior change vs `derivePrintStatusControls`:** `Standby` is ALWAYS [Standby]. The
 * legacy `PrintStatusControlModel` rendered TERMINAL controls when a leftover `restartFilename`
 * existed (`PrintStatusControlModel.kt:67`) — the classifier MUST NOT copy that masquerade. A
 * leftover/stale filename does not flip Standby to a terminal mode; restart-from-idle moves to the
 * Standby launcher Files tile (16-06), not a terminal gutter.
 *
 * The Klippy host lifecycle ([PrinterState.klippyState]) is a SEPARATE axis (STATE-04) — a
 * shutdown/error there must NOT manufacture a [Terminal] mode here; routing on klippy lifecycle
 * lives elsewhere (the Splash). This classifier is print-state-only.
 */
sealed interface PrintStatusMode {
    /** Idle — no active/finished job to present (always Standby for `PrintState.Standby`). */
    data object Standby : PrintStatusMode

    /** A job is actively printing. */
    data object Printing : PrintStatusMode

    /** A job is paused (the user is intentionally intervening). */
    data object Paused : PrintStatusMode

    /** A job has ended — [kind] distinguishes the three terminal outcomes. */
    data class Terminal(val kind: TerminalKind) : PrintStatusMode
}

/** The three terminal outcomes a finished job can land in. */
enum class TerminalKind { Complete, Cancelled, Error }

/**
 * Map the raw 6-value [PrintState] to the 4-state [PrintStatusMode]. Reads ONLY
 * [PrinterState.printState] — see the class doc on the Standby-stays-Standby behavior change and
 * the print-state-only (klippy-ignored) discipline.
 */
fun classifyPrintStatus(state: PrinterState): PrintStatusMode = when (state.printState) {
    PrintState.Printing -> PrintStatusMode.Printing
    PrintState.Paused -> PrintStatusMode.Paused
    PrintState.Complete -> PrintStatusMode.Terminal(TerminalKind.Complete)
    PrintState.Cancelled -> PrintStatusMode.Terminal(TerminalKind.Cancelled)
    PrintState.Error -> PrintStatusMode.Terminal(TerminalKind.Error)
    PrintState.Standby -> PrintStatusMode.Standby
}

/**
 * Babystep early-first-layer gating (RESEARCH Pattern 4): the Z-babystep row is shown ONLY while
 * the print is in its early-layer window, when nudging the offset is meaningful.
 *
 * - `settingEnabled == false` -> HIDDEN (the app setting is off).
 * - [currentLayer] == null -> HIDDEN. There is NO time fallback — a null layer means the slicer
 *   never reported layer info, so we do not guess.
 * - [currentLayer] <= [layerThreshold] -> SHOWN.
 * - [currentLayer] > [layerThreshold] -> HIDDEN.
 */
fun babystepVisible(settingEnabled: Boolean, currentLayer: Int?, layerThreshold: Int): Boolean =
    settingEnabled && (currentLayer?.let { it <= layerThreshold } == true)

/**
 * Advance the babystep step size through the single canonical fixed cycle
 * [PrinterCommands.BABYSTEP_STEPS] (`0.02 -> 0.05 -> 0.10 -> 0.15 -> 0.20 -> 0.02`). The step list
 * is owned by [PrinterCommands] (16-03, the single source of truth) — this fn REFERENCES it, never
 * redefines it. A [current] value that is not an exact member snaps to the nearest member first,
 * then advances (so an off-grid value can never strand the cycle).
 */
fun nextBabystepStep(current: Double): Double {
    val steps = PrinterCommands.BABYSTEP_STEPS
    val nearestIndex = steps.indices.minByOrNull { kotlin.math.abs(steps[it] - current) } ?: 0
    return steps[(nearestIndex + 1) % steps.size]
}
