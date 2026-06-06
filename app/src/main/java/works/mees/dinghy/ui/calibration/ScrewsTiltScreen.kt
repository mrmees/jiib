package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.calibration.ScrewPoint
import works.mees.dinghy.calibration.ScrewTurn
import works.mees.dinghy.calibration.ScrewsTiltHolder
import works.mees.dinghy.calibration.ScrewsTiltVm
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import kotlin.math.max

/**
 * The Screws-Tilt page (CALIB-02) — the OWNER-AUTHORED spatial layout (UI-SPEC §2, supersedes the
 * mockup card). The D-03 guided one-screw-at-a-time loop driven off the STRUCTURED `screws_tilt_adjust`
 * object (never console text), surfaced through [ScrewsTiltHolder].
 *
 * ## Verbatim render (prior-wave correction, commit ef260cb)
 * The worst screw, its `adjust` clock string, `sign`, joined `name`, and the "X of N in tolerance"
 * count come straight off [ScrewsTiltVm.loop] — this screen NEVER re-derives "worst" or reintroduces a
 * `|z|` ranking. The holder owns that math via `parseScrewsTilt`.
 *
 * ## Layout
 *  - **Focus** = a to-scale representation of the bed derived from the screw coords ([ScrewPoint.x]/
 *    [ScrewPoint.y]). Each point is a state glyph at its real location (`point_scan` unmeasured /
 *    `rotate_left` CCW / `rotate_right` CW / `commit` green in-tol) with a `clock_loader_10` direction
 *    indicator rotated to point from bed-center toward the screw. The Focus headline = "X of N in
 *    tolerance" + the worst screw's turn (Geist Mono Display). GRACEFUL FALLBACK (D-06): when no coords
 *    are available ([ScrewsTiltVm.hasCoords] == false) the Focus shows only the headline.
 *  - **Field** = the point list; each row ratio-split 20%/40%/40% (`clock_loader_10` indicator /
 *    `hh:mm` turn (Mono) / degrees (Mono)). Generic N-screw (D-04) — the list is VERTICALLY SCROLLABLE
 *    so a 5/6-screw bed never overflows; do NOT assume exactly 3/4 rows.
 *  - **Gutter** = `Run` (blue, dispatches `SCREWS_TILT_CALCULATE`, gated on homed — D-13 inline Home
 *    when unhomed) + `Back` (green). NOTHING is applied (one-shot, D-05 — Run = (re)probe, Back = leave;
 *    no Accept/Cancel).
 *
 * Dispatcher `Failure` (the printer's redacted RpcError text) surfaces as an error [SeverityToast]
 * (T-09-04-02 — never the transport `e.message`). All sizing ratio-only; all color via [LocalTokens].
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the headless [ScrewsTiltHolder] (guided loop + coords + homed gate + error).
 * @param onBack    the neutral Back gutter exit (D-10).
 */
@Composable
fun ScrewsTiltScreen(
    container: AppContainer,
    holder: ScrewsTiltHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()

    var failureText by remember { mutableStateOf<String?>(null) }

    // Surface a dispatch failure (already redacted by the dispatcher) as an error toast (T-09-04-02).
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
    // The holder's folded error (e.g. a Failure that arrived before this collector attached).
    val errorText = failureText ?: vm.errorText

    val runKey = CommandRegistry.screwsTiltCalculate.dispatchKey(Unit)
    val running = runKey in inFlight

    // Measured turns surface only after a Run COMPLETES this load: armed (Run tapped this visit) AND not
    // running. False on entry (fresh `remember`) and WHILE a run is in flight — so a returning user and an
    // in-progress run never show stale/previous turn directions; flips true the instant the run finishes.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed, running) { holder.setShowResults(armed && !running) }

    fun runProbe() {
        if (running) return
        dispatcher?.dispatch(CommandRegistry.screwsTiltCalculate, Unit)
        armed = true
    }
    fun home() {
        val key = CommandRegistry.homeAll.dispatchKey(Unit)
        if (key in inFlight) return
        dispatcher?.dispatch(CommandRegistry.homeAll, Unit)
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            // Portrait = an even vertical focus/field split (default weighted 50/50) — NOT an aspect-locked
            // square focus; the bed sits centered in the top half (owner: don't force-fill the focus frame).
            focus = {
                ScrewsTiltFocus(vm = vm, modifier = Modifier.fillMaxSize().padding(8.dp))
            },
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScrewPointList(vm = vm, modifier = Modifier.fillMaxWidth().weight(1f))
                    errorText?.let { SeverityToast(Severity.Error, it, Modifier.fillMaxWidth()) }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // D-13 pre-flight: Run is gated on homed; offer an inline blue Home when unhomed.
                    if (!vm.homedGate) {
                        ScrewsTiltActionControl(
                            label = "Home",
                            onClick = ::home,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                        )
                    } else {
                        ScrewsTiltActionControl(
                            label = if (running) "Running" else "Run",
                            onClick = ::runProbe,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            enabled = !running,
                        )
                    }
                    ScrewsTiltActionControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral, // D-10: plain nav spends no safety color.
                    )
                }
            },
        )
    }
}

