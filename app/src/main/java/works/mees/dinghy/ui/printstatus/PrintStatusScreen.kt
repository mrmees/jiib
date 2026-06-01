package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.render.GraphViewHost
import works.mees.dinghy.render.ProgressRing
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Print Status home (SHELL-04, part 1 of 2) — the single state-adaptive monitor/landing surface
 * (D-08): printing shows the [ProgressRing]; idle shows a live temp/status "Ready" readout. Built on
 * [ScreenScaffold] (Focus/Field/Gutter, docs/ui_design/LAYOUT.md); all color routes through
 * [LocalTokens] role tokens (THEME-01) and all live numbers use GeistMono tabular numerals.
 *
 * ## Focus (state-adaptive, D-08)
 * `printState ∈ {Printing, Paused}` → a [ProgressRing] in a sacred `aspectRatio(1f)` square driven by
 * `PrinterState.progress`. Otherwise (idle/Ready) → a temp/status readout that FILLS the Focus usefully
 * (design philosophy "fill usable space") — never a bare 0% ring.
 *
 * ## Field (the 2×3 grid + a RESERVED sparkline slot, review #4)
 * The numeric stat grid is read from [PrintStatusHolder.grid] (primary cur/target, secondary or
 * omitted per the fallback rules, progress%, filename). A sized, positioned sparkline placeholder is
 * reserved in the Field so 04-06b's GraphView sparkline is an ADDITIVE fill, not a relayout — the
 * Views graph host is deliberately NOT hosted here (it lands in 04-06b with the combined-render gate).
 *
 * ## Gutter (D-07) + Stop → ConfirmGuard (SHELL-02/D-10, review #1)
 * Three tiles: a wired Stop ([Intent.Danger]) plus greyed/disabled Tune + Pause "coming soon"
 * placeholders (Phase 5/7). Tapping Stop raises the full-screen [ConfirmGuard]; confirming dispatches
 * `printer.emergency_stop` through the per-session [works.mees.dinghy.command.CommandDispatcher] (NOT a
 * raw rpc) — the ConfirmGuard itself dispatches nothing (PRIM-03). Firing it drives klippy_state →
 * shutdown, which `TopRoute.derive()` routes to the Splash recovery surface automatically (D-10/04-05);
 * this screen never navigates manually. A dispatcher [DispatchEvent.Failure] surfaces a
 * [SeverityToast].
 *
 * Renders at the store's already-throttled cadence (no second sampling layer here).
 *
 * @param container the service-locator (provides the live `printerState` + the session dispatcher).
 * @param holder    the toolkit-agnostic [PrintStatusHolder] (grid model + primary-heater ring).
 */
@Composable
fun PrintStatusScreen(
    container: AppContainer,
    holder: PrintStatusHolder,
    modifier: Modifier = Modifier,
) {
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val grid by holder.grid.collectAsStateWithLifecycle()
    val sparkline by holder.sparkline.collectAsStateWithLifecycle()
    val tokens = LocalTokens.current

    var showEstopGuard by remember { mutableStateOf(false) }
    var failureText by remember { mutableStateOf<String?>(null) }

    // Surface a dispatch failure (redacted message) as an error toast (PRIM-04).
    // Reset stale failure from a previous session when the dispatcher key changes.
    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> failureText = event.message
            }
        }
    }

    // Auto-dismiss the error toast after 4 s so the stat grid is not permanently obscured.
    LaunchedEffect(failureText) {
        if (failureText != null) {
            delay(4_000)
            failureText = null
        }
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                PrintStatusFocus(state = state)
            },
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatGrid(grid = grid, modifier = Modifier.fillMaxWidth().weight(1f))
                    // Heater sparkline filling the slot 04-06 reserved (review #4): the Phase-3
                    // classic-Views GraphView hosted via GraphViewHost/AndroidView (ADR 0001, D-09 —
                    // the in-anger Views render proof). Fed by the holder's primary-heater RingBuffer
                    // snapshot at the store's throttled cadence (no second sampling layer). Passing the
                    // live LocalTokens means a theme flip recolors the Canvas (push-tokens seam, D-06).
                    GraphViewHost(
                        tokens = tokens,
                        snapshot = sparkline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.6f),
                    )
                    failureText?.let { msg ->
                        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
                    }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Greyed Tune/Pause placeholders (D-07) — no-op until Phase 5/7.
                    DisabledTile(label = "Tune", modifier = Modifier.weight(1f))
                    DisabledTile(label = "Pause", modifier = Modifier.weight(1f))
                    OutlinedControl(
                        label = "Stop",
                        onClick = { showEstopGuard = true },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger,
                    )
                }
            },
        )

        if (showEstopGuard) {
            // Full-screen safety gate (PRIM-03). ConfirmGuard dispatches NOTHING — onConfirm does.
            ConfirmGuard(
                title = "Emergency stop?",
                message = "This halts the printer.",
                confirmLabel = "STOP",
                onConfirm = {
                    // Route the destructive halt through the per-session dispatcher (review #1),
                    // never a raw transport request. Firing drives klippy → shutdown → Splash (04-05).
                    dispatcher?.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP)
                    showEstopGuard = false
                },
                onCancel = { showEstopGuard = false },
                destructive = true,
            )
        }
    }
}

