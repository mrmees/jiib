package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
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
                    StopButton(
                        onTap = { showEstopGuard = true },
                        onHold = { dispatcher?.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP) },
                        modifier = Modifier.weight(1f),
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
 * Focus: the [ProgressRing] is ALWAYS drawn (gray track when idle — progress 0 shows only the
 * surface2 well; accent arc fills while printing). The ring CENTER is the "preview" slot — the live
 * % while printing, the Benchy no-job image when idle. Beneath: filename + Z/layer while printing,
 * else "Ready". Temps are NOT repeated here — they live in the field grid (Matthew, 2026-06-01).
 */
@Composable
private fun PrintStatusFocus(state: PrinterState) {
    val t = LocalTokens.current
    val printing = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        // The ring is ~90% of the focus's SMALLER dimension (largest circle that fits, both orientations).
        val ringSize = minOf(maxWidth, maxHeight) * 0.9f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(ringSize)) {
                ProgressRing(
                    progress = if (printing) state.progress.toFloat() else 0f,
                    modifier = Modifier.fillMaxSize(),
                )
                // Preview slot: ~90% of the ring, circle-clipped (corners drop — preview isn't edge-to-edge).
                // Idle → the Benchy no-job image (theme-accent tinted); Inc 2 puts the gcode thumbnail here.
                Box(
                    Modifier.fillMaxSize(0.9f).align(Alignment.Center).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!printing) {
                        Icon(
                            painter = painterResource(R.drawable.benchy),
                            contentDescription = null,
                            tint = t.accent2,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1600f / 900f),
                        )
                    }
                }
                // Status text CENTERED ON the bottom of the circle (its center at box-center + R, R =
                // ringSize/2 = the 6-o'clock point of the drawn ring): "Ready" idle → "NN%" printing.
                Text(
                    text = if (printing) "${(state.progress * 100).roundToInt()}%" else "READY",
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(30f, t.fs).sp,
                    modifier = Modifier.align(Alignment.Center).offset(y = ringSize / 2),
                )
            }
            if (printing) {
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
                    text = "Z ${fmtZ(state)} · Layer ${state.currentLayer ?: "—"}/${state.totalLayer ?: "—"}",
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontSize = fsSp(16f, t.fs).sp,
                )
            }
        }
    }
}

/**
 * The 3×2 icon-led stat grid (mockup 03-print-status.png) — glanceable from across the room. Reads only
 * catalog-confirmed fields; a missing source shows "—" (never fabricated). Two cell shapes:
 *  - [IconTwoRowCell] (icon | active-over-inactive): Z height (altitude), Layer (layers), Nozzle/Bed temp.
 *  - [IconValueCell] (icon | single value): Elapsed (timer_arrow_up), Remaining (timer_arrow_down).
 * The "final height" (Z) and Remaining (ETA) need file metadata → "—" until Inc 2.
 */
@Composable
private fun StatGrid(state: PrinterState, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTwoRowCell(
                icon = { sp -> MaterialSymbol("altitude", tint = t.text2, sizeSp = sp) },
                active = fmtZ(state), inactive = "—", activeColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconTwoRowCell(
                icon = { sp -> MaterialSymbol("layers", tint = t.text2, sizeSp = sp) },
                active = state.currentLayer?.toString() ?: "—",
                inactive = state.totalLayer?.toString() ?: "—",
                activeColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTwoRowCell(
                icon = { sp -> DrawableIcon(R.drawable.nozzle, t.heat, sp) },
                active = tempActive(nozzle), inactive = tempInactive(nozzle), activeColor = t.heat,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconTwoRowCell(
                icon = { sp -> DrawableIcon(R.drawable.heat_bed, t.heat, sp) },
                active = tempActive(bed), inactive = tempInactive(bed), activeColor = t.heat,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconValueCell(
                icon = { sp -> MaterialSymbol("timer_arrow_up", tint = t.text2, sizeSp = sp) },
                value = fmtDuration(state.printDuration), valueColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconValueCell(
                icon = { sp -> MaterialSymbol("timer_arrow_down", tint = t.text2, sizeSp = sp) },
                value = "—", valueColor = t.text3,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** A bespoke vector glyph (nozzle / heat_bed) tinted to a token, sized to the cell (dp ≈ the icon sp). */
@Composable
private fun DrawableIcon(resId: Int, tint: Color, sizeSp: Float) {
    Icon(painter = painterResource(resId), contentDescription = null, tint = tint, modifier = Modifier.size(sizeSp.dp))
}

/** Icon font size as a fraction of the cell height — kept SMALL so the icon is a quiet indicator and
 * the reading is the hero (Matthew: big icons distract from the values). */
private const val CELL_ICON_FRACTION = 0.45f

/**
 * Icon (LEFT, scaled to the cell height) | active-over-inactive value (RIGHT-aligned). Active =
 * bold/bright, inactive = dim/smaller. SpaceBetween pins the icon left and the values right.
 */
@Composable
private fun IconTwoRowCell(
    icon: @Composable (sizeSp: Float) -> Unit,
    active: String,
    inactive: String,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier) {
        val iconSp = maxHeight.value * CELL_ICON_FRACTION
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(iconSp)
            Column(horizontalAlignment = Alignment.End) {
                Text(active, color = activeColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(26f, t.fs).sp)
                Text(inactive, color = t.text3, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(17f, t.fs).sp)
            }
        }
    }
}

/** Icon (LEFT, scaled to the cell height) | single value (RIGHT-aligned) — the time cells. */
@Composable
private fun IconValueCell(
    icon: @Composable (sizeSp: Float) -> Unit,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier) {
        val iconSp = maxHeight.value * CELL_ICON_FRACTION
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(iconSp)
            Text(value, color = valueColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(26f, t.fs).sp)
        }
    }
}

/**
 * The gutter Stop: a red `crisis_alert` glyph (no label). TAP opens the e-stop [ConfirmGuard]; HOLD
 * (>~½ s, the system long-press) fires the e-stop IMMEDIATELY (the panic path, with haptic) — Matthew.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StopButton(onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.stop), shape)
            .combinedClickable(onClick = onTap, onLongClick = onHold),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol("crisis_alert", tint = t.stop, sizeSp = fsSp(32f, t.fs))
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

/** Active (current) temp; "—" when the heater is absent. No degree symbol (saves space — Matthew). */
private fun tempActive(h: HeaterState?): String = h?.let { fmt(it.temperature) } ?: "—"

/** Inactive (target) temp; "—" when off (target 0) or absent. */
private fun tempInactive(h: HeaterState?): String =
    h?.takeIf { it.target > 0.0 }?.let { fmt(it.target) } ?: "—"

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