/**
 * Focus = the to-scale bed, centered and filling the pane. Each point is a state glyph at its real bed
 * location + the screw name (see [BedScale]). The tolerance count + worst-screw readout were removed
 * (owner: they cluttered the Focus) — per-screw status reads off the bed glyphs and the Field list.
 * D-06 fallback: when no coords are available, a short prompt shows instead.
 */
@Composable
private fun ScrewsTiltFocus(vm: ScrewsTiltVm, modifier: Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (vm.hasCoords) {
            BedScale(
                vm = vm,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(shape)
                    .border(BorderStroke(2.dp, t.outline), shape)
                    .background(t.surface),
            )
        } else {
            Text(
                text = if (vm.loop.totalScrews > 0) "Bed screw map unavailable" else "Run to probe the bed screws",
                color = t.text2,
                fontFamily = Geist,
                fontSize = fsSp(16f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A to-scale square bed with location-accurate point indicators. Bed extents come from the screw-coord
 * bounding box (with a margin so edge screws aren't clipped); the printer Y axis (up) is flipped to
 * screen Y (down). Each point is its state glyph; a `clock_loader_10` wedge is rotated to point from
 * bed-center toward the screw (bearing derived at runtime — NOT hardcoded per-screw, UI-SPEC §2).
 */
@Composable
private fun BedScale(vm: ScrewsTiltVm, modifier: Modifier) {
    val t = LocalTokens.current
    val coordPoints = vm.points.filter { it.x != null && it.y != null }
    if (coordPoints.isEmpty()) {
        Box(modifier)
        return
    }

    val minX = coordPoints.minOf { it.x!! }
    val maxX = coordPoints.maxOf { it.x!! }
    val minY = coordPoints.minOf { it.y!! }
    val maxY = coordPoints.maxOf { it.y!! }
    // Margin so corner screws sit inside the drawn bed, not on its edge.
    val spanX = max(maxX - minX, 1.0)
    val spanY = max(maxY - minY, 1.0)
    val padX = spanX * 0.18
    val padY = spanY * 0.18
    val loX = minX - padX
    val hiX = maxX + padX
    val loY = minY - padY
    val hiY = maxY + padY
    Box(modifier) {
        // State glyph + name label at each screw's real bed position. (No wedge/tick chrome — the
        // rotate_left/rotate_right/commit/anchor glyph already conveys direction; the faint rotated
        // ticks just read as askew stray dots.)
        BoxWithPoints(coordPoints, loX, hiX, loY, hiY)
    }
}

/** Place a state glyph at each point's fractional bed position (printer-Y flipped). */
@Composable
private fun BoxWithPoints(
    points: List<ScrewPoint>,
    loX: Double,
    hiX: Double,
    loY: Double,
    hiY: Double,
) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxSize()) {
        // Use a simple overlay: each glyph is positioned via fractional alignment using a nested Box.
        points.forEach { p ->
            val fx = ((p.x!! - loX) / (hiX - loX)).toFloat().coerceIn(0f, 1f)
            val fy = (1f - ((p.y!! - loY) / (hiY - loY)).toFloat()).coerceIn(0f, 1f)
            val turn = p.turn
            // turn == null → not yet measured: neutral point_scan, name only (no turn glyph / height).
            val glyph = when {
                turn == null -> "point_scan"
                turn.isBase -> "anchor"
                turn.isInTol -> "commit"
                turn.sign == "CCW" -> "rotate_left"
                turn.sign == "CW" -> "rotate_right"
                else -> "point_scan"
            }
            val tint = when {
                turn == null -> t.text2         // not yet measured — neutral
                turn.isBase -> t.text2          // anchor / reference — neutral
                turn.isInTol -> t.accent        // within tolerance → theme accent
                turn.sign == "CW" -> t.go       // clockwise → green
                turn.sign == "CCW" -> t.stop    // counterclockwise → red
                else -> t.accent
            }
            Box(
                Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = BiasAlignment(fx, fy),
            ) {
                // Glyph + the screw's name beneath it (UI-SPEC §2 line: "the point shows its name").
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MaterialSymbol(name = glyph, tint = tint, sizeSp = fsSp(56f, t.fs))
                    p.name?.let { name ->
                        Text(
                            text = shortScrewName(name),
                            color = t.text2,
                            fontFamily = Geist,
                            fontWeight = FontWeight.Medium,
                            fontSize = fsSp(22f, t.fs).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Measured probe height (mm, 3 decimals) — only once measured this run.
                    if (turn != null) {
                        Text(
                            text = "${"%.3f".format(turn.z)} mm",
                            color = t.text3,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = fsSp(16f, t.fs).sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Trim a trailing " screw" so corner labels stay short on the bed ("front left screw" → "front left"). */
private fun shortScrewName(name: String): String =
    name.trim().removeSuffix("screw").trim().ifEmpty { name.trim() }

/** Map a 0..1 fraction to Compose's -1..1 bias for [androidx.compose.ui.BiasAlignment]. */
private fun BiasAlignment(fx: Float, fy: Float): Alignment =
    androidx.compose.ui.BiasAlignment(horizontalBias = fx * 2f - 1f, verticalBias = fy * 2f - 1f)

/**
 * The point list (Field). Only 3–4 screws on this bed (owner), so the rows EXPAND to fill the pane
 * (weighted Column, no scroll). Built from [ScrewsTiltVm.points] (the config layout) — the named rows
 * show even before a Run; a row's measured turn (minutes/degrees) appears only once a run completes.
 */
@Composable
private fun ScrewPointList(vm: ScrewsTiltVm, modifier: Modifier) {
    val t = LocalTokens.current
    val rows = vm.points
    if (rows.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Run to probe the bed screws.",
                color = t.text2,
                fontFamily = Geist,
                fontSize = fsSp(16f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { point ->
            ScrewRow(point = point, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/**
 * One screw row, expanded to fill its share of the Field: an ~80%-height status glyph + the screw name
 * (title) over the minutes (left) ⟷ degrees (right) adjustment. When the screw is NOT yet measured
 * ([ScrewPoint.turn] == null) the row shows only the name + a neutral `point_scan` icon — no turn data.
 */
@Composable
private fun ScrewRow(point: ScrewPoint, modifier: Modifier) {
    val t = LocalTokens.current
    val turn = point.turn
    val glyph = when {
        turn == null -> "point_scan"
        turn.isBase -> "anchor"
        turn.isInTol -> "commit"
        turn.sign == "CW" -> "rotate_right"
        turn.sign == "CCW" -> "rotate_left"
        else -> "point_scan"
    }
    val tint = when {
        turn == null -> t.text2          // not yet measured — neutral
        turn.isBase -> t.text2           // anchor / reference — neutral
        turn.isInTol -> t.accent         // within tolerance → theme accent
        turn.sign == "CW" -> t.go        // clockwise → green
        turn.sign == "CCW" -> t.stop     // counterclockwise → red
        else -> t.accent
    }
    val needsTurn = turn != null && !turn.isBase && !turn.isInTol
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(t.rCtrl))
            .background(t.surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val glyphSp = maxHeight.value * 0.8f
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // [icon] ~80%-height status glyph — the only direction/status cue.
            Box(Modifier.weight(0.2f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                MaterialSymbol(name = glyph, tint = tint, sizeSp = glyphSp)
            }
            // [remainder] name title; the minutes (left) ⟷ degrees (right) split shows only when measured.
            Column(
                Modifier.weight(0.8f).fillMaxHeight().padding(start = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = point.name ?: point.key,
                    color = t.text,
                    fontFamily = Geist,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fsSp(24f, t.fs).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (turn != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val valueColor = if (needsTurn) t.text else t.text3
                        // Degrees signed by direction: a CCW turn reads negative (e.g. -66°), CW positive.
                        // Zero-guard avoids "-0°" on the base/zero-turn screw.
                        val degMag = "%.0f".format(turn.degrees)
                        val degText = if (turn.sign == "CCW" && degMag != "0") "-$degMag°" else "$degMag°"
                        Text(
                            text = turn.adjust,
                            color = valueColor,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(22f, t.fs).sp,
                            maxLines = 1,
                        )
                        Text(
                            text = degText,
                            color = valueColor,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(22f, t.fs).sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrewsTiltActionControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = if (enabled) intentColor(intent, t) else t.hair
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, outline), shape)
        .background(if (enabled) androidx.compose.ui.graphics.Color.Transparent else t.surface)
        .padding(horizontal = 12.dp, vertical = 18.dp)
    val clickable = if (enabled) base.clickable(onClick = onClick) else base
    Box(clickable, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = if (enabled) t.text else t.text3,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}
