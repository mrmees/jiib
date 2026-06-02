package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.render.ProgressRing
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Print Status home (SHELL-04) — the primary monitor surface (≈90% of interaction). Built on
 * [ScreenScaffold]; all color via [LocalTokens] (THEME-01); live numbers in GeistMono tabular numerals.
 *
 * Data is read STRICTLY from fields confirmed present in docs/moonraker-capabilities.md (real Ender 5 +
 * Ender 3) — no assumed fields. Layer info is nullable (slicer/state-dependent) → "—" fallback.
 *
 * ## Inc 1 (this pass — on-the-wire data only, NO new networking)
 *  - **Focus** (printing): [ProgressRing] with the % centered, filename + state, and a Z-height / layer
 *    line. (idle → a "Ready" temp readout.)
 *  - **Field**: a 3×2 stat grid — LAYER (cur/total) · FILAMENT (used) · NOZZLE (cur/target) · BED
 *    (cur/target) · ELAPSED (print_duration) · REMAINING (— until Inc 2 brings the slicer ETA).
 *  - **Gutter**: Stop (wired e-stop + [ConfirmGuard]); Tune/Pause are disabled placeholders.
 *
 * ## Deferred
 *  - Inc 2: `server.files.metadata` one-shot → thumbnail in the ring + total filament/layers + the
 *    slicer-file-estimate ETA (REMAINING / FINISH BY).
 *  - Inc 3: tap a temp cell → its setting page; wire the mid-print Tune button.
 *
 * @param container the service-locator (live `printerState` + the session dispatcher).
 */
@Composable
fun PrintStatusScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)

    var showEstopGuard by remember { mutableStateOf(false) }
    var failureText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> failureText = event.message
            }
        }
    }
    LaunchedEffect(failureText) {
        if (failureText != null) {
            delay(4_000)
            failureText = null
        }
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = { PrintStatusFocus(state = state) },
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatGrid(state = state, modifier = Modifier.fillMaxWidth().weight(1f))
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
                    // Tune/Pause are placeholders until Inc 3 / Phase 7; Stop is wired.
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
            ConfirmGuard(
                title = "Emergency stop?",
                message = "This halts the printer.",
                confirmLabel = "STOP",
                onConfirm = {
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
 * State-adaptive Focus (D-08): while printing/paused, the [ProgressRing] with the % centered + filename
 * and a Z-height / layer line beneath; otherwise a live "Ready" temp readout (never a bare 0% ring).
 */
@Composable
private fun PrintStatusFocus(state: PrinterState) {
    val t = LocalTokens.current
    val printing = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        if (printing) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.62f).aspectRatio(1f)) {
                    ProgressRing(progress = state.progress.toFloat(), modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "${(state.progress * 100).roundToInt()}%",
                            color = t.text,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = fsSp(34f, t.fs).sp,
                        )
                    }
                }
                state.printFilename.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = t.text,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(18f, t.fs).sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = "Z ${fmtZ(state)} · Layer ${fmtLayer(state.currentLayer, state.totalLayer)}",
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontSize = fsSp(16f, t.fs).sp,
                )
            }
        } else {
            val primary = primaryHeater(state)
            val bed = state.heaters["heater_bed"]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Benchy line art is the idle/no-job hero image (Matthew, 2026-06-01).
                Icon(
                    painter = painterResource(R.drawable.benchy),
                    contentDescription = null,
                    tint = t.text2,
                    modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1600f / 900f),
                )
                Text(
                    text = "Ready",
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(34f, t.fs).sp,
                )
                primary?.let {
                    Text("Nozzle ${fmt(it.temperature)}°", color = t.text2, fontFamily = GeistMono, fontSize = fsSp(18f, t.fs).sp)
                }
                bed?.let {
                    Text("Bed ${fmt(it.temperature)}°", color = t.text2, fontFamily = GeistMono, fontSize = fsSp(18f, t.fs).sp)
                }
            }
        }
    }
}

/**
 * The 3×2 print-stat grid (mockup 03-print-status.png). Reads only catalog-confirmed fields; a missing
 * source shows "—" (never fabricated). Totals (filament/layers) and the ETA arrive in Inc 2.
 */
@Composable
private fun StatGrid(state: PrinterState, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("LAYER", fmtLayer(state.currentLayer, state.totalLayer), t.text, Modifier.weight(1f).fillMaxHeight())
            StatCell("FILAMENT", fmtFilament(state.filamentUsed), t.text, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("NOZZLE", fmtHeater(nozzle), t.heat, Modifier.weight(1f).fillMaxHeight())
            StatCell("BED", fmtHeater(bed), t.heat, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("ELAPSED", fmtDuration(state.printDuration), t.text, Modifier.weight(1f).fillMaxHeight())
            StatCell("REMAINING", "—", t.text3, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** One stat cell: a small uppercase label over a GeistMono value (borderless, per mockup). */
@Composable
private fun StatCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = t.text3, fontFamily = GeistMono, fontSize = fsSp(13f, t.fs).sp)
            Text(value, color = valueColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(22f, t.fs).sp)
        }
    }
}

/** A greyed, disabled gutter placeholder (D-07) — neutral outline + faint label, no-op. */
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
        Text(label, color = t.text3, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = fsSp(18f, t.fs).sp)
    }
}

// --- formatters / resolution (catalog-aligned) -----------------------------------------------------

/** Primary nozzle heater: `extruder`, else the first `extruder`-prefixed heater (multi-tool naming). */
private fun primaryHeater(state: PrinterState): HeaterState? =
    state.heaters["extruder"] ?: state.heaters.entries.firstOrNull { it.key.startsWith("extruder") }?.value

/** "cur/target" when a target is set (>0), else just "cur"; "—" when the heater is absent. */
private fun fmtHeater(h: HeaterState?): String {
    if (h == null) return "—"
    return if (h.target > 0.0) "${fmt(h.temperature)}/${fmt(h.target)}" else fmt(h.temperature)
}

/** "cur/total" layers; "—" when total is unknown (idle / slicer didn't report — catalog fallback). */
private fun fmtLayer(current: Int?, total: Int?): String {
    if (total == null) return "—"
    return "${current ?: "–"}/$total"
}

/** Filament used in metres (1 decimal); "—" when nothing extruded yet. */
private fun fmtFilament(mm: Double): String =
    if (mm <= 0.0) "—" else "${((mm / 1000.0) * 10).roundToInt() / 10.0} m"

/** Live Z height (mm, 1 decimal) from gcode_position[2]; "—" until a position is known. */
private fun fmtZ(state: PrinterState): String =
    state.gcodePosition?.getOrNull(2)?.let { fmt(it) } ?: "—"

/** Duration as H:MM (≥1h) or M:SS (<1h); "—" when zero/none. */
private fun fmtDuration(seconds: Double): String {
    if (seconds <= 0.0) return "—"
    val total = seconds.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h >= 1) "$h:${m.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** Tabular-friendly one-decimal formatting, rounded (not truncated). */
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()
