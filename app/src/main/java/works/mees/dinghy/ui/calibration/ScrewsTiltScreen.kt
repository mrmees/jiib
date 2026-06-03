package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.calibration.ScrewPoint
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
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

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
 * @param onBack    the green Back gutter exit.
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

    fun runProbe() {
        if (running) return
        dispatcher?.dispatch(CommandRegistry.screwsTiltCalculate, Unit)
    }
    fun home() {
        val key = CommandRegistry.homeAll.dispatchKey(Unit)
        if (key in inFlight) return
        dispatcher?.dispatch(CommandRegistry.homeAll, Unit)
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            portraitFocusAspect = 1f,
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
                        intent = Intent.Go,
                    )
                }
            },
        )
    }
}

/**
 * Focus = the headline ("X of N in tolerance" + the worst turn) over the to-scale bed. The bed extents
 * are derived from the screw-coord bounding box (with margin); each point sits at its real location.
 * D-06 fallback: when no coords are available, only the headline shows.
 */
@Composable
private fun ScrewsTiltFocus(vm: ScrewsTiltVm, modifier: Modifier) {
    val t = LocalTokens.current
    val loop = vm.loop
    val shape = RoundedCornerShape(t.rCard)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Headline — VERBATIM from the loop (no re-rank).
        val countLine = if (loop.totalScrews > 0) {
            "${loop.inToleranceCount} of ${loop.totalScrews} in tolerance"
        } else {
            "Run to probe the bed screws"
        }
        Text(
            text = countLine,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(20f, t.fs).sp,
            modifier = Modifier.fillMaxWidth(),
        )
        loop.worstScrew?.let { worst ->
            val turn = "${worst.adjust} ${worst.sign.orEmpty()}".trim()
            Text(
                text = turn,
                color = t.heat,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(40f, t.fs).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            worst.name?.let { name ->
                Text(
                    text = name,
                    color = t.text2,
                    fontFamily = Geist,
                    fontSize = fsSp(15f, t.fs).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // The to-scale bed (sacred square), centered, only when real coords exist (D-06).
        if (vm.hasCoords) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                BedScale(
                    vm = vm,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(shape)
                        .border(BorderStroke(2.dp, t.outline), shape)
                        .background(t.surface),
                )
            }
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
    val cx = (loX + hiX) / 2.0
    val cy = (loY + hiY) / 2.0

    Box(modifier) {
        // The wedge-pointer chrome (faint) is drawn on a Canvas under the glyphs; the glyphs themselves
        // are placed with offsets so MaterialSymbol's font glyph can carry the per-state icon.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val drawSpanX = (hiX - loX).toFloat()
            val drawSpanY = (hiY - loY).toFloat()
            coordPoints.forEach { p ->
                val fx = ((p.x!! - loX) / drawSpanX).toFloat()
                // printer Y up → screen Y down (flip).
                val fy = (1f - ((p.y!! - loY) / drawSpanY)).toFloat()
                val px = fx * w
                val py = fy * h

                // Bearing from bed-center toward the screw, with the printer-Y-up→screen-Y-down flip and
                // the icon's native wedge offset (~30° from 12-o'clock). atan2 here yields the screen-space
                // angle (clockwise from +x); the wedge baseline points up, so add 90°, then the asset's
                // ~30° native wedge-center offset. Derived per-point — not a hardcoded angle table.
                val dx = (p.x!! - cx).toFloat()
                val dyScreen = -(p.y!! - cy).toFloat() // flip to screen space
                val angleDeg = Math.toDegrees(atan2(dyScreen.toDouble(), dx.toDouble())).toFloat()
                val wedgeRotation = angleDeg + 90f + WEDGE_NATIVE_OFFSET_DEG

                rotate(degrees = wedgeRotation, pivot = Offset(px, py)) {
                    // a faint radial tick toward the screw, the size cue for the wedge direction.
                    drawCircle(
                        color = t.outline,
                        radius = min(w, h) * 0.012f,
                        center = Offset(px, py - min(w, h) * 0.06f),
                    )
                }
            }
        }
        // State glyphs at each point.
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
            val glyph = when {
                turn.isInTol -> "commit"
                turn.sign == "CCW" -> "rotate_left"
                turn.sign == "CW" -> "rotate_right"
                else -> "point_scan"
            }
            val tint = if (turn.isInTol) t.go else t.accent
            Box(
                Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = BiasAlignment(fx, fy),
            ) {
                MaterialSymbol(name = glyph, tint = tint, sizeSp = fsSp(28f, t.fs))
            }
        }
    }
}

/** Map a 0..1 fraction to Compose's -1..1 bias for [androidx.compose.ui.BiasAlignment]. */
private fun BiasAlignment(fx: Float, fy: Float): Alignment =
    androidx.compose.ui.BiasAlignment(horizontalBias = fx * 2f - 1f, verticalBias = fy * 2f - 1f)

/**
 * The point list (Field). Each row ratio-split 20%/40%/40% (indicator / `hh:mm` turn / degrees), Mono
 * for the values. VERTICALLY SCROLLABLE (D-04 — never assume 3/4 rows; a 5/6-screw bed scrolls).
 */
@Composable
private fun ScrewPointList(vm: ScrewsTiltVm, modifier: Modifier) {
    val t = LocalTokens.current
    val rows = vm.loop.screws
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
    LazyColumn(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            val glyph = when {
                row.isInTol -> "commit"
                row.sign == "CCW" -> "rotate_left"
                row.sign == "CW" -> "rotate_right"
                else -> "point_scan"
            }
            val tint = if (row.isInTol) t.go else t.accent
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(t.rCtrl))
                    .background(t.surface2)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // [≤20%] direction indicator
                Box(Modifier.weight(0.2f), contentAlignment = Alignment.Center) {
                    MaterialSymbol(name = glyph, tint = tint, sizeSp = fsSp(24f, t.fs))
                }
                // [40%] hh:mm turn (Mono)
                Box(Modifier.weight(0.4f), contentAlignment = Alignment.CenterStart) {
                    Column {
                        row.name?.let {
                            Text(
                                it,
                                color = t.text2,
                                fontFamily = Geist,
                                fontSize = fsSp(12f, t.fs).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${row.adjust} ${row.sign.orEmpty()}".trim(),
                            color = t.text,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(18f, t.fs).sp,
                        )
                    }
                }
                // [40%] degrees (Mono)
                Box(Modifier.weight(0.4f), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = "${"%.0f".format(row.degrees)}°",
                        color = if (row.isInTol) t.go else t.text,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(18f, t.fs).sp,
                    )
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

/** The `clock_loader_10` asset's filled wedge sits ~30° clockwise of 12-o'clock; calibrated once here. */
private const val WEDGE_NATIVE_OFFSET_DEG = 30f