/**
 * State-adaptive Focus (D-08): the [ProgressRing] while printing/paused, else a live temp/status
 * readout that fills the region — never a bare 0% ring on idle.
 */
@Composable
private fun PrintStatusFocus(state: PrinterState) {
    val t = LocalTokens.current
    val printing = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (printing) {
            Box(Modifier.aspectRatio(1f).padding(12.dp)) {
                ProgressRing(progress = state.progress.toFloat(), modifier = Modifier.fillMaxSize())
            }
        } else {
            // Idle "Ready" readout — current primary/secondary temps fill the Focus usefully.
            val primary = state.heaters["extruder"]
                ?: state.heaters.entries.firstOrNull { it.key.startsWith("extruder") }?.value
            val bed = state.heaters["heater_bed"]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Ready",
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(34f, t.fs).sp,
                    textAlign = TextAlign.Center,
                )
                primary?.let {
                    Text(
                        text = "Nozzle ${fmt(it.temperature)}°",
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontSize = fsSp(18f, t.fs).sp,
                    )
                }
                bed?.let {
                    Text(
                        text = "Bed ${fmt(it.temperature)}°",
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontSize = fsSp(18f, t.fs).sp,
                    )
                }
            }
        }
    }
}

/** The 2×3 numeric stat grid; empty cells render a "—" placeholder, never a fabricated value. */
@Composable
private fun StatGrid(grid: PrintStatusGrid, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 2) {
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 0 until 3) {
                    StatCell(
                        cell = grid.cells.getOrNull(row * 3 + col),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** One grid cell — a typed readout, or a "—" placeholder when the slot has no source (review #9). */
@Composable
private fun StatCell(cell: PrintStatusCell?, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier, contentAlignment = Alignment.Center) {
        when (cell) {
            is HeaterCell -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label(cell.objectName),
                    color = t.text3,
                    fontFamily = GeistMono,
                    fontSize = fsSp(12f, t.fs).sp,
                )
                Text(
                    text = cell.target?.let { "${fmt(cell.current)}/${fmt(it)}" } ?: fmt(cell.current),
                    color = t.heat,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(20f, t.fs).sp,
                )
            }

            is ProgressCell -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PROG", color = t.text3, fontFamily = GeistMono, fontSize = fsSp(12f, t.fs).sp)
                Text(
                    text = "${(cell.fraction * 100).toInt()}%",
                    color = t.accent2,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(20f, t.fs).sp,
                )
            }

            is InfoCell -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cell.label, color = t.text3, fontFamily = GeistMono, fontSize = fsSp(12f, t.fs).sp)
                Text(
                    text = cell.value,
                    color = t.text,
                    fontFamily = GeistMono,
                    fontSize = fsSp(16f, t.fs).sp,
                )
            }

            null -> Text(
                text = "—",
                color = t.text3,
                fontFamily = GeistMono,
                fontSize = fsSp(20f, t.fs).sp,
            )
        }
    }
}

/**
 * A greyed, disabled gutter placeholder (D-07) — neutral outline + faint label, no-op. The "coming
 * soon" Tune/Pause controls land in Phase 5/7; here they are inert so the gutter grid is complete.
 */
@Composable
private fun DisabledTile(label: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(t.rCtrl))
            .border(BorderStroke(2.dp, t.hair), RoundedCornerShape(t.rCtrl)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = t.text3,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}

/**
 * Short uppercase label for a heater object name.
 * - `extruder` → "NOZZLE"
 * - `extruder1`, `extruder2`, … → "NOZZLE 1", "NOZZLE 2", … (avoids duplicate "NOZZLE" in multi-tool grids)
 * - `heater_bed` → "BED"
 * - `heater_generic <name>` → uppercased name (verbatim nonstandard heater)
 * - anything else → uppercased verbatim
 */
private fun label(objectName: String): String = when {
    objectName == "extruder" -> "NOZZLE"
    objectName.startsWith("extruder") -> "NOZZLE ${objectName.removePrefix("extruder")}"
    objectName == "heater_bed" -> "BED"
    objectName.startsWith("heater_generic ") -> objectName.removePrefix("heater_generic ").uppercase()
    else -> objectName.uppercase()
}

/** Tabular-friendly one-decimal temperature formatting, rounded (not truncated). */
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()
